plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.dokka)
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.kotlin.onetimepassword)
    implementation(libs.gson)
}

kotlin {
    jvmToolchain(25)

    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        freeCompilerArgs.add("-Xlocal-type-aliases")
    }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("core") {
            from(components["kotlin"])
            pom {
                name.set("authentikt-core")
                description.set("Kotlin/Ktor multi-step authentication flow library")
                url.set("https://github.com/Julius-Babies/authentikt")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("Julius-Babies")
                        name.set("Julius Babies")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/Julius-Babies/authentikt.git")
                    developerConnection.set("scm:git:ssh://github.com/Julius-Babies/authentikt.git")
                    url.set("https://github.com/Julius-Babies/authentikt")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Julius-Babies/authentikt")
            credentials {
                username = project.findProperty("gpr.user") as String?
                    ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String?
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
