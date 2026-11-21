import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.13.0-rc01"
}

group = "io.github.soundmesh"
version = "1.2.0"

val miuixVersion = "0.9.5-beta03"
val jnaVersion = "5.19.1"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("top.yukonga.miuix.kmp:miuix-ui:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-preference:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-icons:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-window:$miuixVersion")
    implementation("net.java.dev.jna:jna:$jnaVersion")
    implementation("net.java.dev.jna:jna-platform:$jnaVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "io.github.soundmesh.MainKt"
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED", "-Dfile.encoding=UTF-8")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "SoundMesh"
            packageVersion = project.version.toString()
            description = "Play system audio on multiple output devices at once"
            vendor = "SoundMesh"
            modules("java.instrument", "jdk.unsupported", "java.management", "java.net.http")
            windows {
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                menuGroup = "SoundMesh"
                upgradeUuid = "6c3f0a7e-4f3b-4c7e-9d0b-2f5f1a9e8c11"
                iconFile.set(project.file("packaging/icon.ico"))
            }
        }
    }
}
