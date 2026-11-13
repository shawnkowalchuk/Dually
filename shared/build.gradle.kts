// The code both phones share (ADR-014, phase 3). Kotlin Multiplatform: the same rules compiled
// for Android, where the app uses them, and for iOS, where the iPhone app will. It is moved here
// from the app a package at a time, unchanged, with its tests; the first is the trip rules
// (core/trip, 2026-11-12). Nothing here may use Android's or Java's own APIs: the iOS build
// would not compile.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    alias(libs.plugins.android.kotlin.multiplatform.library)
    // The service book and, later, the export format are written with kotlinx.serialization.
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.shawnkowalchuk.milo.shared"
        compileSdk = 37
        minSdk = 34
        // The shared tests run on the computer, beside the app's own unit tests.
        withHostTest {}
    }
    // iPhones, and the simulator on an Apple-silicon Mac. iOS 16 is the floor (ADR-014); it is
    // set where the iPhone app is built, in Xcode.
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        // As in the app: a compiler warning fails the build.
        allWarningsAsErrors = true
        // The shared date, language and number types are `expect` classes whose Android side is
        // java.time's own (core/time, core/text, core/number). Kotlin calls such classes Beta and
        // warns about each unless this flag says the module means to use them (KT-61573).
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest {
            // The made-up GPS tracks the tests drive with, which the app's own tests use too
            // (app/build.gradle.kts adds the same folder): one copy, two users.
            kotlin.srcDir("src/testFixtures/kotlin")
            dependencies {
                implementation(kotlin("test"))
            }
        }
        // The iPhone's dates. On Android the shared date types are java.time's own (core/time).
        iosMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
    }
}
