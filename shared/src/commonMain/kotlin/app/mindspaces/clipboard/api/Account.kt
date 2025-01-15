package app.mindspaces.clipboard.api

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ApiAccount(
    val id: SUUID,
    val type: Type,
    val auth: Auth,
    val handle: String,
    val name: String,
    val desc: String?,
    @Transient val secret: String = "",
    @SerialName("profile_id") val profileId: SUUID?,
    @SerialName("banner_id") val bannerId: SUUID?,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?
) {
    enum class Type {
        @SerialName("account")
        Account, Group, Channel, Automated
    }

    enum class Auth {
        @SerialName("default")
        Default, Privileged, Root
    }
}

@Serializable
data class AccountParams(
    //val handle: String,
    val name: String, val secret: String
)

@Serializable
data class ApiAccountProperty(
    val id: SUUID,
    @SerialName("account_id") val accountId: SUUID?,
    @SerialName("installation_id") val installationId: SUUID,
    val type: Type,
    val content: String,
    @SerialName("verification_code") val verificationCode: String,
    val valid: Boolean,
    val primary: Boolean?,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?
) {
    enum class Type {
        @SerialName("phone_no")
        PhoneNo,

        @SerialName("email")
        Email
    }
}

@Serializable
data class AccountPropertyParams(
    val content: String, val type: ApiAccountProperty.Type? = null, val scope: Scope = Scope.Signup
) {
    enum class Scope {
        @SerialName("signup")
        Signup,

        @SerialName("2fa")
        TwoFactor
    }
}

@Serializable
data class ApiAccountLink(
    @SerialName("account_id") val accountId: SUUID,
    @SerialName("peer_id") val peerId: SUUID,
    val perm: Perm,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("created_by") val createdBy: SUUID,
    @SerialName("deleted_at") val deletedAt: Instant?
) {
    // NOTE: add ordinal to compare authorization
    enum class Perm {
        @SerialName("invited")
        Invited,

        @SerialName("read")
        Read,

        @SerialName("write")
        Write,

        @SerialName("read_write")
        ReadWrite,

        @SerialName("admin")
        Admin
    }
}

@Serializable
data class AccountLinkParams(
    @SerialName("account_id") val accountId: SUUID?,
    @SerialName("peer_id") val peerId: SUUID,
    @SerialName("perm") val perm: ApiAccountLink.Perm
)
