plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localVersionCode = 1
val maxAndroidVersionCode = 2_100_000_000

val releaseBundleRequested =
    gradle.startParameter.taskNames.any { requestedTask ->
        requestedTask.substringAfterLast(':') == "bundleRelease"
    }
val releaseVersionCode = providers.environmentVariable("ATC_RELEASE_VERSION_CODE")
val releaseStoreFile = providers.environmentVariable("ATC_RELEASE_STORE_FILE")
val releaseStorePassword = providers.environmentVariable("ATC_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ATC_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ATC_RELEASE_KEY_PASSWORD")
val ciEnvironment = providers.environmentVariable("CI")

val configuredReleaseVersionCode =
    if (releaseBundleRequested) {
        if (!ciEnvironment.isPresent ||
            ciEnvironment.get().lowercase() !in setOf("true", "1")
        ) {
            throw GradleException("bundleRelease is restricted to CI=true release jobs.")
        }

        val missingSigningValues =
            listOf(
                "ATC_RELEASE_STORE_FILE" to releaseStoreFile,
                "ATC_RELEASE_STORE_PASSWORD" to releaseStorePassword,
                "ATC_RELEASE_KEY_ALIAS" to releaseKeyAlias,
                "ATC_RELEASE_KEY_PASSWORD" to releaseKeyPassword,
            ).filter { (_, value) -> !value.isPresent || value.get().isEmpty() }
                .map { (name, _) -> name }
        if (missingSigningValues.isNotEmpty()) {
            throw GradleException(
                "bundleRelease requires these CI environment variables: " +
                    missingSigningValues.joinToString(),
            )
        }

        val value =
            if (releaseVersionCode.isPresent) {
                releaseVersionCode.get().trim().toIntOrNull()
            } else {
                null
            }
        if (value == null || value !in (localVersionCode + 1)..maxAndroidVersionCode) {
            throw GradleException(
                "ATC_RELEASE_VERSION_CODE must be a monotonically increasing integer between " +
                    "${localVersionCode + 1} and $maxAndroidVersionCode.",
            )
        }
        value
    } else {
        localVersionCode
    }

val configuredReleaseStoreFile =
    if (releaseBundleRequested) {
        file(releaseStoreFile.get()).also { storeFile ->
            if (!storeFile.isFile) {
                throw GradleException("ATC_RELEASE_STORE_FILE does not point to a readable file.")
            }
        }
    } else {
        null
    }

android {
    namespace = "com.stuart.atccontroller"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.stuart.atccontroller"
        minSdk = 26
        targetSdk = 37
        versionCode = configuredReleaseVersionCode
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    val releaseSigningConfig =
        configuredReleaseStoreFile?.let { storeFile ->
            signingConfigs.create("release") {
                this.storeFile = storeFile
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfig != null) signingConfig = releaseSigningConfig
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }
}

kotlin {
    jvmToolchain(21)
}

val validateReleaseBundle by tasks.registering {
    group = "verification"
    description = "Prevents unsigned or non-CI release bundles from being produced."
    notCompatibleWithConfigurationCache(
        "Release signing credentials must never be stored in the configuration cache.",
    )
    doLast {
        check(releaseBundleRequested && configuredReleaseStoreFile != null) {
            "Invoke the fully qualified :app:bundleRelease task using the documented CI signing " +
                "environment; abbreviated release task names are intentionally unsupported."
        }
    }
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    dependsOn(validateReleaseBundle)
}

tasks.configureEach {
    if (name != validateReleaseBundle.name && name.contains("Release")) {
        mustRunAfter(validateReleaseBundle)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
