plugins {
    kotlin("jvm")
    id("me.champeau.jmh") version "0.6.6"
}


val originalLibProfile = "originallib"
val patchedLibProfile = "patchedlib"
var profile: String = if (project.hasProperty(patchedLibProfile)) {
    patchedLibProfile
} else {
    originalLibProfile
}

sourceSets.create(profile)

sourceSets["main"].java {
    srcDir("src/${profile}/kotlin")
}


println("current profile: $profile")


group = "org.jetbrains.kotlinx"
version = "1.6.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val coroutinesVersion = "1.6.0"

dependencies {
    jmh("commons-io:commons-io:2.11.0")
    jmh("org.openjdk.jmh:jmh-core:1.35")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.35")

    implementation("commons-io:commons-io:2.11.0")
    implementation("org.openjdk.jmh:jmh-core:1.35")
    implementation("org.openjdk.jmh:jmh-generator-annprocess:1.35")


    when (profile) {
        patchedLibProfile -> {
            implementation(project(":kotlinx-coroutines-core"))
            implementation(project(":kotlinx-coroutines-debug"))
        }

        // original lib
        else -> {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${coroutinesVersion}")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:${coroutinesVersion}")
        }
    }

}

val jarName: String
    get() = "${profile}-benchmark.jar"


tasks.named("jmhJar", type = Jar::class) {
    archiveFileName.set(jarName)

    destinationDirectory.set(File(project.buildDir.absoluteFile, "libs"))
    archiveFileName.set(jarName)
}

task("jmhRun", type = me.champeau.jmh.JMHTask::class) {
    println("jmhRun execution with profile ${profile}")
    jarArchive.set(File(File(project.buildDir.absoluteFile, "libs"), jarName))
    resultsFile.set(File(File(project.buildDir.absoluteFile, "results/jmh"), "${profile}-results.txt"))

    failOnError.set(true)

    dependsOn("jmhJar")
}
