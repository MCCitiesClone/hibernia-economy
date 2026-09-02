// :common-paper — Paper/framework-facing utilities shared by the server plugins
// (treasury, business, treasury-api-plugin). Sibling of :common, which is a strictly
// framework-free pure-JVM library that the Spring :treasury-rest-api also consumes —
// anything touching Bukkit, Adventure or hibernia-framework belongs here instead.
//
// Paper and the framework are compileOnly: every consumer already puts them on the
// runtime classpath (Paper from the server, the framework shaded into each plugin
// jar), so this module must not bundle a second copy.
//
// This does NOT apply paper-server-conventions — that carries shadowJar, plugin.yml
// expansion and dev-server staging, none of which a plain library wants. The
// repository list is the same one, kept in sync by hand for hibernia-framework.

plugins {
    `java-library`
    id("io.paradaux.jvm-conventions")
}

group = "io.paradaux"
version = rootProject.version

repositories {
    // Opt-in mavenLocal, matching paper-server-conventions: normal and CI builds
    // resolve hibernia-framework only from the declared remotes (PAR-267).
    if (providers.gradleProperty("useMavenLocal").isPresent) {
        mavenLocal()
    }
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven("https://jitpack.io")
    maven {
        name = "ParadauxReleases"
        url = uri("https://repo.paradaux.io/releases")
        mavenContent { releasesOnly() }
    }
    maven {
        name = "ParadauxSnapshots"
        url = uri("https://repo.paradaux.io/snapshots")
        mavenContent { snapshotsOnly() }
    }
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.hibernia.framework)

    testImplementation(libs.paper.api)
    testImplementation(libs.hibernia.framework)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
}
