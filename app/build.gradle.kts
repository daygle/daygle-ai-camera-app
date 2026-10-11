import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.daygle.aicamera"

    lint {
        lintConfig = file("lint.xml")
    }
    compileSdk = 37

    val gitVersionName = System.getenv("VERSION_NAME") ?: project.findProperty("VERSION_NAME") as? String ?: "1.0.0"
    val gitVersionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: (project.findProperty("VERSION_CODE") as? String)?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "com.daygle.aicamera"
        minSdk = 26
        targetSdk = 37
        versionCode = gitVersionCode
        versionName = gitVersionName

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing: credentials come from environment variables (CI secrets)
    // or, for local builds, from an untracked keystore.properties in the project
    // root. See README "Release signing". When no keystore is configured the
    // release buildType stays unsigned.
    val releaseSigningProps = Properties().apply {
        val propsFile = rootProject.file("keystore.properties")
        if (propsFile.exists()) {
            propsFile.inputStream().use { load(it) }
        }
    }
    fun releaseSigningValue(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: releaseSigningProps.getProperty(name)?.takeIf { it.isNotBlank() }

    val releaseKeystorePath = releaseSigningValue("RELEASE_KEYSTORE_FILE")
    val releaseKeystore = releaseKeystorePath?.let { rootProject.file(it) }
    val releaseSigningConfigured = releaseKeystore?.exists() == true

    if (releaseSigningConfigured && releaseKeystore != null) {
        signingConfigs.create("release") {
            storeFile = releaseKeystore
            storePassword = releaseSigningValue("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = releaseSigningValue("RELEASE_KEY_ALIAS")
            keyPassword = releaseSigningValue("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            // R8 shrinks/obfuscates the release build. AGP embeds the resulting
            // mapping.txt in the .aab (BUNDLE-METADATA), so Google Play picks up
            // the deobfuscation file automatically.
            isMinifyEnabled = true
            isShrinkResources = true
            // Bundle native debug symbols for the prebuilt .so files pulled in by
            // dependencies so Play can symbolicate native crashes and ANRs.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
            keepDebugSymbols += "**/libdatastore_shared_counter.so"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
