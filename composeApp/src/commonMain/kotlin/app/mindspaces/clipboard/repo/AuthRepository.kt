package app.mindspaces.clipboard.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.mindspaces.clipboard.api.AccountHints
import app.mindspaces.clipboard.api.Accounts
import app.mindspaces.clipboard.api.ApiAccount
import app.mindspaces.clipboard.api.ApiAccountProperty
import app.mindspaces.clipboard.api.ApiAuthSession
import app.mindspaces.clipboard.api.ApiErrorResponse
import app.mindspaces.clipboard.api.AuthHints
import app.mindspaces.clipboard.api.AuthSessionParams
import app.mindspaces.clipboard.api.AuthSessions
import app.mindspaces.clipboard.api.HintedApiSuccessResponse
import app.mindspaces.clipboard.api.KeyInstallationID
import app.mindspaces.clipboard.api.SignupParams
import app.mindspaces.clipboard.api.toEntity
import app.mindspaces.clipboard.db.Account
import app.mindspaces.clipboard.db.AccountProperty
import app.mindspaces.clipboard.db.AuthSession
import app.mindspaces.clipboard.db.Database
import app.mindspaces.clipboard.db.cleanup
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@Inject
@SingleIn(AppScope::class)
class AuthRepository(private val db: Database, private val client: HttpClient) {
    private val log = Logger.withTag("AuthRepo")

    private val accountQueries = db.accountQueries
    private val accountPropertyQueries = db.accountPropertyQueries
    private val authSessionQueries = db.authSessionQueries

    fun self() =
        accountQueries.getSelf().asFlow().mapToOneOrNull(Dispatchers.IO).distinctUntilChanged()

    fun selfResult() = accountQueries.getSelf().asFlow().mapToOneOrNull(Dispatchers.IO).map {
        if (it == null) RepoResult.Empty(loading = false)
        else RepoResult.Data(it)
    }.distinctUntilChanged()

    fun properties() =
        accountPropertyQueries.getSelf().asFlow().mapToList(Dispatchers.IO).distinctUntilChanged()

    // NOTE: do not clean installation, but links
    suspend fun login(unique: String, secret: String, cleanup: Boolean = true):
            RepoResult<AuthSessionResult> {
        log.i { "login - unique: $unique" }
        try {
            val resp = client.post(AuthSessions()) {
                contentType(ContentType.Application.Json)
                setBody(AuthSessionParams(unique, secret, true, null))
            }
            if (!resp.status.isSuccess()) {
                val err = resp.body<ApiErrorResponse>()
                if (cleanup && err.errors.containsKey(KeyInstallationID)) {
                    log.w { "cleanup - found stale installation, full cleanup..." }
                    cleanup(db, full = true)
                    return login(unique, secret, false)
                }
                return RepoResult.ValidationError(err.errors)
            }
            val success = resp.body<HintedApiSuccessResponse<ApiAuthSession, AuthHints>>()
            val session = success.data.toEntity()
            val props = success.hints.properties.map(ApiAccountProperty::toEntity)
            val account = success.hints.account.toEntity()
            log.i { "login - saving resp: $success..." }
            db.transaction {
                accountQueries.insert(account)
                authSessionQueries.insert(session)
                props.forEach(accountPropertyQueries::insert)
            }

            // force ktor-client to re-initialize auth state
            log.d { "login - clearing tokens..." }
            client.authProvider<BearerAuthProvider>()?.clearToken()

            return RepoResult.Data(AuthSessionResult(session, account, props))
        } catch (e: Throwable) {
            log.e(e) { "login - unexpected resp: $e" }
            return RepoResult.Empty(false)
        }
    }

    suspend fun signup(name: String, secret: String, email: String, cleanup: Boolean = true):
            RepoResult<AccountResult> {
        log.i { "signup - name: $name" }
        try {
            val resp = client.post(Accounts.Signup()) {
                contentType(ContentType.Application.Json)
                setBody(SignupParams(name, secret, email))
            }
            if (!resp.status.isSuccess()) {
                val err = resp.body<ApiErrorResponse>()
                if (cleanup && err.errors.containsKey(KeyInstallationID)) {
                    log.w { "signup - found stale installation, full cleanup..." }
                    cleanup(db, full = true)
                    return signup(name, secret, email, false)
                }
                return RepoResult.ValidationError(err.errors)
            }
            val success = resp.body<HintedApiSuccessResponse<ApiAccount, AccountHints>>()
            val validatedProperties = success.hints.properties.map(ApiAccountProperty::toEntity)
            val session = success.hints.session.toEntity()
            val account = success.data.toEntity()
            log.i { "signup - saving resp: $success..." }
            db.transaction {
                // should never cause conflicts since db is cleared before insert
                // TODO clean db before insert
                validatedProperties.forEach(accountPropertyQueries::insert)
                accountQueries.insert(account)
                authSessionQueries.insert(session)
            }

            // force ktor-client to re-initialize auth state
            log.d { "signup - clearing tokens..." }
            client.authProvider<BearerAuthProvider>()?.clearToken()

            return RepoResult.Data(AccountResult(account, validatedProperties))
        } catch (e: Throwable) {
            log.e(e) { "signup - unexpected resp: $e" }
            return RepoResult.Empty(false)
        }
    }

    // wrapper
    fun logout() {
        cleanup(db)
    }

    data class AccountResult(
        val account: Account, val properties: List<AccountProperty>
    )

    data class AuthSessionResult(
        val authSession: AuthSession, val account: Account, val properties: List<AccountProperty>
    )
}
