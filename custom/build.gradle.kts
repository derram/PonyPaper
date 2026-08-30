plugins {
    java
    application
}

// Desktop custom-pony editor (Swing). Shares only pure-Java PonyDefinition from :app
// sources — do not depend on the Android application module.

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("uk.cpjsmith.ponypaper.custom.PonyEditor")
}

dependencies {
    // Modern Swing L&F; bundled into customponies.jar (fat jar below).
    implementation("com.formdev:flatlaf:3.7.2")
}

sourceSets {
    main {
        java {
            // Existing layout: custom/src/... (not src/main/java)
            setSrcDirs(
                listOf(
                    layout.projectDirectory.dir("src"),
                    // Shared model only — never the rest of the Android app tree
                    layout.projectDirectory.dir("../app/src/main/java"),
                )
            )
            include(
                "uk/cpjsmith/ponypaper/custom/**",
                "uk/cpjsmith/ponypaper/PonyDefinition.java",
                "uk/cpjsmith/ponypaper/WaitExpiry.java",
                "uk/cpjsmith/ponypaper/SceneExit.java",
            )
        }
    }
}

tasks.jar {
    archiveBaseName.set("customponies")
    // Stable name for docs / local use; CI renames with the app versionName for releases.
    archiveVersion.set("")
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "uk.cpjsmith.ponypaper.custom.PonyEditor"
    }
    // Avoid "customponies-.jar" when archiveVersion is empty
    archiveFileName.set("customponies.jar")

    // Fat jar so `java -jar customponies.jar` still works with FlatLaf on the classpath.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.EC")
}

tasks.named<Jar>("jar") {
    // Ensure the jar is executable-style for file managers that honor +x on zip/jar
    doLast {
        archiveFile.get().asFile.setExecutable(true, false)
    }
}

tasks.register<JavaExec>("testPacker") {
    group = "verification"
    description = "Run ImageImport still-frame packer checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.ImageImportPackTest")
}

tasks.register<JavaExec>("testDefinition") {
    group = "verification"
    description = "Run PonyDefinition action-graph validation checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.PonyDefinitionValidateTest")
}

tasks.register<JavaExec>("testWeightedLists") {
    group = "verification"
    description = "Run next/start list name:N parse and rewrite checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.WeightedActionListTest")
}

tasks.register<JavaExec>("testWaitExpiry") {
    group = "verification"
    description = "Run idle stay-or-go weight checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.WaitExpiryTest")
}

tasks.register<JavaExec>("testSceneExit") {
    group = "verification"
    description = "Run 1-in-8 scene-leave roll checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.SceneExitTest")
}

tasks.register<JavaExec>("testSpritePreview") {
    group = "verification"
    description = "Run spritesheet preview / anchor-picker zoom checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.SpriteSheetPreviewTest")
}

tasks.register<JavaExec>("testActionFrames") {
    group = "verification"
    description = "Run ActionFrameSource wide-sheet / VolatileImage blit checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.ActionFrameSourceTest")
}

tasks.register<JavaExec>("testFileChooserScroll") {
    group = "verification"
    description = "Run FlatLaf file-chooser directory scroll-home checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.EditorFileChoosersTest")
}

tasks.named("check") {
    dependsOn("testPacker")
    dependsOn("testDefinition")
    dependsOn("testWeightedLists")
    dependsOn("testWaitExpiry")
    dependsOn("testSceneExit")
    dependsOn("testSpritePreview")
    dependsOn("testActionFrames")
    dependsOn("testFileChooserScroll")
}
