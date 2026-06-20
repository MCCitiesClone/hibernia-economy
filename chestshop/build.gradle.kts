import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// =============================================================================
// ChestShop — root build (Gradle port of the former Maven reactor)
//
// Module layout mirrors the old Maven modules:
//   :plugin                       core plugin (thin jar; classes + resources)
//   :adapter-{spigot,paper}-*     version-specific server adapters
//   :assemble                     shades plugin + adapters + libs -> ChestShop.jar
//
// Only :assemble applies the Shadow plugin; it is the artifact that ships.
// =============================================================================

// --- Build metadata (replaces the static/dynamic_build_number Maven profiles) -
// Jenkins/CI sets BUILD_NUMBER; otherwise it's a "manual" build stamped with a
// timestamp. Exposed to subprojects via extra properties.
val ciBuildNumber: String? = System.getenv("BUILD_NUMBER")
val resolvedBuildType: String = if (ciBuildNumber != null) "jenkins" else "manual"
val resolvedBuildNumber: String = ciBuildNumber ?: "0"
val buildTimestamp: String = OffsetDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
val resolvedBuildDescription: String =
    if (ciBuildNumber != null) "(build $ciBuildNumber)" else "(compiled at $buildTimestamp)"

// --- Spigot API version (replaces the spigot_version_latest Maven profile) -----
// Default mirrors the upstream Maven default (lowest-common 1.13.2 API). Pass
// -PspigotVersionLatest to compile the core against the latest server API.
val spigotApiVersion: String =
    if (project.hasProperty("spigotVersionLatest")) "1.21.11-R0.1-SNAPSHOT"
    else "1.13.2-R0.1-SNAPSHOT"

allprojects {
    group = "io.paradaux"
    version = "4.0.2"

    extra["buildType"] = resolvedBuildType
    extra["buildNumber"] = resolvedBuildNumber
    extra["buildTimestamp"] = buildTimestamp
    extra["buildDescription"] = resolvedBuildDescription
    // Full version string substituted into plugin.yml (was ${bukkit.plugin.version}).
    extra["bukkitPluginVersion"] = "$version $resolvedBuildDescription"
    extra["spigotApiVersion"] = spigotApiVersion
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/groups/public")
        maven("https://maven.enginehub.org/repo/")
        maven("https://repo.codemc.org/repository/maven-public/")
        maven("https://ci.ender.zone/plugin/repository/everything/")
        maven("https://nexus.hc.to/content/repositories/pub_releases/")
        maven("https://repo.minebench.de/")
        maven("https://jitpack.io")
        maven("https://ci.nyaacat.com/maven/")
        maven("https://raw.githubusercontent.com/FabioZumbi12/RedProtect/mvn-repo/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://repo.paradaux.io/releases")
        maven("https://repo.paradaux.io/snapshots")
        // Vendored plugin jars not available from any public repo (Maven-layout).
        // Resolve against the chestshop project dir explicitly — `rootDir` is the
        // monorepo root in the single build, not this subtree.
        maven {
            name = "chestshopLocalRepo"
            url = uri("${project(":chestshop").projectDir}/repo")
        }
    }

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
    }
}

// (No lifecycle tasks here: in the single root build, `./gradlew build` runs
// `build` across the chestshop subprojects natively — :chestshop:assemble:build
// produces ChestShop.jar and :chestshop:plugin:test runs the tests.)

