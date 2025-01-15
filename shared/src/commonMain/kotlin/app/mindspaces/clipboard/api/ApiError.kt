@file:OptIn(ExperimentalSerializationApi::class)

package app.mindspaces.clipboard.api

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ApiError {
    // if multiple of a type are submitted, ref point to the submitted id/value that is causing the error

    abstract val ref: String?

    // the value that is actually wrong, might be ref
    // useful for example to see a sanitized version
    abstract val value: String?

    // code wrong size:
    // "errors": { "Challenge-Response": [ { "type": "size", "property": "code", "value": "<code>", "ref": "<prop-id>" } ] }
    @Serializable
    @SerialName("size")
    data class Size(
        @EncodeDefault(EncodeDefault.Mode.NEVER) val min: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val max: Int? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val ref: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null
    ) : ApiError()

    // specific type may be required
    // errors: { "Challenge-Response": [ { "code": "required", "category": "Phone-No" } ] }
    @Serializable
    @SerialName("required")
    data class Required(
        // `type` for serial
        @EncodeDefault(EncodeDefault.Mode.NEVER) val category: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val ref: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("schema")
    data class Schema(
        @EncodeDefault(EncodeDefault.Mode.NEVER) val schema: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val ref: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null
    ) : ApiError()

    // invalid code:
    // "errors": { "Challenge-Response": [ { "property": "code", "value": "<code>", "ref": "<prop-id>" } ] }
    @Serializable
    @SerialName("forbidden")
    data class Forbidden(
        @EncodeDefault(EncodeDefault.Mode.NEVER) val auth: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val ref: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("reference")
    data class Reference(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val ref: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = ref
    ) : ApiError()

    @Serializable
    @SerialName("conflict")
    data class Conflict(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val ref: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = ref
    ) : ApiError()
}
