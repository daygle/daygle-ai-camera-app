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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
            // Go-built binaries that the NDK strip tool can't process
            keepDebugSymbols += "**/libwg-go.so"
            keepDebugSymbols += "**/libwg-quick.so"
            keepDebugSymbols += "**/libwg.so"
        }
    }
}

// WireGuard's com.wireguard.android:tunnel ships prebuilt native libs that
// must satisfy the 16 KB page size requirement (mandatory for apps targeting
// Android 15+). Even in 1.0.20260102 only the arm64-v8a / x86_64 libs are
// 16 KB-aligned; the armeabi-v7a / x86 libs are still 4 KB (p_align 0x1000).
// This realignment is REQUIRED, not merely a safety net: AGP 9's packageRelease
// stores native libs uncompressed and page-aligned, and refuses to package the
// 4 KB-aligned 32-bit .so files, so assembleRelease fails without it. (Do not
// remove this without also dropping the 32-bit ABIs.) scripts/align_elf_16k.py
// inserts padding between LOAD segments and raises p_align to 0x4000 without
// touching any virtual address, relocation, or symbol -- the bytes each segment
// maps are identical, so the result behaves exactly like the original on 4 KB
// devices and becomes loadable on 16 KB devices.
val alignScript = rootProject.file("scripts/align_elf_16k.py")
val pythonExecutable =
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) "python" else "python3"

// AGP 9 removed the public task classes (e.g. MergeNativeLibsTask), so the
// native libs directory that packaging consumes is located by its stable
// convention path: the *stripped* output, since package{Variant} reads from
// stripped_native_libs rather than merged_native_libs.
listOf("debug", "release").forEach { buildType ->
    val cap = buildType.replaceFirstChar { it.uppercase() }
    val nativeLibsDir =
        layout.buildDirectory.dir(
            "intermediates/stripped_native_libs/$buildType/strip${cap}DebugSymbols/out"
        )
    val alignTask = tasks.register<Exec>("align${cap}NativeLibs16k") {
        group = "build"
        description = "Realigns WireGuard native libs to 16 KB page boundaries"
        dependsOn("strip${cap}DebugSymbols")
        // Resolved at configuration time so no closure (and no config-cache
        // script reference) is involved; only plain strings reach commandLine.
        inputs.dir(nativeLibsDir)
        val dir = nativeLibsDir.get().asFile.absolutePath
        commandLine(pythonExecutable, alignScript.absolutePath, dir)
    }
    tasks.matching { it.name == "package$cap" }.configureEach {
        dependsOn(alignTask)
    }
}

kotlin {
    jvmToolchain(17)
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

    implementation(libs.wireguard.tunnel)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)

    debugImplementation(libs.androidx.ui.tooling)
}
