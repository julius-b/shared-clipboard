package app.mindspaces.clipboard.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiDataNotification(
    val target: Target
) {
    enum class Target {
        @SerialName("media")
        Media
    }
}
