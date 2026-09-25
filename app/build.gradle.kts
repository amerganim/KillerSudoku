import java.util.Properties

/**
 * Ad identifiers, as in Nonogram (its build plan 8.4): real IDs come from
 * local.properties, which is gitignored, and are injected through BuildConfig. Debug
 * builds always use Google's published test units - safe to commit because they are
 * nobody's inventory, and essential, because real ads served to a developer tapping the
 * same screen fifty times is what gets AdMob accounts suspended.
 *
 * Killer Sudoku is its own AdMob app with its own units. Nothing is shared with Nonogram.
 */
val googleTestAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val googleTestInterstitialUnit = "ca-app-pub-3940256099942544/1033173712"
val googleTestRewardedUnit = "ca-app-pub-3940256099942544/5224354917"

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun adProperty(name: String): String? =
    (localProperties.getProperty(name) ?: providers.gradleProperty(name).orNull)?.trim()?.takeIf { it.isNotEmpty() }

/**
 * `-PlocalReleaseCheck=true` signs the release with the debug key and permits test ad
 * units, so the minified (R8) build can be run on a device without a signing key or an
 * AdMob account. Deliberately not shippable: Play rejects debug-signed uploads.
 */
val localReleaseCheck = providers.gradleProperty("localReleaseCheck").orNull == "true"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.ganim.killersudoku"
    // Compose 1.12 requires compileSdk 37; targetSdk stays at what Play requires (36).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ganim.killersudoku"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            buildConfigField("String", "ADMOB_APP_ID", "\"$googleTestAdMobAppId\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"$googleTestInterstitialUnit\"")
            buildConfigField("String", "AD_UNIT_REWARDED", "\"$googleTestRewardedUnit\"")
            buildConfigField("boolean", "USES_TEST_ADS", "true")
            manifestPlaceholders["admobAppId"] = googleTestAdMobAppId
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            if (localReleaseCheck) signingConfig = signingConfigs.getByName("debug")

            val appId = adProperty("admob.appId")
            val interstitial = adProperty("admob.unit.interstitial")
            val rewarded = adProperty("admob.unit.rewarded")
            buildConfigField("String", "ADMOB_APP_ID", "\"${appId ?: googleTestAdMobAppId}\"")
            buildConfigField("String", "AD_UNIT_INTERSTITIAL", "\"${interstitial ?: googleTestInterstitialUnit}\"")
            buildConfigField("String", "AD_UNIT_REWARDED", "\"${rewarded ?: googleTestRewardedUnit}\"")
            buildConfigField("boolean", "USES_TEST_ADS", "${appId == null}")
            manifestPlaceholders["admobAppId"] = appId ?: googleTestAdMobAppId
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time only exists from API 26; the daily and streak logic uses LocalDate.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

// The exported Room schema is committed, so every future migration can be reviewed and
// verified against it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":engine"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.play.services.ads)
    implementation(libs.billing.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

/**
 * Refuses to package a release that would ship Google's test ad units - it would earn
 * nothing, silently. Checked at execution time so a fresh clone can still build and test.
 */
val verifyReleaseAdUnits = tasks.register("verifyReleaseAdUnits") {
    group = "verification"
    description = "Fails if a release build would use Google's test ad units."
    val appId = adProperty("admob.appId")
    val interstitial = adProperty("admob.unit.interstitial")
    val rewarded = adProperty("admob.unit.rewarded")
    // Plain values only: anything from the script inside a task action breaks the configuration cache.
    val allowTestAds = localReleaseCheck
    doLast {
        if (allowTestAds) {
            logger.lifecycle("localReleaseCheck: TEST ad units and the debug key. For checking R8 output only - do not upload.")
        }
        val missing = buildList {
            if (appId == null) add("admob.appId")
            if (interstitial == null) add("admob.unit.interstitial")
            if (rewarded == null) add("admob.unit.rewarded")
        }
        if (missing.isNotEmpty() && !allowTestAds) {
            throw GradleException(
                "Release build is missing real AdMob identifiers: ${missing.joinToString(", ")}. " +
                    "Add them to local.properties (gitignored). Without them the release ships test ads.",
            )
        }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(verifyReleaseAdUnits)
}
