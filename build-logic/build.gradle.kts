plugins {
    `kotlin-dsl`
}

// Compile the convention plugins themselves on JDK 21 (the monorepo toolchain).
// Without this, Gradle's toolchain auto-detection can pick a stray JRE that has
// no compiler and the included build fails to configure.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // Needed so the paper-server convention plugin can reference the ShadowJar
    // task type and configure it. The version matches the one centralized in the
    // root build's `plugins { … apply false }` block (com.gradleup.shadow 9.0.2).
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.0.2")
}
