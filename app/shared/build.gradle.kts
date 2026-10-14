import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// The API's address is part of the build, not the source. To try the app on a real phone, point it at the machine
// that runs the API with `apiBaseUrl=http://<ip>:8080/api/v1` in local.properties (never committed) or with
// `-Pstockflow.apiBaseUrl=...`; without either it reaches the API that a simulator or emulator runs on the same
// machine. See app/README.md.
val apiBaseUrl: String = providers.gradleProperty("stockflow.apiBaseUrl").orNull
    ?: rootProject.file("local.properties").takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } }.getProperty("apiBaseUrl") }
    ?: "http://localhost:8080/api/v1"

val generateApiEnvironment = tasks.register("generateApiEnvironment") {
    val outputDir = layout.buildDirectory.dir("generated/apiEnvironment/kotlin")
    val baseUrl = apiBaseUrl
    inputs.property("apiBaseUrl", baseUrl)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().asFile.resolve("com/axzydev/stock/ApiEnvironment.kt")
        file.parentFile.mkdirs()
        file.writeText(
            "package com.axzydev.stock\n\n" +
                "// The API's address, written by the build. Do not edit. See app/README.md.\n" +
                "internal const val API_BASE_URL: String = \"$baseUrl\"\n",
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
        namespace = "com.axzydev.stock.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain {
            // Where the API is reached, generated from local.properties/-P so no machine's address lands in the source.
            kotlin.srcDir(generateApiEnvironment)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compottie)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // The camera scanner (mobile-app.md section 2): CameraX with the ML Kit barcode reader, bundled in the app.
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.camera.mlkitVision)
            implementation(libs.mlkit.barcodeScanning)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}