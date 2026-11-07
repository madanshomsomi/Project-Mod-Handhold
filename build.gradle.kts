import com.example.ExpandModVersionAction
import com.example.PrepareModVersionTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.GradleException
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.Delete
import java.util.Properties

plugins {
	id("net.fabricmc.fabric-loom-remap")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.4.21"
}

repositories {
	// Add repositories to retrieve artifacts from in here.
	// You should only use this when depending on other mods because
	// Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
	// See https://docs.gradle.org/current/userguide/declaring_repositories.html
	// for more information about repositories.
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("handhold") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets.getByName("client"))
		}
	}
}

fabricApi {
	configureDataGeneration {
		client = true
		modId = "handhold"
	}
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
    mappings(loom.officialMojangMappings())
	modImplementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	modImplementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
}

val modSourceFiles = fileTree("src")
val modVersionStateFile = layout.projectDirectory.file(".gradle/handhold-version.properties")
val prepareModVersion = tasks.register<PrepareModVersionTask>("prepareModVersion") {
	sourceFiles.from(modSourceFiles)
	sourceRoot.set(layout.projectDirectory)
	baseVersion.set(providers.gradleProperty("version"))
	outputFile.set(modVersionStateFile)
}

val modVersion = providers.fileContents(prepareModVersion.flatMap { it.outputFile }).asText.map { contents ->
	Properties().apply { load(contents.reader()) }.getProperty("version")
		?: throw GradleException("Generated Handhold version is missing")
}

tasks.processResources {
	dependsOn(prepareModVersion)
	inputs.property("version", modVersion)
	filesMatching("fabric.mod.json", ExpandModVersionAction(modVersion))
}

tasks.withType<AbstractArchiveTask>().configureEach {
	dependsOn(prepareModVersion)
	archiveVersion.set(modVersion)
}

tasks.named<Delete>("clean") {
	setDelete(fileTree(layout.buildDirectory) {
		exclude("libs/*.jar")
	})
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 21
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_21
	}
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}
