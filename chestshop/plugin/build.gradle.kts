plugins {
    `java-library`
    jacoco
}

// archivesName "chestshop" -> jar is chestshop-<version>.jar, matching the old
// Maven artifactId. :assemble shades this into the final ChestShop.jar.
base.archivesName.set("chestshop")

val spigotApiVersion = extra["spigotApiVersion"] as String

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
    compileOnly("org.spigotmc:spigot-api:$spigotApiVersion")
    compileOnly("org.apache.logging.log4j:log4j-core:2.17.2")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // --- soft-depend plugin APIs: compiled against, never shaded, and resolved
    //     NON-TRANSITIVELY. We reference each plugin's own classes only; pulling
    //     their full dependency closures (as Gradle's compileOnly does, unlike
    //     Maven's provided scope) drags in dead/incompatible artifacts — e.g.
    //     RedProtect -> de.keyle:mypet (gone), worldguard -> worldedit 8.0.0
    //     -SNAPSHOT (needs Java 25). Add a specific transitive back explicitly
    //     below only if compilation actually requires it.
    compileOnly("net.milkbowl.vault:Vault:1.6.6") { isTransitive = false }
    compileOnly("com.herocraftonline.heroes:Heroes:1.5.5") { isTransitive = false }
    compileOnly("fr.xephi:authme:5.6.0-SNAPSHOT") { isTransitive = false }
    compileOnly("com.griefcraft.lwc:LWCX:2.2.5") { isTransitive = false }
    compileOnly("com.daemitus.deadbolt:deadbolt:2.2") { isTransitive = false }
    // WorldGuard integration genuinely needs WorldGuard's transitives (StateFlag,
    // Flags, RegionPermissionModel) + WorldEdit's BukkitAdapter, so these stay
    // transitive — but WorldEdit is force-pinned to 7.3.9 below (the snapshot
    // chain otherwise resolves worldedit 8.0.0-SNAPSHOT, which needs Java 25).
    compileOnly("com.sk89q.worldedit:worldedit-core:7.3.9") { isTransitive = false }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.9") { isTransitive = false }
    compileOnly("com.sk89q.worldguard:worldguard-legacy:7.0.0-SNAPSHOT")
    compileOnly("com.github.TechFortress:GriefPrevention:16.12.0") { isTransitive = false }
    compileOnly("com.github.jojodmo:ItemBridge:b0054538c1") { isTransitive = false }
    compileOnly("br.net.fabiozumbi12.RedProtect:RedProtect-Spigot:7.7.3") { isTransitive = false }
    compileOnly("br.net.fabiozumbi12.RedProtect:RedProtect-Core:7.7.3") { isTransitive = false }
    compileOnly("com.webkonsept.bukkit.simplechestlock:simplechestlock:1.2.1") { isTransitive = false }
    compileOnly("org.yi.acru.bukkit.lockette:lockette:1.8.14") { isTransitive = false }
    compileOnly("nl.rutgerkok:blocklocker:1.9") { isTransitive = false }
    compileOnly("com.bekvon.bukkit:residence:4.6.1.4") { isTransitive = false }
    compileOnly("de.themoep.showitem:api:1.6.3") { isTransitive = false }
    compileOnly("net.tnemc:Reserve:0.1.5.4") { isTransitive = false }
    // Substituted to the local Treasury/Business builds by the root composite
    // (settings.gradle.kts); the version here only governs standalone builds,
    // so it tracks the current dev version which carries the API ChestShop uses
    // (FirmApi.getFirmByAccountId, StaffApi.hasPermissionForAccount, …).
    compileOnly(project(":treasury:treasury-api")) { isTransitive = false }
    compileOnly(project(":business:business-api")) { isTransitive = false }
    compileOnly("me.crafter.mc:lockettepro:2.10-SNAPSHOT") { isTransitive = false }

    // --- bundled libraries (relocated + shaded into ChestShop.jar by :assemble)
    // `api`, not `implementation`: some appear in the core's public method
    // signatures (e.g. adventure Component), so the adapter modules that compile
    // against :plugin need them on their compile classpath — matching Maven's
    // transitive `compile` scope. Shadow bundles them from the runtime classpath.
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
    testRuntimeOnly("org.spigotmc:spigot-api:$spigotApiVersion")
    testImplementation(platform("org.junit:junit-bom:5.13.3"))
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("org.assertj:assertj-core:3.26.3")
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
                    "io/paradaux/chestshop/breeze/Utils/Encoding/Base64.class",
                    "io/paradaux/chestshop/ChestShop.class",
                    "io/paradaux/chestshop/Listeners/**",
                    "io/paradaux/chestshop/Commands/**",
                    "io/paradaux/chestshop/Plugins/**",
                    "io/paradaux/chestshop/Updater/**",
                    "io/paradaux/chestshop/Database/**",
                    "io/paradaux/chestshop/breeze/Database/**",
                )
            }
        })
    )
}

tasks.test { finalizedBy(tasks.jacocoTestReport) }
