import net.fabricmc.loom.task.RemapJarTask


plugins {
    id ("fabric-loom") version "1.13-SNAPSHOT"
    id ("maven-publish")
	id ("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Preprocess
extensions.extraProperties["targetVersion"] = "mc211"
extensions.extraProperties["inputSourceDir"] = "${rootProject.projectDir}/Multiworld-Common/src/main/java"
val createPreprocessor = rootProject.extra["createPreprocessor"] as groovy.lang.Closure<*>
createPreprocessor.call(project)

base {
    archivesBaseName = "Multiworld-Fabric"
    version = "1.21.1"
    group = "me.isaiah.mods"
}

repositories {
	// Fantasy 1.21
	mavenLocal()
}

configurations.all {
    resolutionStrategy {
        // Check for updates every build
        cacheChangingModulesFor(0, "seconds")
    }
}

dependencies {

	annotationProcessor("com.pkware.jabel:jabel-javac-plugin:1.0.1-1")
	compileOnly("com.pkware.jabel:jabel-javac-plugin:1.0.1-1")

	// 1.21.1
    minecraft("com.mojang:minecraft:1.21.1") 
    mappings("net.fabricmc:yarn:1.21.1+build.3:v2")
    modImplementation("net.fabricmc:fabric-loader:0.18.3")

	include("xyz.nucleoid:fantasy:0.6.3+1.21")
	modImplementation("xyz.nucleoid:fantasy:0.6.3+1.21")
	modImplementation("curse.maven:cyber-permissions-407695:4640544")
	modImplementation("me.lucko:fabric-permissions-api:0.2-SNAPSHOT")
	// modImplementation("net.fabricmc.fabric-api:fabric-api-deprecated:0.100.1+1.21")
	
	 modImplementation("net.fabricmc.fabric-api:fabric-api:0.103.0+1.21.1")
	
	setOf(
		"fabric-api-base",
		"fabric-lifecycle-events-v1",
		"fabric-networking-api-v1",
		"fabric-events-interaction-v0",
		"fabric-command-api-v2"
	).forEach {
		// Add each module as a dependency
		// modImplementation(fabricApi.module(it, "0.100.1+1.21"))
		modImplementation(fabricApi.module(it, "0.103.0+1.21.1"))
	}
	
	// iCommon : on fige le jar vendoré (compilé avec Loom 1.11.8, remap mixin) dans libs/.
	// L'artefact distant `com.javazilla.mods:icommon-fabric-1.21.1:1.21.1` (isChanging) a été
	// republié recompilé avec Loom 1.15.5 (+ Fabric-Loom-Mixin-Remap-Type: static), que notre
	// Architectury Loom 1.13.469 ne sait pas remapper ("Mod was built with a newer version of Loom").
	// Pour suivre l'upstream il faudrait monter Loom à 1.17, ce qui casserait le build multi-versions.
	modImplementation(files("libs/icommon-fabric-1.21.1.jar"))
}

// Note: dimapi is not needed for 1.21
sourceSets {
    main {
        java {
            srcDir("${rootProject.projectDir}/Multiworld-Common/src/main/java/com")
            srcDir("src/main/java")
			exclude("**/dimapi/*.java")
			exclude("**/dimapi/*.class")
			exclude("**/dimapi/mixin/*.java")
			exclude("**/dimapi/mixin/*.class")
        }
        resources {
            srcDir("${rootProject.projectDir}/Multiworld-Common/src/main/resources")
			exclude("**/dimapi/*.java")
			exclude("**/dimapi/*.class")
			exclude("**/dimapi/mixin/*.java")
			exclude("**/dimapi/mixin/*.class")
        }
    }
}

// Jabel
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_21.toString() // for the IDE support
    options.release.set(17)

    javaCompiler.set(
        javaToolchains.compilerFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    )
}

/*configure([tasks.compileJava]) {
    sourceCompatibility = 16 // for the IDE support
    options.release = 8

    javaCompiler = javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(16)
    }
}*/

//tasks.getByName("compileJava") {
    //sourceCompatibility = 16
    //options.release = 8
//}


tasks.withType<Jar> { duplicatesStrategy = DuplicatesStrategy.INHERIT }

val remapJar = tasks.getByName<RemapJarTask>("remapJar")

tasks.named("build") { finalizedBy("copyReport2") }

tasks.register<Copy>("copyReport2") {
    from(remapJar)
    into("${project.rootDir}/output")
}


publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = project.group.toString()
            artifactId = project.name.lowercase()
            version = project.version.toString()
            
            pom {
                name.set(project.name.lowercase())
                description.set("A concise description of my library")
                url.set("http://www.example.com/")
            }

            artifact(remapJar)
        }
    }

    repositories {
        val mavenUsername: String? by project
        val mavenPassword: String? by project
        mavenPassword?.let {
            maven(url = "https://repo.codemc.io/repository/maven-releases/") {
                credentials {
                    username = mavenUsername
                    password = mavenPassword
                }
            }
        }
    }
}