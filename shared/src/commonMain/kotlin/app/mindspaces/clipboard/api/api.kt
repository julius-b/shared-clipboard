@file:OptIn(ExperimentalSerializationApi::class)

package app.mindspaces.clipboard.api

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

const val KeyInstallationID = "Installation-Id"
const val KeyChallengeResponse = "Challenge-Response"
const val KeyRefreshToken = "Refresh-Token"

@Serializable
data class ApiSuccessResponse<out T : Any>(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val count: Int? = null, val data: T
)

// TODO make properties part of Actor -> no more Hints
@Serializable
data class HintedApiSuccessResponse<out T : Any, out S : Any>(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val count: Int? = null, val data: T, val hints: S
)

@Serializable
data class ApiErrorResponse(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val errors: Errors
)

typealias Errors = Map<String, Array<out ApiError>>
