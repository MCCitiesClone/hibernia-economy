plugins {
    `java-library`
    `maven-publish`
    jacoco
}

group = "io.paradaux"
version = rootProject.version
description = "Business API"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
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
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly("io.paradaux:hibernia-framework:0.1.2")

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

jacoco {
    toolVersion = "0.8.12"
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "business-api"
            version = project.version.toString()

            pom {
                name.set("business-api")
                description.set("Public API for the DemocracyCraft Business plugin.")
                url.set("https://repo.paradaux.io")
                licenses {
                    license {
                        name.set("AGPL-3.0-or-later")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.en.html")
                        distribution.set("repo")
                    }
                }
                developers { developer { id.set("rian"); name.set("Rían Errity") } }
                scm {
                    url.set("https://github.com/DemocracyCraft/Business")
                    connection.set("scm:git:https://github.com/DemocracyCraft/Business.git")
                    developerConnection.set("scm:git:ssh://git@github.com/DemocracyCraft/Business.git")
                }
            }
        }
    }
}
