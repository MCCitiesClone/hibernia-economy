import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.gradleup.shadow")
    id("io.paradaux.paper-server-conventions")
}

// group + version are set centrally by the root allprojects block (single
// mono-repo version, 2.3.0-SNAPSHOT, overridable with -Pversion).
// The JVM toolchain, repositories, resource expansion, base test setup, shaded-jar
// defaults, and dev-server staging come from io.paradaux.paper-server-conventions.
description = "TreasuryAPI"

dependencies {
    implementation(project(":common"))

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
    // Project-specific shaded-lib relocations. archiveClassifier + mergeServiceFiles
    // come from io.paradaux.paper-server-conventions.
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
    }
}
