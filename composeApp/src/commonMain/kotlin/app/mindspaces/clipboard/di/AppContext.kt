package app.mindspaces.clipboard.di

import me.tatarka.inject.annotations.Qualifier

@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.TYPE,
)
@Qualifier
annotation class AppContext
