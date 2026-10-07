plugins {
    id("com.android.application")
}

android {
    namespace = "com.taxiscan.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.taxiscan.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "2.1.0"
    }
}
