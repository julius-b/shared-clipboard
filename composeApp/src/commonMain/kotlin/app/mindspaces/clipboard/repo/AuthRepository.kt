package app.mindspaces.clipboard.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.mindspaces.clipboard.api.AccountHints
import app.mindspaces.clipboard.api.AccountParams
import app.mindspaces.clipboard.api.AccountPropertyParams
import app.mindspaces.clipboard.api.Accounts
import app.mindspaces.clipboard.api.ApiAccount
import app.mindspaces.clipboard.api.ApiAccountProperty
import app.mindspaces.clipboard.api.ApiAuthSession
import app.mindspaces.clipboard.api.ApiErrorResponse
import app.mindspaces.clipboard.api.ApiSuccessResponse
import app.mindspaces.clipboard.api.AuthHints
import app.mindspaces.clipboard.api.AuthSessionParams
import app.mindspaces.clipboard.api.AuthSessions
import app.mindspaces.clipboard.api.HintedApiSuccessResponse
import app.mindspaces.clipboard.api.KeyChallengeResponse
import app.mindspaces.clipboard.api.toEntity
import app.mindspaces.clipboard.db.Account
import app.mindspaces.clipboard.db.AccountProperty
import app.mindspaces.clipboard.db.AuthSession
import app.mindspaces.clipboard.db.Database
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
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

    // NOTE: do not clean installation, but links
    suspend fun login(unique: String, secret: String): RepoResult<AuthSessionResult> {
        log.i { "login - unique: $unique" }
        try {
            val resp = client.post(AuthSessions()) {
                contentType(ContentType.Application.Json)
                setBody(AuthSessionParams(unique, secret, true, null))
            }
            if (!resp.status.isSuccess()) {
                val err = resp.body<ApiErrorResponse>()
                return RepoResult.ValidationError(err.errors)
            }
            val success = resp.body<HintedApiSuccessResponse<ApiAuthSession, AuthHints>>()
            val session = success.data.toEntity()
            val props = success.hints.properties.map(ApiAccountProperty::toEntity)
            val account = success.hints.account.toEntity()
            db.transaction {
                accountQueries.insert(account)
                authSessionQueries.insert(session)
                props.forEach(accountPropertyQueries::insert)
            }

            // clean token so loadTokens is refreshed
            log.d { "login - clearing tokens..." }
            client.authProvider<BearerAuthProvider>()?.clearToken()

            return RepoResult.Data(AuthSessionResult(session, account, props))
        } catch (e: Throwable) {
            log.e(e) { "login - unexpected resp: $e" }
            return RepoResult.Empty(false)
        }
    }

    // @persist: false during auth
    suspend fun createProperty(content: String): RepoResult<ApiAccountProperty> {
        log.i { "create-property - content: $content" }
        try {
            val resp = client.post(Accounts.Properties()) {
                contentType(ContentType.Application.Json)
                setBody(AccountPropertyParams(content))
            }
            if (!resp.status.isSuccess()) {
                val err = resp.body<ApiErrorResponse>()
                return RepoResult.ValidationError(err.errors)
            }
            val success = resp.body<ApiSuccessResponse<ApiAccountProperty>>()
            // NOTE: accountId is null
            val prop = success.data
            return RepoResult.Data(prop)
        } catch (e: Throwable) {
            log.e(e) { "create-prop - unexpected resp: $e" }
            return RepoResult.NetworkError
        }
    }

    suspend fun signup(
        name: String,
        secret: String,
        properties: List<ApiAccountProperty>
    ): RepoResult<AccountResult> {
        log.i { "signup - name: $name" }
        try {
            val resp = client.post(Accounts()) {
                contentType(ContentType.Application.Json)
                setBody(AccountParams(name, secret))

                for (prop in properties) {
                    header(KeyChallengeResponse, "${prop.id}=${prop.verificationCode}")
                }
            }
            if (!resp.status.isSuccess()) {
                val err = resp.body<ApiErrorResponse>()
                return RepoResult.ValidationError(err.errors)
            }
            val success = resp.body<HintedApiSuccessResponse<ApiAccount, AccountHints>>()
            val validatedProperties = success.hints.properties.map(ApiAccountProperty::toEntity)
            val account = success.data.toEntity()
            db.transaction {
                // should never cause conflicts since db is cleared before insert
                // TODO clea db before insert
                validatedProperties.forEach(accountPropertyQueries::insert)
                accountQueries.insert(account)
            }
            return RepoResult.Data(AccountResult(account, validatedProperties))
        } catch (e: Throwable) {
            log.e(e) { "signup - unexpected resp: $e" }
            return RepoResult.Empty(false)
        }
    }

    data class AccountResult(
        val account: Account, val properties: List<AccountProperty>
    )

    data class AuthSessionResult(
        val authSession: AuthSession, val account: Account, val properties: List<AccountProperty>
    )
}
