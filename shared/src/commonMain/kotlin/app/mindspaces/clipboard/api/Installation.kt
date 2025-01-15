package app.mindspaces.clipboard.api

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ApiInstallation(
    val id: SUUID,
    val name: String,
    val desc: String,
    val os: Platform,
    val client: String,
    @SerialName("created_at") val createdAt: Instant,
    // don't share with client as they don't care / understand
    @Transient @SerialName("deleted_at") val deletedAt: Instant? = null
) {
    enum class Platform {
        @SerialName("desktop")
        Desktop,

        @SerialName("windows")
        Windows,

        @SerialName("mac_os")
        MacOS,

        @SerialName("linux")
        Linux,

        @SerialName("mobile")
        Mobile,

        @SerialName("android")
        Android,

        @SerialName("ios")
        iOS,

        @SerialName("web")
        Web
    }
}

@Serializable
data class InstallationParams(
    val id: SUUID,
    val name: String,
    val desc: String,
    val os: ApiInstallation.Platform,
    @SerialName("client") val client: String
)

@Serializable
data class ApiInstallationLink(
    val id: SUUID,
    val name: String?,
    @SerialName("installation_id") val installationId: SUUID,
    val installation: ApiInstallation,
    @SerialName("account_id") val accountId: SUUID,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?
)
