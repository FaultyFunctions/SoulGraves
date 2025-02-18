plugins {
    kotlin("jvm") version "2.0.20" // Kotlin
    id("com.gradleup.shadow") version "8.3.3" // Shadow
}


group = "dev.faultyfunctions"
version = "1.3.1"
kotlin.jvmToolchain(21)


repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") // Spigot
    maven("https://oss.sonatype.org/content/groups/public/") // Kotlin STD

    maven("https://jitpack.io") // RTag
}

dependencies {
    // Base
    compileOnly("org.spigotmc:spigot-api:1.21.3-R0.1-SNAPSHOT") // Spigot
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8") // Kotlin STD

    // Database
    compileOnly("com.zaxxer:HikariCP:5.1.0") // Hikari CP
    compileOnly("io.lettuce:lettuce-core:6.5.3.RELEASE") // Lettuce

    // Develop Lib
    implementation("dev.dejvokep:boosted-yaml:1.3.6") // BoostedYaml

    // Bukkit Lib
    implementation("com.saicone.rtag:rtag:1.5.8") // RTag
    implementation("com.saicone.rtag:rtag-item:1.5.8") // RTag
    implementation("org.bstats:bstats-bukkit:3.0.2") // Bstats
    implementation("net.kyori:adventure-platform-bukkit:4.3.4") // Kyori
    implementation("net.kyori:adventure-text-minimessage:4.17.0") // Kyori
    implementation("com.jeff-media:MorePersistentDataTypes:2.4.0") // MorePersistentDataTypes
}


tasks {
    build {
        dependsOn(shadowJar)
    }
    shadowJar {
        relocate("com.saicone.rtag", "${project.group}.libs.rtag")
        relocate("org.bstats", "${project.group}.libs.bstats")
        relocate("dev.dejvokep.boostedyaml", "${project.group}.libs.boostedyaml")
        relocate("com.jeff_media.morepersistentdatatypes", "${project.group}.libs.morepersistentdatatypes")

//        destinationDirectory.set(file("C:/Users/Catnies/Desktop/FirTown Test Server/plugins"))
//        archiveFileName = "SoulGrave-${version}-all.jar.jar"
    }
}


tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}