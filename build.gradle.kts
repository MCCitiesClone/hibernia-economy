// =============================================================================
// Hibernia Economy monorepo — root build.
//
// Owns no source. It centralises the external plugin versions (so each
// subproject applies them WITHOUT a version — a single build rejects the same
// versioned plugin being requested twice) and sets the shared coordinates.
//
//   ./gradlew build            assemble + test every subproject
//   ./gradlew :treasury:build  build one project
//   ./gradlew :chestshop:plugin:shadowJar     build ChestShop.jar
// =============================================================================

plugins {
    id("com.gradleup.shadow") version "9.0.2" apply false
    id("org.springframework.boot") version "4.0.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    // NOTE: org.flywaydb.flyway is intentionally NOT centralized here. It must
    // load on the same buildscript classloader as the JDBC driver, so it's
    // declared with its version inside economy-flyway/build.gradle.kts.
}

// The treasury-api / business-api subprojects derive their version from
// rootProject.version (they publish under it), so set it here. The publish
// workflows pass -Pversion=… to cut a release, so honour that property; default
// to the dev snapshot otherwise. Plugins/apps that pin their own version in their
// build.gradle.kts override this for themselves.
allprojects {
    group = "io.paradaux"
    version = providers.gradleProperty("version").orElse("2.2.1-SNAPSHOT").get()
}
