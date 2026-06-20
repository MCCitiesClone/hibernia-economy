plugins {
    java
    id("com.gradleup.shadow")
}

// Aggregates the core plugin + every server adapter and shades them — together
// with the relocated runtime libraries inherited from :plugin — into the single
// ChestShop.jar that ships. Mirrors the old Maven `assemble` module.
dependencies {
    implementation(project(":chestshop:plugin"))
    implementation(project(":chestshop:adapter-spigot-1_14"))
    implementation(project(":chestshop:adapter-spigot-1_15_2"))
    implementation(project(":chestshop:adapter-spigot-1_17"))
    implementation(project(":chestshop:adapter-spigot-1_20"))
    implementation(project(":chestshop:adapter-spigot-1_20_5"))
    implementation(project(":chestshop:adapter-paper-1_13_2"))
    implementation(project(":chestshop:adapter-paper-1_15_2"))
}

val buildType = extra["buildType"] as String
val buildTimestamp = extra["buildTimestamp"] as String

// Shadow 9 + plain :jar both target build/libs/<name>.jar and :jar wins if left
// enabled — disable it so the fat jar survives (see workspace CLAUDE.md rule 8).
tasks.jar { enabled = false }

tasks.shadowJar {
    // -> build/libs/ChestShop.jar (Maven finalName, no version/classifier).
    archiveBaseName.set("ChestShop")
    archiveClassifier.set("")
    archiveVersion.set("")

    // Whitelist exactly the artifacts the Maven shade bundled. Project artifacts
    // (group io.paradaux = core plugin + adapters) MUST be listed or the filter
    // would drop them. Everything else (spigot-api, vault, worldedit, ItemBridge,
    // treasury-api/business-api, …) stays compileOnly and is never bundled.
    dependencies {
        include(dependency("io.paradaux:.*:.*"))
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
    relocate("net.gravitydevelopment.updater", "io.paradaux.chestshop.Updater")
    relocate("com.j256.ormlite", "io.paradaux.chestshop.Libs.ORMlite")
    relocate("javax.persistence", "io.paradaux.chestshop.Libs.javax.persistence")

    // Concatenate META-INF/services (adventure ships some) rather than last-wins.
    mergeServiceFiles()

    // Top-level docs the Maven assemble copied into the jar root.
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

// Building this module produces the shaded jar.
tasks.assemble { dependsOn(tasks.shadowJar) }
