// Paper 1.15.2 adapter — compiled against the 1.15.2 Paper API.
base.archivesName.set("chestshop-paper-1.15.2")

dependencies {
    compileOnly(project(":chestshop:plugin"))
    compileOnly("com.destroystokyo.paper:paper-api:1.15.2-R0.1-SNAPSHOT")
}
