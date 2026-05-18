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
    versionCatalogs {
        create("ktorLibs") {
            from("io.ktor:ktor-version-catalog:3.5.0")
        }
    }
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
    }
}

include(":composeApp")
include(":server")
include(":shared")