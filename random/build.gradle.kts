// This line is a result of IDE recommendation, without quite understanding
// WHY it needs to import the Download class in addition to defining.
// If info about this is online (almost certainly) it is obscured in a way I couldn't find it.
// -- voliol 2026-03-04
import de.undercouch.gradle.tasks.download.Download
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

plugins {
    id("io.github.file5.guidesigner") version "1.0.2"
    id("de.undercouch.download") version "5.7.0"
}

dependencies {
    // Internal dependencies
    implementation(project(":romio"))
    implementation(project(":utils"))
    testImplementation(testFixtures(project(":romio")))

    // Needed to compile the IntelliJ GUI Designer forms
    implementation("com.jetbrains.intellij.java:java-gui-forms-rt:+")
    implementation("com.jgoodies:forms:1.1-preview")

    // Other dependencies
    implementation("com.formdev:flatlaf:3.7")
}

tasks.jar {
    // Renamed from FVX's UPR-FVX.jar (11/10/2026): this is our own build, not an FVX build.
    archiveFileName.set("PokemonRandomizerYAO.jar")
    manifest {
        attributes("Main-Class" to "com.uprfvx.random.gui.RandomizerGUI")
    }

    // Makes the jar fat (include all runtime dependencies)
    dependsOn(configurations.runtimeClasspath)
    from(
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    ) {
        exclude("META-INF/LICENSE")
    }
}

tasks.register<Delete>("clearReleaseDir") {
    group = "release setup"
    delete("build/target")
}

// This is in a way ugly, compared to the ideomatic way of having
// Version be a Gradle config var that nestles its way into the Java.
// However, the Version.java class wasn't going away wholesale either way,
// and it is imho preferable to only have Versions be defined in one place
// - especially for devs who don't touch the Gradle.
// -- voliol 2026-03-14
tasks.register<JavaExec>("getVersionName") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.uprfvx.random.Version"

    val versionNameStream = ByteArrayOutputStream()
    standardOutput = versionNameStream
    doLast {
        rootProject.extra["randomizerVersion"] = "v${versionNameStream.toString().trim()}"
    }
}

// We get the launcher here, so we can use the same JDK for running jlink and jdeps,
// as is used for compiling the program (the "toolchain" JDK).
val launcher = javaToolchains.launcherFor {
    languageVersion.set(java.toolchain.languageVersion)
}
val javaHome = launcher.map { it.metadata.installationPath }

val javaVersion = "25.0.2"
val jdkVersion = "12"

// All of these are 64-bit
enum class PlatformConfig(
    val apiOS: String, val apiArchitecture: String,
    val launcherExtension: String,
    val jmodsInZip: Boolean = false
) {
    Windows("windows", "amd64", "bat", jmodsInZip = true),
    Linux_x86("linux", "amd64", "sh"),
    Linux_ARM("linux", "aarch64", "sh"),
    Mac_x86("macos", "amd64", "command"),
    Mac_ARM("macos", "aarch64", "command")
}
fun taskName(cfg: PlatformConfig): String {
    return cfg.name.replace("_", "")
}

val zipTasks = mutableListOf<TaskProvider<Zip>>()
PlatformConfig.entries.forEach { cfg ->
    var extension = "tar.gz"
    var decompresser: (File) -> FileTree = { tarTree(it) }
    if (cfg.jmodsInZip) {
        extension = "zip"
        decompresser = { zipTree(it) }
    }

    val download = tasks.register<Download>("downloadJmods${taskName(cfg)}") {
        group = "release setup"
        // We use BellSoft Liberica because they include jmods in their normal JDK releases.
        // At the time of writing Adoptium Eclipse Temurin does not, which caused a bunch of Jlink confusion.
        src("https://download.bell-sw.com/java/$javaVersion+$jdkVersion/bellsoft-jdk$javaVersion+$jdkVersion-${cfg.apiOS}-${cfg.apiArchitecture}.$extension")
        dest(layout.buildDirectory.file("jmods/compressed/${cfg.name}.$extension"))
        overwrite(false)
    }

    val decompress = tasks.register<Copy>("decompressJmods${taskName(cfg)}") {
        group = "release setup"
        dependsOn(download)
        from(download.map { t -> decompresser(t.dest) }) {
            // Strip the archive's top-level directory, which vendors name
            // inconsistently ("jdk-25.0.2", "jdk-25.0.2.jdk", Adoptium's
            // "JDK-<version>-jmods"). jlink is pointed at <cfg>/jmods below.
            eachFile {
                relativePath = RelativePath(relativePath.isFile,
                    *relativePath.segments.drop(1).toTypedArray())
            }
            includeEmptyDirs = false
        }
        into(layout.buildDirectory.dir("jmods/decompressed/${cfg.name}"))
    }

    val jlink = tasks.register<Exec>("jlink${taskName(cfg)}") {
        group = "release setup"
        dependsOn(decompress)

        val modulePath = layout.buildDirectory.dir("jmods/decompressed/${cfg.name}/jmods")
        val outputDir = layout.buildDirectory.dir("java-runtime-image/${cfg.name}")
        // jdk.httpserver added (modified from FVX, 11/10/2026): the web interface (com.uprfvx.random.web.WebServer)
        // uses com.sun.net.httpserver.HttpServer, which lives in that module; release builds crashed with
        // NoClassDefFoundError without it since this jlink module list predates the web interface.
        val modules = "java.base,java.desktop,java.logging,java.naming,jdk.httpserver"

        doFirst {
            // jlink silently falls back to the running JDK's system modules
            // when the module path is empty, producing images for the wrong
            // platform. Fail instead.
            if (modulePath.get().asFile.listFiles { f -> f.extension == "jmod" }.isNullOrEmpty()) {
                throw GradleException(
                    "No .jmod files found in ${modulePath.get().asFile.absolutePath}; " +
                    "the JDK archive for ${cfg.name} has an unexpected layout."
                )
            }
            outputDir.get().asFile.deleteRecursively()
        }

        executable = javaHome.map { it.file("bin/jlink").asFile.absolutePath }.get()
        args(
                "--module-path", modulePath.get().asFile.absolutePath,
                "--add-modules", modules,
                "--output", outputDir.get().asFile.absolutePath
        )
    }

    val copy = tasks.register<Copy>("moveIntoReleaseDir${taskName(cfg)}") {
        group = "release setup"
        dependsOn("jar")
        dependsOn("clearReleaseDir")
        dependsOn(jlink)

        // Restructured (packaging cleanup, 11/10/2026): the zip root used to mix the launcher scripts
        // with a pile of program internals (jar, data/, bundled java/ runtime), which read as confusing.
        // Now the zip root only has the launchers and the README; everything else lives under app/.
        from("src/main/resources") {
            exclude("data/custom_player_graphics_unused")
            exclude("com")
            into("app")
        }
        // Renamed from FVX's unqualified "launcher.*" (11/10/2026): that name made the old, unbranded
        // Swing interface look like the main way to start the program. The web interface (web-launcher.*)
        // is the recommended one; this legacy name makes that unambiguous in the zip's file listing.
        val launcherName = "legacy-swing-launcher.${cfg.launcherExtension}"
        // Moved into app/ (packaging cleanup, 11/10/2026): the zip root now contains only
        // web-launcher.*, so the legacy launcher and the README move alongside the jar.
        from("src/main/launcher") {
            include("README.txt")
            expand("launcherName" to launcherName)
            filter { line ->
                when {
                    line.startsWith("[LINUX_ONLY]") ->
                        if (cfg.apiOS == "linux") line.removePrefix("[LINUX_ONLY]") else null
                    else -> line
                }
            }
            into("app")
        }
        from("src/main/launcher") {
            include("launcher_*.${cfg.launcherExtension}")
            rename { launcherName }
            into("app")
        }
        // Modified from YAO (10/10/2026): a second launcher that opens the local web interface.
        from("src/main/launcher") {
            include("web-launcher_*.${cfg.launcherExtension}")
            rename { "web-launcher.${cfg.launcherExtension}" }
        }
        from(layout.buildDirectory.dir("libs")) {
            into("app")
        }
        from(layout.buildDirectory.dir("java-runtime-image/${cfg.name}")) {
            into("app/java")
        }

        into(layout.buildDirectory.dir("target/${cfg.name}"))
    }

    // This is intentionally zip for all platforms, since that makes the downloads easier to navigate for end users.
    val zip = tasks.register<Zip>("createReleaseZip${taskName(cfg)}") {
        group = "release setup"
        dependsOn(copy)
        dependsOn("getVersionName")

        from(layout.buildDirectory.dir("target/${cfg.name}"))
        destinationDirectory = file("build/dist")
        doFirst {
            // needs to be inside doFirst because extra["randomizerVersion"] is not defined before getVersionName runs.
            val versionName = (rootProject.extra["randomizerVersion"] as String).replace(".", "_")
            // Renamed from FVX's UPR_FVX-* naming (11/10/2026): this is our own build, not an FVX build.
            archiveFileName = "pkmn-rnd-yao_${versionName}-${cfg.name}.zip"
        }
    }
    zipTasks.add(zip)
}

tasks.register("createReleaseZips") {
    group = "release"
    dependsOn(zipTasks)
}

tasks.register<Exec>("jdeps") {
    dependsOn(tasks.jar)

    executable = javaHome.map { it.file("bin/jdeps").asFile.absolutePath }.get()
    args(
        layout.buildDirectory.dir("libs").get().asFile.absolutePath + "/UPR-FVX.jar"
    )
}

fun detectPlatform(): PlatformConfig {
    // detects the platform you're running this on
    val name = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")
    return if (name.contains("Linux")) {
        if (arch == "aarch64") PlatformConfig.Linux_ARM else PlatformConfig.Linux_x86
    } else if (name.contains("Windows")) {
        PlatformConfig.Windows
    } else if (name.contains("Mac") || name.contains("mac")) {
        if (arch == "aarch64") PlatformConfig.Mac_ARM else PlatformConfig.Mac_x86
    } else {
        error("Unidentified platform: $name (arch: $arch)")
    }
}

tasks.register<Exec>("launch") {
    group = "launch"
    val platform: PlatformConfig = detectPlatform()
    dependsOn("moveIntoReleaseDir${taskName(platform)}")

    // The legacy launcher now lives inside app/ alongside the jar (packaging cleanup, 11/10/2026).
    workingDir(layout.buildDirectory.dir("target/${platform.name}/app"))
	if (platform == PlatformConfig.Windows) {
		commandLine("cmd", "/c", "legacy-swing-launcher.${platform.launcherExtension}")
	} else {
		// Not tested on Mac due to a lack of devices to test on.
		commandLine("bash", "./legacy-swing-launcher.${platform.launcherExtension}")
	}
}

tasks.register<Exec>("relaunch") {
    group = "launch"
    val platform: PlatformConfig = detectPlatform()

    // The legacy launcher now lives inside app/ alongside the jar (packaging cleanup, 11/10/2026).
    workingDir(layout.buildDirectory.dir("target/${platform.name}/app"))

    if (platform == PlatformConfig.Windows) {
		commandLine("cmd", "/c", "legacy-swing-launcher.${platform.launcherExtension}")
	} else {
		// Not tested on Mac due to a lack of devices to test on.
		commandLine("bash", "./legacy-swing-launcher.${platform.launcherExtension}")
	}
}

tasks.named<Test>("test") {
    filter {
        excludeTestsMatching("*Randomizer*Test")
        excludeTestsMatching("*Updater*Test")
    }
}

// TODO: this shares a bunch of info with random:testROMs, would be nice to combine them
tasks.register<Test>("testROMs") {
    description = "Runs tests dependent on loading ROM files."
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("romsPath", rootProject.file("roms").absolutePath)

    shouldRunAfter("test")

    useJUnitPlatform()
    ignoreFailures = true
    maxHeapSize = "6G"

    filter {
        includeTestsMatching("*Randomizer*Test")
        includeTestsMatching("*Updater*Test")
    }
}

