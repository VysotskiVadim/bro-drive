plugins {
    id("io.ktor.plugin") version "2.3.9"
    application
    id("org.jetbrains.kotlin.jvm")
}

application {
    mainClass = "com.mapbox.hackathon.backend.MainKt"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-websockets")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation(project(":shared"))
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}