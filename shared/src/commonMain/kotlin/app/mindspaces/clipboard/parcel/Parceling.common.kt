package app.mindspaces.clipboard.parcel

// src: https://github.com/slackhq/circuit/blob/main/samples/star/src/commonMain/kotlin/com/slack/circuit/star/parcel/Parceling.common.kt

// For Android @Parcelize
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class CommonParcelize

// For Android Parcelable
expect interface CommonParcelable
