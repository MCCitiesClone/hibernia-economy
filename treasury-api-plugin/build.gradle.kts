import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow")
}

group = "io.paradaux"
version = providers.gradleProperty("version")
    .orElse("2.1.0-SNAPSHOT")
    .get()
description = "TreasuryAPI"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven("https://oss.sonatype.org/content/groups/public/")
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
    // Paper API (provided by server)
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")

    // Treasury API (provided at runtime by Treasury plugin)
    compileOnly(project(":treasury:treasury-api"))

    // Business API (provided at runtime by Business plugin)
    compileOnly(project(":business:business-api"))

    // LuckPerms API (optional softdepend — used by the group reconciliation cron)
    compileOnly("net.luckperms:api:5.4")

    // Hibernia Framework
    implementation("io.paradaux:hibernia-framework:1.0.2")

    // Runtime impls
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.2")
    implementation("org.reflections:reflections:0.10.2")
    implementation("org.mybatis:mybatis:3.5.16")
    implementation("org.mybatis:mybatis-guice:4.0.0")

    // Guice
    implementation("com.google.inject:guice:7.0.0")

    // JJWT (for JWT API key signing)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")

    // Tests
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    // Mirror Maven default goal locally
    defaultTasks("clean", "shadowJar")

    test {
        useJUnitPlatform()
    }

    // Keep resource filtering tight to avoid $ expansion issues in YAML like config.yml
    processResources {
        filteringCharset = "UTF-8"
        // Capture at configuration time so the filesMatching action never touches
        // `project` at execution time (config-cache safe; Gradle 10 forward-compat).
        val expansions = mapOf("version" to project.version, "name" to project.name,
                "description" to (project.description ?: ""))
        filesMatching(listOf("**/*.properties", "plugin.yml", "paper-plugin.yml", "application*.yml")) {
            expand(expansions)
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    // Produce a single shaded jar without the "-all" classifier
    withType<ShadowJar> {
        val root = "io.paradaux.treasuryapi.libs"

        relocate("com.google.inject", "$root.guice")
        relocate("org.aopalliance",   "$root.org.aopalliance")
        relocate("org.mybatis",       "$root.mybatis")
        relocate("com.zaxxer.hikari", "$root.hikari")
        relocate("org.mariadb",       "$root.mariadb")
        relocate("org.reflections",   "$root.reflections")
        relocate("io.jsonwebtoken",   "$root.jjwt")
        relocate("com.fasterxml.jackson", "$root.jackson")

        mergeServiceFiles()
        archiveClassifier.set("")
    }
}

val isCi = project.hasProperty("ci")

val copyPlugin = tasks.register<Copy>("copyPlugin") {
    // Both :jar and :shadowJar write to build/libs/<name>.jar by default;
    // Gradle 8.11 strict-mode requires declaring deps on every task whose
    // output we read.
    dependsOn(tasks.named("shadowJar"), tasks.named("jar"))
    from(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("../../server/plugins"))
    onlyIf { !isCi } // don't run on CI
}

tasks.named<ShadowJar>("shadowJar") {
    finalizedBy(copyPlugin)
}

// Shadow 9 + Gradle 8: both :jar and :shadowJar write to build/libs/<name>.jar,
// and :jar runs AFTER :shadowJar — silently overwriting the fat jar with a
// thin one that disables-on-enable for missing classpath. Disable :jar.
tasks.jar { enabled = false }
