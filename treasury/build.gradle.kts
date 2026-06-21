import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    jacoco
    id("com.gradleup.shadow")
    id("maven-publish")
}

group = "io.paradaux"
version = providers.gradleProperty("version")
    .orElse("2.2.1-SNAPSHOT")
    .get()
description = "Treasury"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    // mavenLocal first so a locally-published hibernia-framework SNAPSHOT
    // (or treasury-api during cross-project work) is picked up before the
    // remote Reposilite copy. Harmless when nothing's published locally.
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
    // Paper API (provided by server)
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // Vault API, exclude Bukkit to avoid capability conflict with Paper
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // LuckPerms API (optional softdepend)
    compileOnly("net.luckperms:api:5.4")

    // log4j-core: provided by Paper at runtime; needed at compile time
    // to programmatically adjust log levels for our package hierarchy.
    compileOnly("org.apache.logging.log4j:log4j-core:2.20.0")

    // Treasury API submodule
    implementation(project(":treasury:treasury-api"))

    // Hibernia Framework
    implementation("io.paradaux:hibernia-framework:1.0.2")

    // Runtime impls
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.2")
    implementation("org.reflections:reflections:0.10.2")
    implementation("org.mybatis:mybatis:3.5.16")
    implementation("org.mybatis:mybatis-guice:4.0.0")

    // Guice + AOP. mybatis-guice's @Transactional uses AOP interception,
    // so we ship the standard guice jar (which bundles cglib for proxies).
    implementation("com.google.inject:guice:7.0.0")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")

    // ---- Test dependencies ----
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")

    // Embedded MariaDB for integration tests — no Docker required.
    // MariaDB4j unpacks a real MariaDB binary into a temp dir and runs it on a
    // dynamic port. Schema loading goes through the mariadb CLI client, so
    // DELIMITER blocks in schema.sql parse correctly.
    testImplementation("ch.vorburger.mariaDB4j:mariaDB4j:3.2.0")

    // Wiring used by services in tests (impls; production scope is `implementation`)
    testImplementation("com.zaxxer:HikariCP:6.2.1")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:3.5.2")
    testImplementation("org.mybatis:mybatis:3.5.16")
    testImplementation("org.mybatis:mybatis-guice:4.0.0")
    testImplementation("com.google.inject:guice:7.0.0")

    // LuckPerms is a compileOnly soft-dependency in production. MembershipServiceImpl
    // declares an `@Inject(optional = true)` setter that takes a LuckPerms parameter, so
    // Guice needs the class on the classpath at injection time even when no binding exists.
    // Tests also mock LuckPerms to cover the group-aware membership paths.
    testImplementation("net.luckperms:api:5.4")

    // Paper API at test scope so Mockito can mock FileConfiguration / OfflinePlayer /
    // Player when testing classes that read config from a Bukkit plugin.
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    // SLF4J impl for tests so Lombok @Slf4j calls have a backing logger
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")

    // Integration tests build their schema by running the authoritative
    // economy-flyway migrations (staged onto the test classpath below), so the
    // tests and production share one source of schema truth — no bundled
    // schema.sql snapshot to drift. flyway-mysql handles the MySQL/MariaDB URL.
    testImplementation("org.flywaydb:flyway-core:10.22.0")
    testImplementation("org.flywaydb:flyway-mysql:10.22.0")
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
        // Tag-based filtering: gradle test -PskipIT skips DB-backed tests.
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

    jacocoTestCoverageVerification {
        dependsOn(jacocoTestReport)
        // Mirror the report's class-level exclusion so the rule applies to the
        // same in-scope set as the report.
        val excludes = listOf(
            "io/paradaux/treasury/Treasury.class",
            "io/paradaux/treasury/Treasury\$*.class",
            "io/paradaux/treasury/commands/**",
            "io/paradaux/treasury/adapters/VaultEconomyAdapter.class",
            "io/paradaux/treasury/adapters/VaultEconomyRegistrar.class",
            "io/paradaux/treasury/events/**",
            "io/paradaux/treasury/tasks/**",
            "io/paradaux/treasury/guice/**",
            "io/paradaux/treasury/utils/CallingPluginDetector.class",
            "io/paradaux/treasury/utils/LoggingConfigurer.class",
            "io/paradaux/treasury/mappers/**"
        )
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) { exclude(excludes) }
            })
        )
        violationRules {
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

    jacocoTestReport {
        dependsOn(test, classes)
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        // Bukkit / I/O glue that can't be exercised without a running server.
        // Coverage targets apply to the in-scope set (services, api, utils, configs).
        val excludes = listOf(
            "io/paradaux/treasury/Treasury.class",
            "io/paradaux/treasury/Treasury\$*.class",
            "io/paradaux/treasury/commands/**",
            "io/paradaux/treasury/adapters/VaultEconomyAdapter.class",
            "io/paradaux/treasury/adapters/VaultEconomyRegistrar.class",
            "io/paradaux/treasury/events/**",
            "io/paradaux/treasury/tasks/**",
            "io/paradaux/treasury/guice/**",
            "io/paradaux/treasury/utils/CallingPluginDetector.class",
            "io/paradaux/treasury/utils/LoggingConfigurer.class",
            "io/paradaux/treasury/mappers/**"
        )
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) { exclude(excludes) }
            })
        )
    }

    // Shadow 9 writes shadowJar to build/libs/<name>.jar by default, and
    // so does :jar. With both enabled :jar runs *after* :shadowJar and
    // overwrites the fat jar with the thin one — produced a 158 KB
    // dependency-less plugin that disabled itself on enable. Disable :jar
    // so the shaded artifact stays put.
    jar {
        enabled = false
    }

    // Produce a single shaded jar without the "-all" classifier
    withType<ShadowJar> {
        archiveClassifier.set("")
        // relocate shaded libs
        relocate("com.google.inject", "io.paradaux.libs.guice")
        relocate("javax.inject", "io.paradaux.libs.javax")
        relocate("org.aopalliance", "io.paradaux.libs.aopalliance")
        relocate("io.jsonwebtoken", "io.paradaux.libs.jjwt")
        relocate("com.fasterxml.jackson", "io.paradaux.libs.jackson")
        // some libs use META-INF/services (safe and cheap)
        mergeServiceFiles()
    }
}

jacoco {
    toolVersion = "0.8.12"
}

val isCi = project.hasProperty("ci")

val copyPlugin = tasks.register<Copy>("copyPlugin") {
    // :jar is disabled (see comment above) so we only depend on shadowJar.
    dependsOn(tasks.named("shadowJar"))
    from(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
    into(layout.projectDirectory.dir("../../server/plugins"))
    onlyIf { !isCi } // don’t run on CI
}

tasks.named<ShadowJar>("shadowJar") {
    finalizedBy(copyPlugin)
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
