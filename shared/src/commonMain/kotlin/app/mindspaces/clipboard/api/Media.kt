package app.mindspaces.clipboard.api

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiMedia(
    val id: SUUID,
    val path: String,
    val dir: String,
    val cre: Long?,
    val mod: Long,
    val size: Long,
    @SerialName("has_thumb") val hasThumb: Boolean,
    @SerialName("has_file") val hasFile: Boolean,
    @SerialName("media_type") val mediaType: MediaType?,
    @SerialName("installation_id") val installationId: SUUID,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("deleted_at") val deletedAt: Instant?
)

@Serializable
data class ApiMediaReceipt(
    @SerialName("media_id") val mediaId: SUUID,
    @SerialName("installation_id") val installationId: SUUID,
    @SerialName("has_thumb") val hasThumb: Boolean,
    @SerialName("has_file") val hasFile: Boolean
)

@Serializable
data class ApiMediaRequest(
    @SerialName("id") val id: SUUID,
    @SerialName("media_id") val mediaId: SUUID,
    @SerialName("installation_id") val installationId: SUUID,
    @SerialName("created_at") val createdAt: Instant
)

@Serializable
data class MediaReceiptParams(
    @SerialName("has_thumb") val hasThumb: Boolean? = null,
    @SerialName("has_file") val hasFile: Boolean? = null
)
