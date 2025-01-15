package app.mindspaces.clipboard.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Message {
    @Serializable
    @SerialName("notice")
    data class Notice(val txt: String) : Message()

    @Serializable
    @SerialName("media-request")
    data class MediaRequest(val req: ApiMediaRequest) : Message()

    @Serializable
    @SerialName("devices")
    data class Devices(val devices: List<ApiInstallationLink>) : Message()
}
