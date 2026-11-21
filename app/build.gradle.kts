plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.bothost.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bothost.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // Node.js Mobile needs the Android NDK + CMake and the matching libnode
    // package placed in app/libnode as described in README.md.
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        pip {
            install("discord.py")
            install("python-dotenv")
        }
    }
}
