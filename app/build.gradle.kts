plugins {
    id("com.android.application")
}

android {
    namespace = "ai.neo24.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.neo24.app.fdroid"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "1.1.7-fdroid"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

     }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = false
        viewBinding = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.webkit:webkit:1.14.0")

    implementation("com.google.android.material:material:1.14.0")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(
        "androidx.test.ext:junit:1.2.1"
    )

    androidTestImplementation(
        "androidx.test.espresso:espresso-core:3.6.1"
    )
}