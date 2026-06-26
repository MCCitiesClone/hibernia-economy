import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// =============================================================================
// ChestShop — a single Paper plugin module, compiled against the Paper 1.21.11
// API and shaded straight to chestshop-<version>.jar. The former per-server-version
// adapter modules + the assemble module (and the old `:chestshop:plugin`
// nesting) were folded into this one module once the core adopted a single
// modern baseline (PAR-258). group + version are inherited from the root
// allprojects block (the unified monorepo version).
// =============================================================================

plugins {
    `java-library`
    jacoco
    id("com.gradleup.shadow")
    id("io.paradaux.jvm-conventions")
}

// The Java toolchain (21) and JavaCompile encoding/release come from
// io.paradaux.jvm-conventions. ChestShop keeps its own bespoke repositories,
// shadow relocations, resource filtering, and build metadata below — they
// diverge too far from the other plugins to share.

repositories {
    // mavenLocal is opt-in (-PuseMavenLocal) so normal/CI builds resolve
    // hibernia-framework only from the declared remotes — reproducible, never a
    // stale local artifact. Pass the flag (added first) when iterating on the
    // hibernia-framework submodule locally. (PAR-267)
    if (providers.gradleProperty("useMavenLocal").isPresent) {
        mavenLocal()
    }
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

// --- Build metadata (replaces the static/dynamic_build_number Maven profiles).
// Jenkins/CI sets BUILD_NUMBER; otherwise it's a "manual" build stamped with a
// timestamp. Folded in from the former aggregator project.
val ciBuildNumber: String? = System.getenv("BUILD_NUMBER")
val buildType: String = if (ciBuildNumber != null) "jenkins" else "manual"
val buildTimestamp: String = OffsetDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
val buildDescription: String =
    if (ciBuildNumber != null) "(build $ciBuildNumber)" else "(compiled at $buildTimestamp)"
// Full version string substituted into plugin.yml (was ${bukkit.plugin.version}).
val bukkitPluginVersion = "$version $buildDescription"

// Compiled against the Paper API (a superset of spigot-api) so the folded-in
// version adapters' Paper-only snapshot API (BlockDestroyEvent, getState(boolean),
// getHolder(boolean)) resolves. ChestShop runs on one modern server version, so
// the old 1.13.2 multi-version-adapter baseline is gone. The Paper version is
// pinned centrally in gradle/libs.versions.toml (libs.paper.api).

// Maven's `provided` scope is visible to tests; Gradle's `compileOnly` is not.
// Make the provided APIs visible to test *compilation*, but NOT to the test
// runtime — dumping the soft-deps' full closures onto the runtime classpath
// pulls a second Bukkit API (via worldguard) that collides with spigot-api and
// breaks Bukkit-backed static initializers (MaterialUtil) under test. The
// server API alone is added to the test runtime below.
configurations.testCompileOnly.get().extendsFrom(configurations.compileOnly.get())

// WorldGuard 7.1.0-SNAPSHOT transitively drags WorldEdit 8.0.0-SNAPSHOT (compiled
// for Java 25). Pin the whole WorldEdit line to the last Java-21-compatible 7.x.
configurations.configureEach {
    resolutionStrategy {
        force(
            "com.sk89q.worldedit:worldedit-core:7.3.9",
            "com.sk89q.worldedit:worldedit-bukkit:7.3.9",
        )
    }
}

dependencies {
    // --- server API: keep transitive (Bukkit compilation needs its deps:
    //     configurate/yaml/guava/etc.).
    compileOnly(libs.paper.api)
    compileOnly("org.apache.logging.log4j:log4j-core:2.17.2")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // Lombok — @Getter on the framework @ConfigurationComponent (matches treasury/business).
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // --- soft-depend plugin APIs: compiled against, never shaded, and resolved
    //     NON-TRANSITIVELY. We reference each plugin's own classes only; pulling
    //     their full dependency closures (as Gradle's compileOnly does, unlike
    //     Maven's provided scope) drags in dead/incompatible artifacts — e.g.
    //     RedProtect -> de.keyle:mypet (gone), worldguard -> worldedit 8.0.0
    //     -SNAPSHOT (needs Java 25). Add a specific transitive back explicitly
    //     below only if compilation actually requires it.
    compileOnly("com.herocraftonline.heroes:Heroes:1.5.5") { isTransitive = false }
    compileOnly("fr.xephi:authme:5.6.0-SNAPSHOT") { isTransitive = false }
    compileOnly("com.griefcraft.lwc:LWCX:2.2.5") { isTransitive = false }
    // WorldGuard integration genuinely needs WorldGuard's transitives (StateFlag,
    // Flags, RegionPermissionModel) + WorldEdit's BukkitAdapter, so these stay
    // transitive — but WorldEdit is force-pinned to 7.3.9 below (the snapshot
    // chain otherwise resolves worldedit 8.0.0-SNAPSHOT, which needs Java 25).
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.9") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.9") { isTransitive = false }
    // WorldGuard soft-depend: pull only its own API jars, non-transitively, so
    // none of its server-provided transitives (bukkit/gson/fastutil/guava) fight
    // paper-api. These coordinates are what worldguard-legacy:7.0.0-SNAPSHOT
    // resolved to; they carry the classes we touch (WorldGuard, StateFlag, Flags,
    // RegionPermissionModel, WorldGuardPlugin).
    compileOnly("com.sk89q.worldguard:worldguard-core:7.1.0-SNAPSHOT") { isTransitive = false }
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.1.0-SNAPSHOT") { isTransitive = false }
    compileOnly("com.github.TechFortress:GriefPrevention:16.12.0") { isTransitive = false }
    compileOnly("com.github.jojodmo:ItemBridge:b0054538c1") { isTransitive = false }
    compileOnly("br.net.fabiozumbi12.RedProtect:RedProtect-Spigot:7.7.3") { isTransitive = false }
    compileOnly("br.net.fabiozumbi12.RedProtect:RedProtect-Core:7.7.3") { isTransitive = false }
    compileOnly("nl.rutgerkok:blocklocker:1.9") { isTransitive = false }
    compileOnly("de.themoep.showitem:api:1.6.3") { isTransitive = false }
    // Substituted to the local Treasury/Business builds by the root composite
    // (settings.gradle.kts); the version here only governs standalone builds,
    // so it tracks the current dev version which carries the API ChestShop uses
    // (FirmApi.getFirmByAccountId, StaffApi.hasPermissionForAccount, …).
    compileOnly(project(":treasury:treasury-api")) { isTransitive = false }
    compileOnly(project(":business:business-api")) { isTransitive = false }
    compileOnly("me.crafter.mc:lockettepro:2.10-SNAPSHOT") { isTransitive = false }

    // --- HiberniaFramework: Guice DI, the annotation-driven Configurator,
    //     Commander, and i18n. Bundled (and relocated below) like treasury/business.
    implementation(libs.hibernia.framework)
    implementation(libs.guice)
    implementation(libs.reflections)

    // --- bundled libraries (relocated + shaded into the plugin jar below).
    // `api` (rather than implementation) is harmless now that everything is one
    // module; kept so they stay on the runtime classpath that shadowJar bundles.
    api("com.j256.ormlite:ormlite-jdbc:6.1")
    api("org.bstats:bstats-bukkit:3.0.1")
    api("javax.persistence:persistence-api:1.0")

    // Adventure is provided natively by Paper — NOT bundled or relocated (the old
    // bundled+relocated Adventure, plus the de.themoep MineDown/lang libraries, were
    // removed: MineDown's internals implemented a now-sealed Adventure interface and
    // broke at runtime. Messaging now uses the framework i18n + messages.properties).
    // ChestShop compiles against the serializers it uses directly; Paper supplies them.
    compileOnly("net.kyori:adventure-text-serializer-gson:4.21.0")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.21.0")

    // --- tests
    // Server API on the test runtime (tests mock/instantiate org.bukkit.* types).
    testRuntimeOnly(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
}

// plugin.yml carries ${bukkit.plugin.version}; the old Maven build expanded it
// at assemble-time. Do it here so the shaded jar ends up with the resolved
// version. Literal token replace (not Groovy expand) to avoid touching the
// MiniMessage/section-sign content in the language files.
tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("bukkitPluginVersion", bukkitPluginVersion)
    filesMatching("plugin.yml") {
        filter { line -> line.replace("\${bukkit.plugin.version}", bukkitPluginVersion) }
    }
    // LICENSE shipped at the jar root.
    from(projectDir) { include("LICENSE") }
}

tasks.test {
    useJUnitPlatform()
    // Mockito's inline mock maker attaches its agent dynamically on JDK 21+.
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

// Same in-scope set as the Maven jacoco config: exclude Bukkit-coupled glue and
// the vendored Base64. No coverage gate — visibility only (matches upstream).
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "io/paradaux/chestshop/utils/encoding/Base64.class",
                    "io/paradaux/chestshop/ChestShop.class",
                    "io/paradaux/chestshop/listeners/**",
                    "io/paradaux/chestshop/commands/**",
                    "io/paradaux/chestshop/plugins/**",
                    "io/paradaux/chestshop/database/**",
                )
            }
        })
    )
}

tasks.test { finalizedBy(tasks.jacocoTestReport) }

// =====================================================================
// Shaded chestshop-<version>.jar (formerly produced by the separate :assemble module).
// =====================================================================

// Shadow 9 + plain :jar both target build/libs/<name>.jar; disable :jar so the
// shaded plugin jar survives (see workspace CLAUDE.md rule 8).
tasks.jar { enabled = false }

tasks.shadowJar {
    // -> build/libs/chestshop-<version>.jar, standardised with the other plugins:
    // base name defaults to the project name ("chestshop") and version to the
    // pinned monorepo version from the root allprojects block (2.3.0-SNAPSHOT or
    // -Pversion). Only the "-all" classifier is dropped. (PAR-274)
    archiveClassifier.set("")

    // Bundle every runtime (api/implementation) dependency. The server API and
    // the soft-depend plugin APIs are compileOnly, so they never reach the
    // runtime classpath and are never shaded. (Replaced the old include-whitelist
    // when HiberniaFramework + Guice were added — enumerating Guice's transitives
    // in a whitelist is fragile; this mirrors treasury/business.)

    // Relocate shaded libraries so they don't clash with the server or other plugins.
    relocate("org.bstats", "io.paradaux.chestshop.Metrics.BStats")
    relocate("com.j256.ormlite", "io.paradaux.chestshop.Libs.ORMlite")
    relocate("javax.persistence", "io.paradaux.chestshop.Libs.javax.persistence")
    relocate("com.google.inject", "io.paradaux.chestshop.Libs.guice")
    relocate("javax.inject", "io.paradaux.chestshop.Libs.javaxinject")
    relocate("org.aopalliance", "io.paradaux.chestshop.Libs.aopalliance")

    mergeServiceFiles()

    // Top-level docs at the jar root.
    from(projectDir) { include("README.md", "SECURITY.md") }

    manifest {
        attributes(
            "Distribution-Type" to buildType,
            "Built-At" to buildTimestamp,
            "Build-Jdk" to System.getProperty("java.runtime.version"),
            "paperweight-mappings-namespace" to "mojang",
        )
    }
}

// Building the module produces the shaded jar.
tasks.assemble { dependsOn(tasks.shadowJar) }

// =====================================================================
// Dev-server staging. ChestShop can't apply io.paradaux.paper-server-conventions
// (its repos/shadow/resources diverge), so it carries its own copyPlugin that
// mirrors the convention: stage the shaded jar into server/plugins after every
// shadowJar. Pass -Pci=true to skip (CI, or to avoid disturbing a running dev
// server). A top-level subproject is one level under the root → "../server/plugins".
// =====================================================================
val pluginsDir = layout.projectDirectory.dir("../server/plugins")
val copyPlugin = tasks.register<Copy>("copyPlugin") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(pluginsDir)
    onlyIf { !project.hasProperty("ci") } // don't run on CI
    doFirst {
        // Remove any previously-staged ChestShop jar so the dev server never loads
        // two copies of the same plugin: the current chestshop-<version>.jar and
        // the legacy ChestShop.jar (case-insensitive — it predates the standardised
        // name from PAR-274).
        val re = Regex("^chestshop(-\\d.*)?\\.jar$", RegexOption.IGNORE_CASE)
        pluginsDir.asFile.listFiles { f -> f.isFile && re.matches(f.name) }?.forEach { it.delete() }
    }
}
tasks.shadowJar { finalizedBy(copyPlugin) }
