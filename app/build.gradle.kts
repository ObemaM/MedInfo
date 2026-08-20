
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.medinfo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.medinfo"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.4"
        setProperty("archivesBaseName", "MedInfo-v$versionName")
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

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }

    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Retrofit: HTTP-клиент для Android
    implementation(libs.retrofit)

    // Converter: Для автоматического преобразования JSON в Kotlin/Java объекты (Gson)
    implementation(libs.converter.gson)

    // OkHttp Logging Interceptor (для отладки)
    implementation(libs.logging.interceptor)
    implementation(libs.okhttp)

    // Поддержка корутин в Retrofit
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // SignalR
    implementation(libs.signalr)

    // RxJava3 (необходим для работы AccessTokenProvider в SignalR)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)

    // Для работы с JSON внутри SignalR
    implementation(libs.gson)

}
