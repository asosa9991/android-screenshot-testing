plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("deviceShots") {
            id = "shots.device"
            implementationClass = "DeviceShotsConventionPlugin"
        }
    }
}
