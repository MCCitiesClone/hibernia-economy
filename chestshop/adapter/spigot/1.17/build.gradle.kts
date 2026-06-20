// Spigot 1.17 adapter — compiled against the 1.17 server API.
base.archivesName.set("chestshop-spigot-1.17")

dependencies {
    compileOnly(project(":chestshop:plugin"))
    compileOnly("org.spigotmc:spigot-api:1.17-R0.1-SNAPSHOT")
}
