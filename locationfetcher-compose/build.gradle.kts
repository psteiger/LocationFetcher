plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
    signing
}

apply(from = "$rootDir/scripts/publish-root.gradle.kts")

group = "app.freel"
version = "8.2.8"

android {
    compileSdk = 32
    defaultConfig {
        minSdk = 21
        targetSdk = 32
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        freeCompilerArgs += listOf(
            "-Xexplicit-api=strict",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${buildDir}/composeReports"
        )
        jvmTarget = "1.8"
        languageVersion = "1.6"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.2.0-alpha08"
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
                version = "8.2.8"
                artifactId = "locationfetcher-compose"
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
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")
    api("com.google.android.gms:play-services-location:19.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.4.1")
    implementation("androidx.compose.runtime:runtime:1.2.0-alpha08")
    implementation("androidx.compose.ui:ui:1.2.0-alpha08")
    implementation("androidx.activity:activity-compose:1.4.0")
    implementation("androidx.compose.material3:material3:1.0.0-alpha10")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test:rules:1.4.0")
}
