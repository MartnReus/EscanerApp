plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "com.example.escanerapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.escanerapp"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // CameraX core library using the camera2 implementation
    // The following line is optional, as the core library is included indirectly by camera-camera2
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    // If you want to additionally use the CameraX Lifecycle library
    implementation(libs.androidx.camera.lifecycle)
    // If you want to additionally use the CameraX VideoCapture library
    implementation(libs.androidx.camera.video)
    // If you want to additionally use the CameraX View class
    implementation(libs.androidx.camera.view)
    // If you want to additionally add CameraX ML Kit Vision Integration
    // implementation(libs.androidx.camera.mlkit.vision)
    // If you want to additionally use the CameraX Extensions library
    implementation(libs.androidx.camera.extensions)
    implementation(project(":OpenCV"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}