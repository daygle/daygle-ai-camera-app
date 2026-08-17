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

    defaultConfig {
        applicationId = "com.daygle.aicamera"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    constraints {
        implementation("io.netty:netty-all:4.1.112.Final")
        implementation("io.netty:netty-common:4.1.112.Final")
        implementation("io.netty:netty-handler:4.1.112.Final")
        implementation("io.netty:netty-codec-http:4.1.112.Final")
        implementation("io.netty:netty-codec-http2:4.1.112.Final")
        implementation("io.netty:netty-codec:4.1.112.Final")
        implementation("io.netty:netty-handler-proxy:4.1.112.Final")
        implementation("io.netty:netty-buffer:4.1.112.Final")
        implementation("io.netty:netty-transport:4.1.112.Final")
        implementation("io.netty:netty-resolver:4.1.112.Final")
        implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
        implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
        implementation("org.jdom:jdom2:2.0.6.1")
        implementation("org.apache.commons:commons-lang3:3.17.0")
        implementation("org.bitbucket.b_c:jose4j:0.9.6")
        implementation("org.apache.httpcomponents:httpclient:4.5.14")
    }
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
}
