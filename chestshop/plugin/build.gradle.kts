plugins {
    `java-library`
    jacoco
    id("com.gradleup.shadow")
}

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

    // --- bundled libraries (relocated + shaded into ChestShop.jar below).
    // `api` (rather than implementation) is harmless now that everything is one
    // module; kept so they stay on the runtime classpath that shadowJar bundles.
    api("com.j256.ormlite:ormlite-jdbc:6.1")
    api("de.themoep.utils:lang-bukkit:1.3-SNAPSHOT")
    api("de.themoep:minedown-adventure:1.7.2-SNAPSHOT")
    api("net.kyori:adventure-platform-bukkit:4.4.1-SNAPSHOT")
    api("net.kyori:adventure-text-serializer-gson:4.21.0") {
        exclude(group = "com.google.code.gson")
    }
    api("org.bstats:bstats-bukkit:3.0.1")
    api("javax.persistence:persistence-api:1.0")

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
val bukkitPluginVersion = extra["bukkitPluginVersion"] as String
tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("bukkitPluginVersion", bukkitPluginVersion)
    filesMatching("plugin.yml") {
        filter { line -> line.replace("\${bukkit.plugin.version}", bukkitPluginVersion) }
    }
    // LICENSE shipped at the jar root (was the plugin module's `../` resource).
    from(project(":chestshop").projectDir) { include("LICENSE") }
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
// Shaded ChestShop.jar (formerly produced by the separate :assemble module).
// =====================================================================
val buildType = extra["buildType"] as String
val buildTimestamp = extra["buildTimestamp"] as String

// Shadow 9 + plain :jar both target build/libs/<name>.jar; disable :jar so the
// shaded ChestShop.jar survives (see workspace CLAUDE.md rule 8).
tasks.jar { enabled = false }

tasks.shadowJar {
    // -> build/libs/ChestShop.jar (no version/classifier).
    archiveBaseName.set("ChestShop")
    archiveClassifier.set("")
    archiveVersion.set("")

    // Whitelist exactly the libraries to bundle; everything else (server +
    // soft-depend APIs) is compileOnly and never shaded. The plugin's own
    // classes — including the folded-in version adapters — are always included.
    dependencies {
        include(dependency("de.themoep:.*:.*"))
        include(dependency("de.themoep.utils:.*:.*"))
        include(dependency("net.kyori:.*:.*"))
        include(dependency("org.bstats:.*:.*"))
        include(dependency("com.j256.ormlite:.*:.*"))
        include(dependency("javax.persistence:.*:.*"))
    }

    // The 7 relocations from the Maven shade, verbatim.
    relocate("de.themoep.utils.lang", "io.paradaux.chestshop.Libs.Lang")
    relocate("de.themoep.minedown.adventure", "io.paradaux.chestshop.Libs.MineDown")
    relocate("net.kyori", "io.paradaux.chestshop.Libs.Kyori")
    relocate("org.bstats", "io.paradaux.chestshop.Metrics.BStats")
    relocate("com.j256.ormlite", "io.paradaux.chestshop.Libs.ORMlite")
    relocate("javax.persistence", "io.paradaux.chestshop.Libs.javax.persistence")

    mergeServiceFiles()

    // Top-level docs at the jar root.
    from(project(":chestshop").projectDir) { include("README.md", "SECURITY.md") }

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
