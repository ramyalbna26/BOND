import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
}

// The Juzoor sources live in a flat Android layout (src/, res/) so the
// standalone build.sh keeps working on machines without Gradle.  The module
// simply points AGP at those directories.
val releaseProps = Properties()
val releasePropsFile = rootProject.file("release.properties")
val hasReleaseProps = releasePropsFile.exists() && run {
    FileInputStream(releasePropsFile).use { releaseProps.load(it) }
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { releaseProps[it] != null }
}

android {
    namespace = "com.ramyelbanna.juzoor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ramyelbanna.juzoor"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "4.0.0"
    }

    signingConfigs {
        if (hasReleaseProps) {
            create("release") {
                storeFile = rootProject.file(releaseProps["storeFile"] as String)
                storePassword = releaseProps["storePassword"] as String
                keyAlias = releaseProps["keyAlias"] as String
                keyPassword = releaseProps["keyPassword"] as String
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseProps) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("src")
            res.srcDirs("res")
            assets.srcDirs("assets")
        }
        getByName("test") {
            java.srcDirs("test")
            resources.srcDirs("test/resources")
        }
    }

    lint {
        // The UI is generated programmatically; several intentional platform
        // APIs (MANAGE_EXTERNAL_STORAGE, legacy storage) are part of the spec.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // Bundled jars: the lite Android apksig signer, the offline Jadx
    // decompiler and its DEX/smali runtime dependencies.  Keeping them in
    // libs/ also lets the standalone build.sh use the exact same versions.
    implementation(fileTree("libs") { include("*.jar") })

    testImplementation(libs.junit)
}
