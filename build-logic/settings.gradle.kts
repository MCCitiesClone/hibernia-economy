// =============================================================================
// build-logic — an included build that provides the monorepo's convention
// plugins (io.paradaux.*-conventions). Wired into the root build via
// `pluginManagement { includeBuild("build-logic") }` in the root
// settings.gradle.kts, so subprojects can apply the conventions by id without a
// version. Keep this build dependency-light; it only compiles Gradle plugins.
// =============================================================================

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "build-logic"
