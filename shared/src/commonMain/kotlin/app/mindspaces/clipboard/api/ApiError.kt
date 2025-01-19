@file:OptIn(ExperimentalSerializationApi::class)

package app.mindspaces.clipboard.api

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ApiError {
    // the value that is actually wrong
    // useful to see a sanitized version
    abstract val value: String?

    @Serializable
    @SerialName("constraint")
    data class Constraint(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val min: Long? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val max: Long? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val equal: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("required")
    data class Required(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val category: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("schema")
    data class Schema(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val schema: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("forbidden")
    data class Forbidden(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val property: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("unauthenticated")
    data class Unauthenticated(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val property: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("reference")
    data class Reference(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("conflict")
    data class Conflict(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null,
        @EncodeDefault(EncodeDefault.Mode.NEVER) val category: String? = null
    ) : ApiError()

    @Serializable
    @SerialName("internal")
    data class Internal(
        @EncodeDefault(EncodeDefault.Mode.NEVER) override val value: String? = null
    ) : ApiError()
}
