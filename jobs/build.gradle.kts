import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    jacoco
    id("com.gradleup.shadow")
    id("io.paradaux.paper-server-conventions")
}

// group + version are set centrally by the root allprojects block (single
// mono-repo version, overridable with -Pversion).
// The JVM toolchain, repositories, resource expansion, base test setup, shaded-jar
// defaults, and dev-server staging come from io.paradaux.paper-server-conventions.
description = "Jobs"

dependencies {
    // Jobs API subproject (bundled into the shadow JAR). Other plugins consume it
    // compileOnly + a `depend: [ Jobs ]`, exactly as Business consumes treasury-api.
    implementation(project(":jobs:jobs-api"))

    // Framework-free shared utilities: DataSourceProvider, so every writer to the
    // shared economy database builds its pool the same way.
    implementation(project(":common"))

    // Paper-facing shared utilities: TagAwareMessage, which resolves {placeholder}s
    // inside MiniMessage tag arguments. Required, not incidental — the /jobs list
    // entries put {job} inside a <click:run_command:'...'> argument.
    implementation(project(":common-paper"))

    // Paper API (provided by server)
    compileOnly(libs.paper.api)

    // LuckPerms is the source of truth for job membership, but is a SOFT dependency:
    // the plugin boots without it and degrades every write to a clear error. Only
    // io.paradaux.jobs.permissions.LuckPermsBackend may reference these types.
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

    // Shared startup + message-key test-kit (brings JUnit, Guice, the framework and
    // MockBukkit transitively so the startup test can drive the real injector).
    testImplementation(project(":test-support"))

    // Paper + LuckPerms are compileOnly in production; tests need them on the
    // classpath — the startup test builds the real injector, and LuckPermsBackend
    // is unit-tested against a mocked LuckPerms.
    testImplementation(libs.paper.api)
    testImplementation(libs.luckperms.api)

    // Embedded MariaDB for mapper integration tests.
    testImplementation(libs.mariadb4j)

    // Wiring used by mapper tests (production scope is `implementation`)
    testImplementation(libs.hikaricp)
    testImplementation(libs.mariadb.java.client)
    testImplementation(libs.mybatis.core)

    // Integration tests build their schema by running the authoritative
    // economy-flyway migrations (staged onto the test classpath below), so tests and
    // production share one source of schema truth — no schema.sql snapshot to drift.
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
    test {
        finalizedBy(jacocoTestReport)
    }

    check {
        dependsOn(jacocoTestCoverageVerification)
    }

    // Bukkit / LuckPerms / I/O glue that can't be exercised without a running
    // server. Coverage targets apply to the in-scope set (services, api, model,
    // mappers, permissions/PermissionBackend contract).
    val coverageExcludes = listOf(
        "io/paradaux/jobs/Jobs.class",
        "io/paradaux/jobs/Jobs\$*.class",
        "io/paradaux/jobs/commands/**",
        "io/paradaux/jobs/guice/**",
        "io/paradaux/jobs/tasks/**",
        // Pure LuckPerms glue: cannot run without a live LuckPerms instance. Its
        // node builders are static factories that resolve through
        // LuckPermsProvider.get(), so they throw outside a running server and
        // mocking the LuckPerms instance does not help.
        "io/paradaux/jobs/permissions/LuckPermsBackend.class",
        "io/paradaux/jobs/permissions/LuckPermsBackend\$*.class",
        // Bukkit scheduler/event-bus glue — a main-thread hop and a callEvent, on
        // the same grounds as commands/** and Business's listeners/**.
        "io/paradaux/jobs/services/JobEventPublisher.class"
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
            // Treasury/Business sit at 0.95 with mature exclude lists. Actual line
            // coverage here is ~94%; the gate sits just under it so an incidental
            // uncovered branch does not fail the build, while a real regression does.
            rule {
                element = "BUNDLE"
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.90".toBigDecimal()
                }
            }
        }
    }

    // Project-specific shaded-lib relocations. archiveClassifier + mergeServiceFiles
    // come from io.paradaux.paper-server-conventions.
    withType<ShadowJar> {
        val root = "io.paradaux.jobs.libs"

        relocate("com.google.inject", "$root.guice")
        relocate("org.aopalliance",   "$root.org.aopalliance")
        relocate("org.mybatis",       "$root.mybatis")
        relocate("com.zaxxer.hikari", "$root.hikari")
        relocate("org.mariadb",       "$root.mariadb")
        relocate("org.reflections",   "$root.reflections")
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}
