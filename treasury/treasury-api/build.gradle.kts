plugins {
    `java-library`
    `maven-publish`
}

group = "io.paradaux"
version = rootProject.version

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    compileOnly("org.jetbrains:annotations:24.0.0")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = "treasury-api"
            version = project.version.toString()

            pom {
                name.set("treasury-api")
                description.set("Public API for the DemocracyCraft Treasury economy plugin.")
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
                    url.set("https://github.com/DemocracyCraft/Treasury")
                    connection.set("scm:git:https://github.com/DemocracyCraft/Treasury.git")
                    developerConnection.set("scm:git:ssh://git@github.com/DemocracyCraft/Treasury.git")
                }
            }
        }
    }
}
