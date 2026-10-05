plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.slide8"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.slide8"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// O aplicativo usa somente classes fornecidas pelo SDK do Android.
