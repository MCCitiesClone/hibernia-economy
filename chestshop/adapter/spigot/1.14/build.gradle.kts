// Spigot 1.14 adapter — compiled against the 1.14 server API.
base.archivesName.set("chestshop-spigot-1.14")

dependencies {
    compileOnly(project(":chestshop:plugin"))
    compileOnly("org.spigotmc:spigot-api:1.14-R0.1-SNAPSHOT")
}
