import io.ktor.plugin.features.DockerImageRegistry
import io.ktor.plugin.features.DockerPortMapping
import io.ktor.plugin.features.DockerPortMappingProtocol

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
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
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

ktor {
    docker {
        val registryUrl = "ghcr.io/mani1232/jkAssist/server".lowercase()

        localImageName.set(registryUrl)
        imageTag.set(project.version.toString())

        jreVersion.set(JavaVersion.VERSION_26)

        portMappings.set(listOf(
            DockerPortMapping(
                8080, 8080, DockerPortMappingProtocol.TCP
            )
        ))

        externalRegistry.set(
            DockerImageRegistry.externalRegistry(
            username = providers.environmentVariable("GITHUB_USERNAME"),
            password = providers.environmentVariable("GITHUB_PASSWORD"),
            project = provider { "jkAssist/server" },
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