import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlin.plugin.parcelize)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-P",
                "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=app.mindspaces.clipboard.parcel.CommonParcelize",
            )
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            kotlin.sourceSets.all {
                freeCompilerArgs.addAll(
                    "-opt-in=kotlin.uuid.ExperimentalUuidApi",
                    "-Xexpect-actual-classes"
                )
            }
        }
    }

    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.work)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.ktor.client)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.resources)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.circuit.foundation)
            implementation(libs.circuit.codegen.annotations)
            implementation(libs.circuitx.android)
            implementation(libs.circuitx.gesture.navigation)
            implementation(libs.libsodium.bindings)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.appdirs)
            implementation(libs.coil)
            implementation(libs.coil.network.ktor3)
            implementation(libs.thumbnailator)
            implementation(libs.kermit)
            implementation(libs.kotlin.inject.runtime)
            implementation(libs.kotlin.inject.anvil.runtime)
            implementation(libs.kotlin.inject.anvil.runtime.optional)
            implementation(projects.shared)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("app.mindspaces.clipboard.db")
        }
    }
}

android {
    namespace = "app.mindspaces.clipboard"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.mindspaces.clipboard"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        // particularly for java.time.Instant
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ksp {
    arg("me.tatarka.inject.generateCompanionExtensions", "true")
    arg("circuit.codegen.lenient", "true")
    arg("circuit.codegen.mode", "kotlin_inject_anvil")
    arg(
        "kotlin-inject-anvil-contributing-annotations",
        "com.slack.circuit.codegen.annotations.CircuitInject"
    )
}

dependencies {
    // https://developer.android.com/studio/write/java8-support#library-desugaring
    coreLibraryDesugaring(libs.desugar)

    ksp(libs.kotlin.inject.compiler)
    ksp(libs.kotlin.inject.anvil.compiler)
    api(libs.circuit.codegen.annotations)
    ksp(libs.circuit.codegen)

    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "app.mindspaces.clipboard.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "app.mindspaces.clipboard"
            packageVersion = "1.0.0"
        }
    }
}
