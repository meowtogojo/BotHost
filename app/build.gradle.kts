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

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        pip {
            install("discord.py")
            install("python-dotenv")
        }
    }
}


dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
}
