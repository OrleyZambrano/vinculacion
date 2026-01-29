import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val mapsApiKey = localProperties.getProperty("MAPS_API_KEY")
    ?: System.getenv("MAPS_API_KEY")
    ?: "REPLACE_WITH_YOUR_KEY"
val weatherApiKey = localProperties.getProperty("WEATHER_API_KEY")
    ?: System.getenv("WEATHER_API_KEY")
    ?: "REPLACE_WITH_YOUR_KEY"

// Configuración actualizada para resolver problemas de compatibilidad AAR
// - compileSdk y targetSdk actualizados a 36
// - Compatibilidad con androidx.activity:1.12.0, androidx.core:1.17.0, etc.
android {
    namespace = "com.example.vinculacion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.vinculacion"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        resValue("string", "google_maps_key", mapsApiKey)
        resValue("string", "weather_api_key", weatherApiKey)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        signingConfigs {
            create("release") {
                val keystorePath = localProperties.getProperty("RELEASE_STORE_FILE")
                if (!keystorePath.isNullOrBlank()) {
                    storeFile = file(keystorePath)
                }
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // applicationIdSuffix = ".debug" // Comentado para que coincida con google-services.json
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // Configuración para compatibilidad con nuevas APIs
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofitConverterGson)
    implementation(libs.okhttpLogging)
    implementation(libs.kotlinxCoroutinesAndroid)
    implementation(libs.androidxLifecycleRuntimeKtx)
    implementation(libs.glide)
    implementation(libs.androidxSwipeRefreshLayout)
    implementation(libs.androidxFragmentKtx)
    implementation(libs.androidxRoomRuntime)
    implementation(libs.androidxRoomKtx)
    ksp(libs.androidxRoomCompiler)
    implementation(libs.tfliteTaskVision)
    implementation(libs.tfliteTaskAudio)
    implementation(libs.playServicesMaps)
    implementation(libs.androidMapsUtils)
    implementation(libs.androidxDatastore)
    implementation(libs.playServicesLocation)
    implementation(libs.androidxExifinterface)
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth-ktx:23.0.0")
    implementation("com.google.firebase:firebase-firestore-ktx:25.0.0")

}