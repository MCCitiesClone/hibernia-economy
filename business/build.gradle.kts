import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    jacoco
    id("com.gradleup.shadow")
}

// group + version are set centrally by the root allprojects block (single
// mono-repo version, 2.2.1-SNAPSHOT, overridable with -Pversion).
description = "Business"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenLocal()
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
    // Business API subproject (bundled into shadow JAR)
    implementation(project(":business:business-api"))

    // Paper API (provided by server)
    compileOnly(libs.paper.api)

    // Vault API, exclude Bukkit to avoid capability conflict with Paper
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // Treasury API (provided at runtime by Treasury plugin)
    compileOnly(project(":treasury:treasury-api"))

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

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // ---- Test dependencies ----
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)

    // Treasury API + Paper API are compileOnly in production; tests need them too.
    testImplementation(project(":treasury:treasury-api"))
    testImplementation(libs.paper.api)

    // Embedded MariaDB for mapper integration tests; same approach Treasury uses.
    testImplementation(libs.mariadb4j)

    // Wiring used by mapper tests (production scope is `implementation`)
    testImplementation(libs.hikaricp)
    testImplementation(libs.mariadb.java.client)
    testImplementation(libs.mybatis.core)

    // Integration tests build their schema by running the authoritative
    // economy-flyway migrations (staged onto the test classpath below), so the
    // tests and production share one source of schema truth — no bundled
    // schema.sql snapshot to drift (PAR-242, mirrors Treasury's PAR-239).
    // flyway-mysql handles the MySQL/MariaDB URL.
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.mysql)

    // SLF4J impl for tests so Lombok @Slf4j calls have a backing logger
    testRuntimeOnly(libs.slf4j.simple)
}

// Stage the economy-flyway migrations onto the test classpath (under db/migration)
// so the IT harness can run them with Flyway (classpath:db/migration).
tasks.named<Copy>("processTestResources") {
    from(project(":economy-flyway").file("src/main/resources/db/migration")) {
        into("db/migration")
    }
}

tasks {
    // Mirror Maven default goal locally
    defaultTasks("clean", "shadowJar")

    // Keep resource filtering tight to avoid $ expansion issues in YAML like config.yml
    processResources {
        filteringCharset = "UTF-8"
        // Capture at configuration time so the filesMatching action never touches
        // `project` at execution time (config-cache safe; Gradle 10 forward-compat).
        val expansions = mapOf("version" to project.version, "name" to project.name,
                "description" to (project.description ?: ""))
        filesMatching(listOf("**/*.properties", "plugin.yml", "paper-plugin.yml", "application*.yml")) {
            // Expands ${...} from these project properties only in these files
            expand(expansions)
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    test {
        useJUnitPlatform()
        // Tag-based filtering: gradle test -PskipIT skips the DB-backed integration suite.
        if (project.hasProperty("skipIT")) {
            useJUnitPlatform { excludeTags("integration") }
        }
        testLogging {
            events("failed", "skipped")
            showStandardStreams = false
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        finalizedBy(jacocoTestReport)
    }

    check {
        dependsOn(jacocoTestCoverageVerification)
    }

    // Bukkit / I/O glue that can't be exercised without a running server.
    // Coverage targets apply to the in-scope set (services, api, utils,
    // mappers, model/config). Mirrored by both the report and the
    // verification rule so the gate evaluates the same set as the report.
    val coverageExcludes = listOf(
        "io/paradaux/business/Business.class",
        "io/paradaux/business/Business\$*.class",
        "io/paradaux/business/commands/**",
        "io/paradaux/business/listeners/**",
        "io/paradaux/business/jobs/**",
        "io/paradaux/business/guice/**",
        "io/paradaux/business/utils/resolvers/**"
    )

    jacocoTestReport {
        dependsOn(test, classes)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) { exclude(coverageExcludes) }
            })
        )
    }

    jacocoTestCoverageVerification {
        dependsOn(jacocoTestReport)
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) { exclude(coverageExcludes) }
            })
        )
        violationRules {
            // Mirrors Treasury's gate. Adjust upward over time.
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.95".toBigDecimal()
                }
            }
        }
    }

    // Produce a single shaded jar without the "-all" classifier
    withType<ShadowJar> {
        val root = "io.paradaux.business.libs"

        relocate("com.google.inject", "$root.guice")
        relocate("org.aopalliance",   "$root.org.aopalliance")
        relocate("org.mybatis",       "$root.mybatis")
        relocate("com.zaxxer.hikari", "$root.hikari")
        relocate("org.mariadb",       "$root.mariadb")
        relocate("org.reflections",   "$root.reflections")

        mergeServiceFiles()
        archiveClassifier.set("")
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val isCi = project.hasProperty("ci")

val copyPlugin = tasks.register<Copy>("copyPlugin") {
    // Both :jar and :shadowJar write to build/libs/<name>.jar by default;
    // Gradle 8.11 strict-mode requires declaring deps on every task whose
    // output we read.
    dependsOn(tasks.named("shadowJar"), tasks.named("jar"))
    from(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("../../server/plugins"))
    onlyIf { !isCi } // don’t run on CI
}

tasks.named<ShadowJar>("shadowJar") {
    finalizedBy(copyPlugin)
}

// Shadow 9 writes both :jar and :shadowJar to build/libs/<name>.jar; :jar runs
// after :shadowJar and overwrites the fat jar with a ~120 KB thin one that
// disables itself on enable for missing classes. Disable :jar so the shaded
// artifact stays put.
tasks.jar {
    enabled = false
}

subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")

                    name = if (isSnapshot) "Snapshots" else "Releases"
                    url = uri(
                        if (isSnapshot)
                            "https://repo.paradaux.io/snapshots"
                        else
                            "https://repo.paradaux.io/releases"
                    )

                    credentials {
                        username = System.getenv("REPO_USER")
                        password = System.getenv("REPO_PASS")
                    }
                }
            }
        }
    }
}