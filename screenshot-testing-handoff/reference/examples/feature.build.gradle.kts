plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi) // preview screenshots on the JVM: ./screenshots previews <module>
    id("shots.device")            // emulator screenshots: ./screenshots shots <module>
}

android {
    namespace = "com.example.chat"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }
    defaultConfig {
        minSdk = 26
    }
    testOptions {
        targetSdk = 36
        unitTests.isIncludeAndroidResources = true // Robolectric renders real resources
    }
}

roborazzi {
    // Goldens are committed next to the tests.
    outputDir.set(file("src/test/screenshots"))

    // Turns every @Preview in com.example.chat into a Robolectric screenshot test.
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    generateComposePreviewRobolectricTests {
        enable = true
        packages = listOf("com.example.chat")
        includePrivatePreviews = true
        robolectricConfig = mapOf("sdk" to "[35]")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(project(":preview-kit"))
    debugImplementation(libs.androidx.compose.ui.tooling)       // previews in Android Studio
    debugImplementation(libs.androidx.compose.ui.test.manifest) // Robolectric needs ComponentActivity

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json) // real org.json on the JVM; android.jar only has stubs
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.preview.scanner.support)
    testImplementation(libs.composable.preview.scanner)
}
