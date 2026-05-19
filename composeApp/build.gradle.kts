import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composePwa)
    //alias(libs.plugins.koin.compiler)
    //alias(libs.plugins.compose.hot.reload)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.material.icons.extended)

            implementation(libs.bundles.compose.core)
            implementation(libs.bundles.compose.adaptive)
            implementation(libs.bundles.compose.architecture)
            implementation(libs.compose.markdown)
            implementation(libs.compose.markdown.m3)

            implementation(libs.kotlin.io)
            implementation(libs.kotlin.datetime)

            implementation(ktorLibs.client.core)
            implementation(ktorLibs.client.js)
            implementation(ktorLibs.client.websockets)
            implementation(ktorLibs.client.auth)
            implementation(ktorLibs.client.serialization)
            implementation(ktorLibs.client.contentNegotiation)
            implementation(ktorLibs.serialization.kotlinx.json)
            implementation(ktorLibs.client.json)

            implementation(kotlinWrappers.browser)

            implementation(projects.shared)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}


