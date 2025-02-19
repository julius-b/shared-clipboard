package app.mindspaces.clipboard.utils

// media paths are always persisted with path-separator = '/'
// db queries are simpler -> alt: pass separator as arg
// `File(...).extension` could never be used for media paths anyway since it always uses the local separator
// calculating extension for any `media.path` is possible without knowing media origin

val String.mediaName: String
    get() = this.substringAfterLast('/')

// if filename has not extension, extension is empty string, not full filename
val String.mediaExt: String
    get() = this.mediaName.substringAfterLast('.', missingDelimiterValue = "")
