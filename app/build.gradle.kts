import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "pl.paragony"
    compileSdk = 36

    defaultConfig {
        applicationId = "pl.paragony"
        minSdk = 26 // D2: Android 8.0
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        // Exported Room schemas feed MigrationTestHelper in androidTest.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        unitTests.all {
            // Anonymized reading samples shared with the Python prototype.
            it.systemProperty("testdata.dir", rootProject.file("testdata").absolutePath)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Room schema JSON goes to git; needed for migration tests (MigrationTest).
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Database (S3, D18)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Reading results are JSON (JsonElement API, no compiler plugin)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)

    // On-device tests: DAO queries and migrations (run in Android Studio, need a phone or emulator)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
