// Spigot 1.20 adapter — compiled against the 1.20 server API.
base.archivesName.set("chestshop-spigot-1.20")

dependencies {
    compileOnly(project(":chestshop:plugin"))
    compileOnly("org.spigotmc:spigot-api:1.20-R0.1-SNAPSHOT")
}
