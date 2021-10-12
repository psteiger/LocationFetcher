plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
}

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
        jvmTarget = "1.8"
        languageVersion = "1.5"
    }
}

dependencies {
    coroutines()
    jetpack()
    implementation("com.google.android.gms:play-services-location:18.0.0")
    implementation("javax.inject:javax.inject:1")
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            register("release", MavenPublication::class) {
                from(components["release"])
                artifactId = project.name
            }
        }
    }
}

fun DependencyHandlerScope.coroutines() {
    val version = "1.5.2"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$version")
}

fun DependencyHandlerScope.jetpack() {
    implementation("androidx.activity:activity-ktx:1.4.0-beta01")
    implementation("androidx.fragment:fragment-ktx:1.4.0-alpha10")
    implementation("androidx.appcompat:appcompat:1.4.0-beta01")
    implementation("androidx.core:core-ktx:1.6.0")
    androidxLifecycle()
}

fun DependencyHandlerScope.androidxLifecycle() {
    val lifecycleVersion = "2.4.0-rc01"
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-common:$lifecycleVersion")
}