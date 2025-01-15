package app.mindspaces.clipboard.db

// NOTE: don't change names, saved in db
enum class ThumbState {
    Pending, Generated, Synced, GenFailed;

    fun hasLocalThumb() = this == Generated || this == Synced

    companion object {
        fun fromRemote(remoteHasThumb: Boolean) = if (remoteHasThumb) Synced else Pending
    }
}
