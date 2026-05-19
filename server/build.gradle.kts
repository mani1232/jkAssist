import io.ktor.plugin.features.DockerImageRegistry
import io.ktor.plugin.features.DockerPortMapping
import io.ktor.plugin.features.DockerPortMappingProtocol

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    //alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "cc.worldmandia.jkassist"
version = "1.0.0"
application {
    mainClass.set("cc.worldmandia.jkassist.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)

    //implementation(libs.koin.core)
    //implementation(libs.koin.ktor)
    //implementation(libs.koin.ktor.logger)

    implementation(libs.kotlin.io)
    implementation(libs.kotlin.datetime)

    implementation(libs.koog.agents.core)
    implementation(libs.koog.agents.tools)

    implementation(libs.koog.ktor)

    implementation(libs.koog.features.memory)
    implementation(libs.koog.features.longterm.memory)

    implementation(libs.koog.llms.all)

    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.websockets)
    implementation(ktorLibs.server.cio)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.apiKey)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.di)
    implementation(ktorLibs.server.forwardedHeader)
    implementation(ktorLibs.serialization)
    implementation(ktorLibs.serialization.kotlinx.json)

    testImplementation(libs.kotlin.testJunit)
}

ktor {
    docker {
        val registryUrl = "ghcr.io/mani1232/jkAssist/server".lowercase()

        localImageName.set(registryUrl)
        imageTag.set(project.version.toString())

        jreVersion.set(JavaVersion.VERSION_26)

        externalRegistry.set(
            DockerImageRegistry.externalRegistry(
            username = providers.environmentVariable("GITHUB_USERNAME"),
            password = providers.environmentVariable("GITHUB_PASSWORD"),
            project = provider { "jkAssist/server".lowercase() },
            hostname = provider { "ghcr.io" },
            namespace = provider { "mani1232" }
        ))

        jib {
            container {
                workingDirectory = "/home/container"
                jvmFlags = listOf()
            }
        }
    }
}