plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
    signing
}

apply(from = "$rootDir/scripts/publish-root.gradle.kts")

group = "app.freel"
version = "8.1.1"

android {
    compileSdk = 31
    defaultConfig {
        minSdk = 16
        targetSdk = 31
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        freeCompilerArgs += listOf(
            "-Xexplicit-api=strict",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
        jvmTarget = "1.8"
        languageVersion = "1.6"
    }
}

val sourcesJar = task<Jar>("androidSourcesJar") {
    archiveClassifier.set("sources")
    from(android.sourceSets["main"].java.srcDirs)
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "app.freel"
                version = "8.1.1"
                artifactId = project.name
                artifact(sourcesJar).apply {
                    classifier = "sources"
                }
                pom {
                    name.set(project.name)
                    description.set("Easy Location fetching for Android apps.")
                    url.set("https://github.com/psteiger/LocationFetcher")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://github.com/psteiger/LocationFetcher/blob/master/LICENSE")
                        }
                    }
                    developers {
                        developer {
                            id.set("psteiger")
                            name.set("Patrick Steiger")
                            email.set("psteiger@gmail.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:github.com/psteiger/LocationFetcher/LocationFetcher.git")
                        developerConnection.set("scm:git:ssh://github.com/psteiger/LocationFetcher/LocationFetcher.git")
                        url.set("https://github.com/psteiger/LocationFetcher/tree/master")
                    }
                }
            }
        }
    }
}

signing {
    val signingKeyId: String by extra
    val signingPassword: String by extra
    val signingKey: String by extra
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(publishing.publications)
}

dependencies {
    api("io.arrow-kt:arrow-core:1.0.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    api("com.google.android.gms:play-services-location:19.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.1")
    implementation("androidx.activity:activity-ktx:1.4.0")
    implementation("androidx.fragment:fragment-ktx:1.4.1")
    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.4.1")
    implementation("androidx.lifecycle:lifecycle-common:2.4.1")
    implementation("javax.inject:javax.inject:1")
}
