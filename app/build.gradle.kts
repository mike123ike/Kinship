import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")

    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use {
            load(it)
        }
    }
}

android {
    namespace = "com.pkg.civicfix"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.pkg.civicfix"

        minSdk = 28
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        val mapsKey =
            localProperties.getProperty(
                "MAPS_API_KEY"
            ) ?: ""

        manifestPlaceholders[
            "MAPS_API_KEY"
        ] = mapsKey

        buildConfigField(
            "String",
            "CLOUD_NAME",
            "\"${localProperties.getProperty("CLOUD_NAME") ?: ""}\""
        )

        buildConfigField(
            "String",
            "UPLOAD_PRESET",
            "\"${localProperties.getProperty("UPLOAD_PRESET") ?: ""}\""
        )

        buildConfigField(
            "String",
            "WORLD_NEWS_API_KEY",
            "\"${localProperties.getProperty("WORLD_NEWS_API_KEY") ?: ""}\""
        )

        buildConfigField(
            "String",
            "NEWSDATA_API_KEY",
            "\"${localProperties.getProperty("NEWSDATA_API_KEY") ?: ""}\""
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    implementation(
        libs.androidx.activity.compose
    )

    implementation(
        libs.androidx.compose.material3
    )

    implementation(
        libs.androidx.compose.ui
    )

    implementation(
        libs.androidx.compose.ui.graphics
    )

    implementation(
        libs.androidx.compose.ui.tooling.preview
    )

    implementation(
        libs.androidx.constraintlayout
    )

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.androidx.lifecycle.runtime.ktx
    )

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        platform(
            libs.androidx.compose.bom
        )
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling
    )

    implementation(
        "com.github.bumptech.glide:glide:4.16.0"
    )

    // Firebase + map dependencies

    implementation(
        platform(
            "com.google.firebase:firebase-bom:34.14.0"
        )
    )

    implementation(
        "com.google.firebase:firebase-analytics"
    )

    implementation(
        "com.google.android.material:material:1.12.0"
    )

    implementation(
        "androidx.cardview:cardview:1.0.0"
    )

    implementation(
        "com.google.firebase:firebase-auth"
    )

    implementation(
        "com.google.firebase:firebase-firestore"
    )

    implementation(
        "com.google.firebase:firebase-storage"
    )

    implementation(
        "androidx.credentials:credentials:1.3.0"
    )

    implementation(
        "androidx.credentials:credentials-play-services-auth:1.3.0"
    )

    implementation(
        "com.google.android.libraries.identity.googleid:googleid:1.1.1"
    )

    implementation(
        "com.google.maps.android:maps-compose:4.3.0"
    )

    implementation(
        "com.google.android.gms:play-services-maps:19.0.0"
    )

    implementation(
        "com.google.android.gms:play-services-location:21.3.0"
    )

    implementation(
        "io.coil-kt:coil-compose:2.5.0"
    )

    implementation(
        "androidx.navigation:navigation-compose:2.7.6"
    )

    implementation(
        "androidx.appcompat:appcompat:1.6.1"
    )

    implementation(
        "com.cloudinary:cloudinary-android:3.0.2"
    )

    implementation(
        "com.firebase:geofire-android-common:3.2.0"
    )
}