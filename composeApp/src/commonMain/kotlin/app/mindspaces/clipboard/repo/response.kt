package app.mindspaces.clipboard.repo

import app.mindspaces.clipboard.api.Errors

sealed interface NetworkResponse<out T : Any> {
    data object Loading : NetworkResponse<Nothing>
    data class Data<T : Any>(val data: T) : NetworkResponse<T>

    // code-only replies are valid
    data class ValidationError(val errors: Errors? = null) : NetworkResponse<Nothing>

    // connect, dns, 404, etc.
    data object NetworkError : NetworkResponse<Nothing>
}

// Loading state isn't necessary, it's the same as Empty (doesn't exist anywhere) in the UI
// Loading isn't a state because both Empty & Data can still be in the process of 'loading'
sealed interface RepoResult<out T : Any> {
    val loading: Boolean

    data class Data<T : Any>(
        val data: T, val cached: Boolean = false, override val loading: Boolean = false
    ) : RepoResult<T>

    // NOTE / TODO: should also include 401
    data class ValidationError(
        val errors: Errors? = null
    ) : RepoResult<Nothing> {
        override val loading = false
    }

    // couldn't be queried, may or may not exist
    data object NetworkError : RepoResult<Nothing> {
        override val loading = false
    }

    // `loading = false`: doesn't exist, not even locally, possibly deleted (deleted_at is set)
    // actorRepo.get: db returns null (loading still true?) -> how to know if maybe the sync just failed due to network, or
    data class Empty(override val loading: Boolean) : RepoResult<Nothing>
}
