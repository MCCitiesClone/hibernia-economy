// :common — a tiny pure-JVM library shared across the build graph (ADT-22).
// Holds framework-free utilities duplicated 3-4× before: UUID ↔ BINARY(16)
// conversion and the JWT signing-key derivation that the mint side
// (treasury-api-plugin) and the verify side (treasury-rest-api) must agree on.
// No Paper/Spring/MyBatis deps — anyone can depend on it, including the Spring
// treasury-rest-api (its first project() dependency).

plugins {
    `java-library`
    id("io.paradaux.jvm-conventions")
}

group = "io.paradaux"
version = rootProject.version

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
