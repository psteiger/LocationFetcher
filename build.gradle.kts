plugins {
    id("com.android.library") version "7.1.0-beta02" apply false
    kotlin("android") version "1.6.0-RC2" apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}

apply(from = "$rootDir/scripts/publish-root.gradle.kts")

// Set up Sonatype repository
nexusPublishing {
    repositories {
        sonatype {
            val ossrhUsername: String by extra
            val ossrhPassword: String by extra
            val sonatypeStagingProfileId: String by extra
            stagingProfileId.set(sonatypeStagingProfileId)
            username.set(ossrhUsername)
            password.set(ossrhPassword)
            // Add these lines if using new Sonatype infra
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}
