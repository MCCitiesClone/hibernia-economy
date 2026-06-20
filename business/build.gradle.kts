import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    jacoco
    id("com.gradleup.shadow")
}

group = "io.paradaux"
version = providers.gradleProperty("version")
    .orElse("2.2.1-SNAPSHOT")
    .get()
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
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")

    // Vault API, exclude Bukkit to avoid capability conflict with Paper
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    // Treasury API (provided at runtime by Treasury plugin)
    compileOnly(project(":treasury:treasury-api"))

    // Hibernia Framework
    implementation("io.paradaux:hibernia-framework:1.0.2")

    // Runtime impls
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.2")
    implementation("org.reflections:reflections:0.10.2")
    implementation("org.mybatis:mybatis:3.5.16")
    implementation("org.mybatis:mybatis-guice:4.0.0")

    // Guice (no_aop classifier to avoid CGLIB/aopalliance)
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

    // Treasury API + Paper API are compileOnly in production; tests need them too.
    testImplementation(project(":treasury:treasury-api"))
    testImplementation("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")

    // Embedded MariaDB for mapper integration tests; same approach Treasury uses.
    testImplementation("ch.vorburger.mariaDB4j:mariaDB4j:3.2.0")

    // Wiring used by mapper tests (production scope is `implementation`)
    testImplementation("com.zaxxer:HikariCP:6.2.1")
    testImplementation("org.mariadb.jdbc:mariadb-java-client:3.5.2")
    testImplementation("org.mybatis:mybatis:3.5.16")

    // SLF4J impl for tests so Lombok @Slf4j calls have a backing logger
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

tasks {
    // Mirror Maven default goal locally
    defaultTasks("clean", "shadowJar")

    // Keep resource filtering tight to avoid $ expansion issues in YAML like config.yml
    processResources {
        filteringCharset = "UTF-8"
        filesMatching(listOf("**/*.properties", "plugin.yml", "paper-plugin.yml", "application*.yml")) {
            // Expands ${...} from project properties only in these files
            expand(project.properties)
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
    toolVersion = "0.8.12"
}

val isCi = project.hasProperty("ci")

val copyPlugin by tasks.registering(Copy::class) {
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