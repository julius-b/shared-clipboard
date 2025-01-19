package app.mindspaces.clipboard.api

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiAuthSession(
    val id: SUUID,
    @SerialName("account_id") val accountId: SUUID,
    @SerialName("installation_id") val installationId: SUUID,
    @SerialName("link_id") val linkId: SUUID,
    @SerialName("secret_update_id") val secretUpdateId: SUUID,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant? = null,
)

@Serializable
data class AuthSessionParams(
    val unique: String,
    val secret: String,
    @SerialName("cap_chat") val capChat: Boolean,
    @SerialName("link_id") val linkId: SUUID? = null
)

@Serializable
data class AuthHints(
    val properties: List<ApiAccountProperty>, val account: ApiAccount
)
