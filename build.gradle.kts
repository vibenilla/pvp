plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.36.0"
}

description = "Combat for Minestom"
group = "rocks.minestom"
version = "0.1.0"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

mavenPublishing {
    coordinates(group.toString(), project.name, version.toString())
    publishToMavenCentral()
    signAllPublications()

    pom {
        name = project.name
        description = project.description
        url = "https://github.com/vibenilla/pvp"

        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                name = "mudkip"
                id = "mudkipdev"
                email = "mudkip@mudkip.dev"
                url = "https://mudkip.dev"
            }
        }

        scm {
            url = "https://github.com/vibenilla/pvp"
            connection = "scm:git:git://github.com/vibenilla/pvp.git"
            developerConnection = "scm:git:ssh://git@github.com/vibenilla/pvp.git"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.minestom:minestom:2025.12.20c-1.21.11")
    testImplementation("net.minestom:minestom:2025.12.20c-1.21.11")
    compileOnly("it.unimi.dsi:fastutil:8.5.12")
}