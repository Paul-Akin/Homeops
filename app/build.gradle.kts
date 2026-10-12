import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.example.homeops"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.homeops"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-m1"
    }

    buildTypes {
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

    testOptions {
        unitTests {
            // Robolectric needs merged resources/manifest on the test classpath.
            isIncludeAndroidResources = true
            all { it.useJUnitPlatform() }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    // Bytecode target only; any installed JDK 17+ (e.g. Android Studio's bundled JDK 21) can run the build.
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Room Gradle plugin: exports the schema JSON for every DB version (DESIGN.md Section 13).
// Commit the generated app/schemas/**/N.json files; migration tests read them.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.serialization.json)

    // Unit tests: JUnit 5 for pure-Kotlin domain tests; the vintage engine runs
    // JUnit 4 tests on the same platform (Robolectric only supports JUnit 4 runners).
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
