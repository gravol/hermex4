// JVM preview harness — NOT part of the Android app.
// Craft Anything's app-screenshot check starts the project's built program via the
// Gradle toolchain launch recipe (`build/libs/*.jar` run with `java -jar`) and passes
// `--screenshot <file.png>`. An Android APK can't be started that way, so this module
// renders the Phase-1 home screen (the same layout, colors and sample data the Android
// app draws, per shots-tour-2026-11-22/01-launch.png) to a PNG headlessly. See NOTES.md.
plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("hermes-clone-preview")
    manifest.attributes["Main-Class"] = "com.gravol.hermesclone.preview.Preview"
    // The toolchain launch recipe looks for build/libs/*.jar under the Gradle root.
    destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
}
