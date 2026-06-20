// =============================================================================
// Hibernia Economy monorepo — root build.
//
// Owns no source. It centralises the external plugin versions (so each
// subproject applies them WITHOUT a version — a single build rejects the same
// versioned plugin being requested twice) and sets the shared coordinates.
//
//   ./gradlew build            assemble + test every subproject
//   ./gradlew :treasury:build  build one project
//   ./gradlew :chestshop:assemble:shadowJar   build ChestShop.jar
// =============================================================================

plugins {
    id("com.gradleup.shadow") version "9.0.2" apply false
    id("org.springframework.boot") version "4.0.3" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.flywaydb.flyway") version "10.22.0" apply false
}

// The treasury-api / business-api subprojects derive their version from
// rootProject.version (they publish under it), so set it here. Plugins/apps set
// their own version in their build.gradle.kts and are unaffected.
allprojects {
    group = "io.paradaux"
    version = "2.2.1-SNAPSHOT"
}
