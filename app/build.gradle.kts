import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// Release signing credentials live in the gitignored /keystore.properties (repo root), never in
// this file or in source control. Missing on a fresh clone -> release build type just stays
// unsigned (assembleDebug/bundleDebug are unaffected either way).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Public developer contact email, from gradle.properties' logez.contactEmail (see the comment there).
// Validated here so a typo fails the build instead of shipping in the privacy policy; the pattern
// also rules out quotes and backslashes, so the value can be spliced into BuildConfig unescaped.
val contactEmail: String = providers.gradleProperty("logez.contactEmail").orNull?.trim().orEmpty()
require(contactEmail.isEmpty() || Regex("^[^@\\s\"\\\\]+@[^@\\s\"\\\\]+\\.[^@\\s\"\\\\]+$").matches(contactEmail)) {
    "logez.contactEmail in gradle.properties is not an email address: '$contactEmail'"
}

android {
    namespace = "com.enil.logez"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.enil.logez"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CONTACT_EMAIL", "\"$contactEmail\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            // R8 on (Play-readiness audit, 2026-09-25): the release APK carried 77 MB of
            // unshrunk dex, 11,400 of its classes Material icons the app never uses. Shrinking,
            // optimisation and obfuscation also speed up startup. The mapping file travels inside
            // the AAB, so Play de-obfuscates crash reports automatically. See proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // The release build exactly as Play will get it (R8, resource shrinking, release
        // resources), but installable beside a debug or release copy and signed with the debug
        // key, so the shrunk app can be regression-tested without the upload keystore:
        //   ./gradlew :app:assembleStaging
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        // M20e: raised 11 -> 17 (Owner, 2026-09-08 structured question), required by
        // compose-unstyled's install docs. Below the JBR 21 Gradle-daemon pin
        // (gradle.properties); does not touch the pinned AGP/Kotlin/KSP/Compose-BOM quadruple.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        // Play-readiness audit, 2026-09-25: two UI-state defaults read java.time.LocalDate.EPOCH,
        // a field that only exists on API 34+. minSdk is 26, so every Android 8-13 phone crashed
        // opening the Workout or Statistics tab, and nothing failed the build. A new-API call is
        // always a crash on older devices, never a style nit, so it now stops the build outright.
        fatal += "NewApi"
        // The release variant is the one Play receives; its lint run gates `assembleRelease` and
        // `bundleRelease` through AGP's vital-lint task, so an error-severity finding there cannot
        // ship unnoticed.
        checkReleaseBuilds = true
        abortOnError = true
    }
}

// PHASE2_PLAN.md §10.4 — exportSchema=true from day one; schemas/ is committed alongside code.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // M20a: long-press drag reorder for routine/exercise lists — replaces the arrow-button
    // reorder mode. Re-attempt of the M3 library (removed then for blocking scroll — lessons 2026-08-23).
    implementation(libs.reorderable)

    // M20b: local-file image loading with downsampling + memory/disk cache for custom-exercise
    // photos. NO coil-network-* anywhere -- this loader itself never touches the network, regardless
    // of the MapLibre-driven INTERNET grant added in M21 (2026-09-12) for map tiles elsewhere.
    implementation(libs.coil.compose)
    implementation(libs.coil.core)

    // M20c (ADR-0009): radar plot for the muscle-balance card only; BarChart/LineChart stay
    // hand-drawn.
    implementation(libs.koalaplot.core)

    // M20d: non-modal (interact-behind) sheet for the live-workout plate calculator.
    implementation(libs.flexible.bottomsheet)

    // M20e: renderless primitives styled with our own tokens where M3 chrome fights the Neon Lab
    // look (Settings sliders + radio dialogs). No sheet artifact -- M20e kept FlexibleBottomSheet
    // (decisions.md 2026-09-08).
    implementation(libs.composeunstyled.slider)
    implementation(libs.composeunstyled.dialog)

    // M20f: swipeable month calendar; java.time artifact — the calendar code is java.time end to end.
    implementation(libs.calendar.compose)


    // M21a: FusedLocationProviderClient for GPS run/walk tracking. Verified by direct AAR
    // manifest inspection to declare zero permissions of its own, including no INTERNET
    // (decisions.md 2026-09-09) -- it talks to the already-networked Play services process over
    // local Binder IPC, never opening a socket from this app's own process.
    implementation(libs.play.services.location)

    // M21b: originally offline map rendering (a bundled Metro Manila MBTiles, no live tile
    // fetching), with its SDK-declared INTERNET/ACCESS_NETWORK_STATE/ACCESS_WIFI_STATE stripped via
    // tools:node="remove". M21 (2026-09-12) reverses that: those three permissions are now genuinely
    // declared and used for live MapTiler tile fetches (AndroidManifest.xml) -- this is the app's
    // only network-facing dependency.
    implementation(libs.maplibre.android.sdk)
    implementation(libs.androidx.health.connect.client)

    // M22a: in-app camera capture for progress photos (no external camera-intent hand-off,
    // no overlay compositing this pass -- see libs.versions.toml's camerax entry).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // M23b: home-screen widget. Glance is Compose-for-RemoteViews -- a separate composable
    // vocabulary, not interoperable with the app's own Compose UI. androidx.work is pinned right
    // below because glance-appwidget's POM otherwise supplies work-runtime-ktx 2.7.1 transitively;
    // MidnightWidgetWorker is the direct consumer. No custom WorkerFactory is introduced, so
    // WorkManager's androidx.startup auto-init is used as-is and nothing needs disabling.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)
    testImplementation(libs.androidx.glance.appwidget.testing)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

// A release must never ship the privacy policy's "contact email not set" placeholder (Owner
// decision, 2026-09-25). Hooked on the two packaging tasks rather than on compilation, so lint and
// unit tests still run on a checkout that has not set the address yet.
tasks.configureEach {
    if (name == "packageRelease" || name == "packageReleaseBundle") {
        val email = contactEmail
        doFirst {
            if (email.isEmpty()) {
                throw GradleException(
                    "logez.contactEmail is empty in gradle.properties. Set the public developer contact " +
                        "email before building a release; it appears in the privacy policy.",
                )
            }
        }
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint()
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    ignoreFailures = true 
}