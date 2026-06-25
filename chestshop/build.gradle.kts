import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// =============================================================================
// ChestShop — root build.
//
// A single buildable module, :plugin, which compiles against the Paper 1.21.11
// API and shades straight to ChestShop.jar. The former per-server-version
// adapter modules + the assemble module were folded into :plugin once the core
// adopted a single modern baseline.
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

allprojects {
    // group + version inherited from the root allprojects block (single mono-repo
    // version, 2.3.0-SNAPSHOT). ChestShop joins the unified version per PAR-243.
    group = "io.paradaux"

    extra["buildType"] = resolvedBuildType
    extra["buildNumber"] = resolvedBuildNumber
    extra["buildTimestamp"] = buildTimestamp
    extra["buildDescription"] = resolvedBuildDescription
    // Full version string substituted into plugin.yml (was ${bukkit.plugin.version}).
    extra["bukkitPluginVersion"] = "$version $resolvedBuildDescription"
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
// `build` on :chestshop:plugin natively — its shadowJar produces ChestShop.jar
// and its test task runs the tests.)

