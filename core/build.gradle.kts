plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.dokka)
    `maven-publish`
}

group = "es.jvbabi.authentikt"

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

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("core") {
            from(components["kotlin"])
            artifact(sourcesJar)
            artifactId = "authentikt-core"
            pom {
                name = "authentikt-core"
                description = "Kotlin/Ktor multi-step authentication flow library"
                url = "https://github.com/Julius-Babies/authentikt"

                licenses {
                    license {
                        name = "MIT"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }

                developers {
                    developer {
                        id = "Julius-Babies"
                        name = "Julius Babies"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/Julius-Babies/authentikt.git"
                    developerConnection = "scm:git:ssh://github.com/Julius-Babies/authentikt.git"
                    url = "https://github.com/Julius-Babies/authentikt"
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
