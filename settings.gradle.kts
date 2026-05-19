rootProject.name = "jkAssist"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven("https://redirector.kotlinlang.org/maven/compose-dev")
        maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/compose-dev")
        maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
    }
    versionCatalogs {
        create("ktorLibs") {
            val ktorVersion = "3.5.0"
            from("io.ktor:ktor-version-catalog:$ktorVersion")
        }
        create("kotlinWrappers") {
            val wrappersVersion = "2026.5.5"
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:$wrappersVersion")
        }
    }
}

include(":composeApp")
include(":server")
include(":shared")