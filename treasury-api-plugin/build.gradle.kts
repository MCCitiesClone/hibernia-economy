import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow")
}

// group + version are set centrally by the root allprojects block (single
// mono-repo version, 2.3.0-SNAPSHOT, overridable with -Pversion).
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
    compileOnly(libs.paper.api)

    // Treasury API (provided at runtime by Treasury plugin)
    compileOnly(project(":treasury:treasury-api"))

    // Business API (provided at runtime by Business plugin)
    compileOnly(project(":business:business-api"))

    // LuckPerms API (optional softdepend — used by the group reconciliation cron)
    compileOnly(libs.luckperms.api)

    // Hibernia Framework
    implementation(libs.hibernia.framework)

    // Runtime impls
    implementation(libs.hikaricp)
    implementation(libs.mariadb.java.client)
    implementation(libs.reflections)
    implementation(libs.mybatis.core)
    implementation(libs.mybatis.guice)

    // Guice
    implementation(libs.guice)

    // JJWT (for JWT API key signing)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Tests
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // GroupReconciliationTask extends BukkitRunnable and references the LuckPerms
    // API, so the test classpath needs both to load the class (even for pure tests).
    testImplementation(libs.paper.api)
    testImplementation(libs.luckperms.api)
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

// In-repo dev server staging dir (server/plugins at the repo root). A top-level
// subproject is one level under the root, so it's "../server/plugins" — the old
// "../../server/plugins" climbed one level too far and wrote outside the repo. (PAR-268)
val pluginsDir = layout.projectDirectory.dir("../server/plugins")

val copyPlugin = tasks.register<Copy>("copyPlugin") {
    // Both :jar and :shadowJar write to build/libs/<name>.jar by default;
    // Gradle 8.11 strict-mode requires declaring deps on every task whose
    // output we read.
    val shadowJar = tasks.named<ShadowJar>("shadowJar")
    dependsOn(shadowJar, tasks.named("jar"))
    from(shadowJar.flatMap { it.archiveFile })
    into(pluginsDir)
    onlyIf { !isCi } // don't run on CI
    doFirst {
        // Remove this plugin's previously-staged jars (any version) so stale
        // copies don't pile up — the dev server would otherwise load two
        // versions of the same plugin. Scoped to THIS artifact's base name + a
        // version digit, so it never matches a sibling, a data folder, or
        // another server jar. (PAR-268)
        val re = Regex("^${Regex.escape(shadowJar.get().archiveBaseName.get())}-\\d.*\\.jar$")
        pluginsDir.asFile.listFiles { f -> f.isFile && re.matches(f.name) }?.forEach { it.delete() }
    }
}

tasks.named<ShadowJar>("shadowJar") {
    finalizedBy(copyPlugin)
}

// Shadow 9 + Gradle 8: both :jar and :shadowJar write to build/libs/<name>.jar,
// and :jar runs AFTER :shadowJar — silently overwriting the fat jar with a
// thin one that disables-on-enable for missing classpath. Disable :jar.
tasks.jar { enabled = false }
