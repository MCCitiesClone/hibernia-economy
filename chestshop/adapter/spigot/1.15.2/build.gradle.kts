// Spigot 1.15.2 adapter — compiled against the 1.15.2 server API.
base.archivesName.set("chestshop-spigot-1.15.2")

dependencies {
    compileOnly(project(":chestshop:plugin"))
    compileOnly("org.spigotmc:spigot-api:1.15.2-R0.1-SNAPSHOT")
}
