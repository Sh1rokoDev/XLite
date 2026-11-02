plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.xlite.downloader"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.xlite.downloader"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("xliteAndroidVersionCode").get().toInt()
        versionName = providers.gradleProperty("xliteVersion").get()
        ndk {
            abiFilters += "arm64-v8a"
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

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(project(":shared"))
    // Referenced directly by the FileProvider entry in AndroidManifest.xml.
    implementation(libs.androidx.core.ktx)
}
