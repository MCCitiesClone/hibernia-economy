// flyway-mysql + the JDBC driver are added to the *plugin* classpath so the
// Flyway plugin's DatabaseType service-loader can find them. The
// `flyway.configurations` mechanism (a separate task-time configuration)
// resolves dependencies fine but doesn't expose them to the plugin's own
// classloader on this version, which is why MariaDB/MySQL-handling fails
// with "No database found to handle jdbc:mysql://...".
buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("org.flywaydb:flyway-mysql:10.22.0")
        classpath("com.mysql:mysql-connector-j:9.4.0")
    }
}

plugins {
    java
    id("org.flywaydb.flyway")
}

group = "io.paradaux"
version = providers.gradleProperty("version").orElse("1.0.0-SNAPSHOT").get()
description = "Flyway-managed schema for the shared DemocracyCraft economy database"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

// (No `flywayMigration` configuration — see the buildscript block above for
// why driver and vendor module live on the plugin classpath instead.)

// =====================================================================
// Configuration sources, in priority order:
//   1. Gradle -P properties     (e.g. -PflywayUrl=...)
//   2. Environment variables    (FLYWAY_URL, FLYWAY_USER, FLYWAY_PASSWORD)
//   3. Defaults: localhost/economy with empty credentials
//
// Treat the env-var path as the production path: CI / Docker / k8s should
// inject FLYWAY_URL / FLYWAY_USER / FLYWAY_PASSWORD. The Gradle -P path is
// for ad-hoc local invocation.
// =====================================================================

fun resolve(envName: String, propName: String, default: String): String =
    (project.findProperty(propName) as String?)
        ?: System.getenv(envName)
        ?: default

flyway {
    url = resolve("FLYWAY_URL", "flywayUrl", "jdbc:mysql://localhost:3306/economy")
    user = resolve("FLYWAY_USER", "flywayUser", "root")
    password = resolve("FLYWAY_PASSWORD", "flywayPassword", "")
    schemas = arrayOf(resolve("FLYWAY_SCHEMA", "flywaySchema", "economy"))
    locations = arrayOf("filesystem:src/main/resources/db/migration")

    // We expect a green-field database. Pass -Pbaseline=true on the first run
    // against a pre-existing DB so Flyway records a baseline at version 0
    // instead of trying to apply V1 against a populated schema.
    baselineOnMigrate = (project.findProperty("baseline") as String?) == "true"
    baselineVersion = "0"

    // Validate that on-disk migration files match what's in flyway_schema_history.
    // Production deploys should always have this on; loosen only for ad-hoc dev.
    validateOnMigrate = true

    // Default name; explicit so it's discoverable from the build file.
    table = "flyway_schema_history"

    // Flyway 10 defaults cleanDisabled=true to prevent accidental wipes in
    // production. Allow opt-in via -PflywayCleanEnabled=true so dev hosts can
    // reset (e.g. when V1 evolves before any prod deploy). Production CI must
    // not set this property.
    cleanDisabled = (project.findProperty("flywayCleanEnabled") as String?) != "true"
}
