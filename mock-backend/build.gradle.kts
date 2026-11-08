plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("com.example.mockbackend.MainKt")
}

dependencies {
    testImplementation(libs.junit)
}

// ./gradlew :mock-backend:run            serves on http://localhost:8080
// ./gradlew :mock-backend:run --args=9090
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
