plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.stuart.atccontroller.benchmark"
    compileSdk = 37
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
