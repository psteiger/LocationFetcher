plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
    signing
}

apply(from = "$rootDir/scripts/publish-root.gradle.kts")

group = "app.freel"
version = "8.0.0-alpha03"

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
        languageVersion = "1.5"
    }
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            register<MavenPublication>("release") {
                from(components["release"])
                groupId = "app.freel"
                version = "8.0.0-alpha03"
                artifactId = project.name
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
                        url.set("https://github.com/LocationFetcher/psteiger/tree/master")
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
    coroutines()
    jetpack()
    arrow()
    implementation("com.google.android.gms:play-services-location:18.0.0")
    implementation("javax.inject:javax.inject:1")
}

fun DependencyHandlerScope.arrow() {
    val version = "1.0.1"
//    implementation(platform("io.arrow-kt:arrow-stack:$version"))
    api("io.arrow-kt:arrow-core:$version")

}

fun DependencyHandlerScope.coroutines() {
    val version = "1.5.2"
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$version")
}

fun DependencyHandlerScope.jetpack() {
    implementation("androidx.activity:activity-ktx:1.4.0")
    implementation("androidx.fragment:fragment-ktx:1.4.0-rc01")
    implementation("androidx.appcompat:appcompat:1.4.0-rc01")
    implementation("androidx.core:core-ktx:1.7.0")
    androidxLifecycle()
}

fun DependencyHandlerScope.androidxLifecycle() {
    val lifecycleVersion = "2.4.0"
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-common:$lifecycleVersion")
}