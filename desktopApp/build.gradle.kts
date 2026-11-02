import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val xliteVersion = providers.gradleProperty("xliteVersion").get()

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.xunlei.desktop)
    implementation(libs.okio)
    testImplementation(kotlin("test-junit"))
}

// App images do not need WiX; system MSI creation is handled by package-installer.ps1.
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    val stub = layout.buildDirectory.dir("wix-stub").get().asFile
    stub.mkdirs()
    wixToolsetDir.set(stub)
}

compose.desktop {
    application {
        mainClass = "com.xlite.downloader.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "XLite"
            packageVersion = xliteVersion
            description = "XLite - Xunlei accelerated downloader"
            vendor = "XLite"
            copyright = "Copyright 2026 XLite"
            includeAllModules = true

            windows {
                iconFile.set(project.file("src/main/resources/xlite.ico"))
                menuGroup = "XLite"
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = false
                // Permanent product family ID: never regenerate this for a new release.
                upgradeUuid = "6f0f2c9e-6b6a-4f6c-9d2c-1f4a5b7c8d90"
            }
            linux {
                iconFile.set(project.file("src/main/resources/xlite.png"))
                packageName = "xlite"
                debMaintainer = "XLite"
            }
        }
    }
}

// Apply after the Compose plugin has wired its launcher argument providers.
// This changes distributions only; development JVMs keep their normal options.
gradle.projectsEvaluated {
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().forEach { task ->
            task.launcherJvmArgs.addAll(
                "-Dxlite.home=\$APPDIR/..",
                "-Djava.io.tmpdir=\$APPDIR/../data/tmp",
                "-Djna.tmpdir=\$APPDIR/../data/tmp",
                "-XX:-UsePerfData",
                "-XX:ErrorFile=\$APPDIR/../data/hs_err_pid%p.log",
            )
            if (task.name == "createDistributable") {
                task.doLast {
                    task.destinationDir.dir("XLite/data/tmp").get().asFile.mkdirs()
                    task.destinationDir.dir("XLite/downloads").get().asFile.mkdirs()
                }
            }
        }
    }
}
