// =============================================================================
// Hibernia Economy monorepo — single root Gradle build.
//
// One settings file, one wrapper, one build graph. Every JVM project is a
// subproject here; cross-project dependencies are wired with project(...) deps
// (see each build.gradle.kts), so nothing is resolved from a remote snapshot.
//
// Excluded on purpose:
//   * economy-explorer  — Node/npm + Next.js, not a JVM build.
//   * hibernia-framework, realty — git submodules, consumed as published
//     artifacts (io.paradaux:*).
// =============================================================================

rootProject.name = "hibernia-economy"

include(":treasury")
include(":treasury:treasury-api")

include(":business")
include(":business:business-api")

include(":treasury-api-plugin")
include(":treasury-rest-api")
include(":economy-flyway")

// ChestShop: a multi-module subtree (core plugin + version adapters + assemble).
include(":chestshop")
include(":chestshop:plugin")
include(":chestshop:assemble")

// Adapter modules live under non-default nested dirs, so map each explicitly.
fun chestshopAdapter(name: String, dir: String) {
    include(":chestshop:$name")
    project(":chestshop:$name").projectDir = file("chestshop/$dir")
}
chestshopAdapter("adapter-spigot-1_14",   "adapter/spigot/1.14")
chestshopAdapter("adapter-spigot-1_15_2", "adapter/spigot/1.15.2")
chestshopAdapter("adapter-spigot-1_17",   "adapter/spigot/1.17")
chestshopAdapter("adapter-spigot-1_20",   "adapter/spigot/1.20")
chestshopAdapter("adapter-spigot-1_20_5", "adapter/spigot/1.20.5")
chestshopAdapter("adapter-paper-1_13_2",  "adapter/paper/1.13.2")
chestshopAdapter("adapter-paper-1_15_2",  "adapter/paper/1.15.2")
