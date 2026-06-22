plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// group + version are set centrally by the root allprojects block (single
// mono-repo version, 2.3.0-SNAPSHOT, overridable with -Pversion).
description = "treasury-rest-api"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    // Caching for the public ChestShop market endpoints (Caffeine, in-process).
    // Collapses identical heavy-aggregate reads to one query per short TTL.
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.1")

    // Swagger / OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")

    // Rate limiting — Bucket4j core + Caffeine (in-memory) and Lettuce
    // (Redis). Caffeine is the default; Redis kicks in when the
    // rate-limit.redis-host property is set (see RateLimitConfig).
    implementation("com.bucket4j:bucket4j_jdk17-core:8.14.0")
    implementation("com.bucket4j:bucket4j_jdk17-caffeine:8.14.0")
    implementation("com.bucket4j:bucket4j_jdk17-redis-common:8.14.0")
    implementation("com.bucket4j:bucket4j_jdk17-lettuce:8.14.0")
    implementation("io.lettuce:lettuce-core:6.4.0.RELEASE")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // JWT
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.mybatis.spring.boot:mybatis-spring-boot-starter-test:4.0.1")
    // Embedded MariaDB for market read-side integration tests (real MariaDB SQL:
    // ON DUPLICATE KEY, DATE_FORMAT, INTERVAL — H2 can't stand in).
    testImplementation(libs.mariadb4j)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
