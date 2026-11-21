import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// =====================================================================
// RELEASE SIGNING
// =====================================================================
val keystorePropsFile = rootProject.file("release.properties")
val keystoreProps = Properties()
val hasValidSigningProps = keystorePropsFile.exists() && run {
    FileInputStream(keystorePropsFile).use { keystoreProps.load(it) }
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        .all { keystoreProps[it] != null }
}

android {
    namespace = "com.sanad.bond"
    compileSdk = 35

    lint {
        checkReleaseBuilds = false
    }

    signingConfigs {
        if (hasValidSigningProps) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.sanad.bond"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "2.2"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Required: POI + Hilt + Compose push the method count past the
        // 64K DEX limit on minSdk 26. Without this the release build fails
        // with "Cannot fit requested classes in a single dex file".
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            if (hasValidSigningProps) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/kotlinx_coroutines_core.version"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            // Apache POI brings these via transitive deps (commons-compress,
            // xmlbeans, commons-io). Excluding them is required to merge the
            // APK without conflicts.
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/*.kotlin_module"

            pickFirsts += "nonJvmMain/default/linkdata/package_androidx/0_androidx.knm"
            pickFirsts += "nonJvmMain/default/linkdata/root_package/0_.knm"
            pickFirsts += "nonJvmMain/default/linkdata/module"
            pickFirsts += "nativeMain/default/linkdata/root_package/0_.knm"
            pickFirsts += "nativeMain/default/linkdata/module"
            pickFirsts += "commonMain/default/linkdata/root_package/0_.knm"
            pickFirsts += "commonMain/default/linkdata/module"
            pickFirsts += "commonMain/default/linkdata/package_androidx/0_androidx.knm"
            pickFirsts += "META-INF/kotlin-project-structure-metadata.json"
            merges += "commonMain/default/manifest"
            merges += "nonJvmMain/default/manifest"
            merges += "nativeMain/default/manifest"
        }
    }
}

// =====================================================================
// DEPENDENCY RESOLUTION
// =====================================================================
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.1.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.0")
        force("androidx.collection:collection:1.6.0")
        force("androidx.collection:collection-ktx:1.6.0")
        force("androidx.annotation:annotation:1.8.1")
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // ---- Core AndroidX / Material ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.interpolator)

    // ---- Jetpack Compose ----
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.rich.editor)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ---- Room + Coroutines + WorkManager ----
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // ---- Document processing (PDF + DOCX) ----
    // PDFBox: PDF read/write
    implementation(libs.pdfbox.android)
    // Apache POI: DOCX read/write (Word). All three artifacts are required:
    //   - poi-ooxml        : XWPFDocument (the .docx implementation)
    //   - poi              : core POI runtime (HSSF etc., required at runtime)
    //   - poi-scratchpad   : HWPF + XSLF helpers (also required by poi-ooxml)
    implementation(libs.poi.ooxml)
    implementation(libs.poi)
    implementation(libs.poi.scratchpad)

    // ---- Markdown (commonmark + GFM tables) ----
    // Used by MarkdownConverter instead of the old hand-rolled regex parser,
    // which produced malformed HTML (orphan </p><p> between blocks) and
    // dropped lists / links / images / code blocks.
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)

    // ---- Hilt Dependency Injection ----
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // ---- Security & Biometric ----
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    // ---- DataStore Preferences ----
    implementation(libs.androidx.datastore.preferences)

    // ---- Navigation ----
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.navigation.compose)

    // ---- Image Loading (Coil) ----
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // ---- Testing ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}