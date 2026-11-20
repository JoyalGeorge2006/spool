import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.takahirom.roborazzi") version "1.79.0"
}

/*
 * BitChord for Wear OS: a remote for the phone app, not a second player.
 *
 * It holds no library, streams nothing and runs no Palette or blur of its own —
 * the phone sends it what is playing, the cover as a 240px JPEG, and the mesh
 * colours the phone's player already computed (see WearBridge in :app). Which is
 * what keeps it small and smooth on a Galaxy Watch 4 class watch.
 *
 * It has to share the phone app's application id *and* signing key, because the
 * Wearable Data Layer only delivers between apps that match on both. So:
 *   dev  -> app.spool.music.dev   pairs only with the phone's dev build
 *   prod -> app.spool.music pairs only with the phone's prod build
 * and each build type is signed exactly the way the phone's is (debug key for
 * debug, the release keystore for release), so a watch build pairs with the
 * phone build of the same variant.
 */

val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val betaSuffix = rootProject.extra["bitchordBetaSuffix"] as String

android {
    namespace = "com.music.bitchord.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.spool.music"
        // Wear OS 3, the oldest a Galaxy Watch 4 ever shipped with.
        minSdk = 30
        targetSdk = 36
        // The phone's version — see the root build.gradle.kts.
        versionCode = rootProject.extra["bitchordVersionCode"] as Int
        versionName = rootProject.extra["bitchordVersionName"] as String
    }

    sourceSets {
        // The phone <-> watch protocol, shared with :app. See WearProtocol.
        getByName("main") { java.srcDir("protocol") }
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationId = "app.spool.music.dev"
            resValue("string", "app_name", "BitChord Dev")
        }
        create("prod") {
            dimension = "env"
            resValue("string", "app_name", "BitChord")
        }
    }

    signingConfigs {
        // The phone's keystore, found the same way app/build.gradle.kts finds it.
        val store = signing.getProperty("storeFile")?.let { rootProject.file(it) }
        if (store != null && store.exists()) {
            create("release") {
                storeFile = store
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        /*
         * Debug is shrunk and non-debuggable too, unlike the phone's. It still
         * signs with the debug key — that is the part pairing depends on — but a
         * debuggable, un-R8'd Compose app is the slow kind, and on a watch CPU
         * that is the difference between a smooth crown and a stuttering one.
         * Pass -Pbitchord.wearDebuggable=true to get a debugger back.
         */
        debug {
            if (betaSuffix.isNotEmpty()) versionNameSuffix = "-$betaSuffix"
            val debuggable = providers.gradleProperty("bitchord.wearDebuggable").orNull == "true"
            isDebuggable = debuggable
            isMinifyEnabled = !debuggable
            isShrinkResources = !debuggable
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        release {
            if (betaSuffix.isNotEmpty()) versionNameSuffix = "-$betaSuffix"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Null without a keystore, like the phone: an unsigned APK rather
            // than a build failure.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures {
        compose = true
        resValues = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin/**",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Media3's custom DataSource and session APIs are all marked unstable.
        optIn.add("androidx.media3.common.util.UnstableApi")
    }
}

// Named after the phone APKs (BitChord-v1.8.apk) but with their own prefix, so
// the phone's updater — which only takes assets starting "BitChord-" — can never
// pick a watch APK by mistake.
base.archivesName.set("BitChordWear")

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.7.6")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.79.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.79.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.6")

    implementation("androidx.wear.compose:compose-material3:1.6.2")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")
    implementation("androidx.wear.compose:compose-navigation:1.6.2")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    // AmbientLifecycleObserver: the always-on player.
    implementation("androidx.wear:wear:1.4.0")
    // NowPlayingListener: our icon on the watch face, opening our player.
    implementation("androidx.wear:wear-ongoing:1.1.0")

    // The watch as the speaker: a player reading from the phone, in a session
    // so it plays on with the screen off. Same Media3 as the phone app.
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")

    // The Data Layer: state from the phone, commands to it.
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    // Lets a sideloaded install use the baseline profiles Compose ships with,
    // instead of starting every screen interpreted.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
}

// Robolectric's API34 curved-text path reads direct-buffer addresses on the host JVM.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    jvmArgs("--add-opens=java.base/java.nio=ALL-UNNAMED")
}
