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
                "uk/cpjsmith/ponypaper/EffectPlacement.java",
                "uk/cpjsmith/ponypaper/WanderTarget.java",
                "uk/cpjsmith/ponypaper/WaitExpiry.java",
                "uk/cpjsmith/ponypaper/SceneExit.java",
                "uk/cpjsmith/ponypaper/WorldFlow.java",
                "uk/cpjsmith/ponypaper/SpawnYBand.java",
                "uk/cpjsmith/ponypaper/UnpinnedLru.java",
                "uk/cpjsmith/ponypaper/InactivePick.java",
                "uk/cpjsmith/ponypaper/InactiveRoster.java",
                "uk/cpjsmith/ponypaper/HerdDrain.java",
                "uk/cpjsmith/ponypaper/ShuffleMixBag.java",
                "uk/cpjsmith/ponypaper/BackgroundAlbumLogic.java",
                "uk/cpjsmith/ponypaper/DragExit.java",
                "uk/cpjsmith/ponypaper/CustomDefinitionCache.java",
                "uk/cpjsmith/ponypaper/SecureXml.java",
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

tasks.register<JavaExec>("testEditorEffects") {
    group = "verification"
    description = "Run PonyEditor effect CRUD and action-scrub checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.PonyEditorEffectTest")
}

tasks.register<JavaExec>("testSpritesFromField") {
    group = "verification"
    description = "Run Sprites-from alias field DocumentListener checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.SpritesFromFieldTest")
}

tasks.register<JavaExec>("testTimingsAdjust") {
    group = "verification"
    description = "Run shared frame-timings +/- adjuster checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.TimingsAdjustTest")
}

tasks.register<JavaExec>("testDpEffects") {
    group = "verification"
    description = "Run Desktop Ponies Effect-line import checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.DesktopPoniesEffectImportTest")
}

tasks.register<JavaExec>("testEffectPlacement") {
    group = "verification"
    description = "Run effect placement math checks (wallpaper parity)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.EffectPlacementMathTest")
}

tasks.register<JavaExec>("testWanderTarget") {
    group = "verification"
    description = "Run wander / movement-mode token and band checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.WanderTargetTest")
}

tasks.register<JavaExec>("testWorldFlow") {
    group = "verification"
    description = "Run World Flow spawn-bag selection checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.WorldFlowTest")
}

tasks.register<JavaExec>("testUnpinnedLru") {
    group = "verification"
    description = "Run unpinned sprite LRU byte-budget checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.UnpinnedLruTest")
}

tasks.register<JavaExec>("testDefinitionCache") {
    group = "verification"
    description = "Run custom XML definition cache stamp checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.CustomDefinitionCacheTest")
}

tasks.register<JavaExec>("testInactivePick") {
    group = "verification"
    description = "Run inactive-pool prefetch pick checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.InactivePickTest")
}

tasks.register<JavaExec>("testInactiveRoster") {
    group = "verification"
    description = "Run key-only inactive roster pick/skip checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.InactiveRosterTest")
}

tasks.register<JavaExec>("testHerdDrain") {
    group = "verification"
    description = "Run wander herd-drain stagger, timeout, and exit-decision checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.HerdDrainTest")
}

tasks.register<JavaExec>("testShuffleMixBag") {
    group = "verification"
    description = "Run dream shuffle include-set filter checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.ShuffleMixBagTest")
}

tasks.register<JavaExec>("testBackgroundAlbum") {
    group = "verification"
    description = "Run saved-background album cycle helper checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.BackgroundAlbumLogicTest")
}

tasks.register<JavaExec>("testDragExit") {
    group = "verification"
    description = "Run drag-to-edge axis and margin checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.DragExitTest")
}

tasks.register<JavaExec>("testEditorCli") {
    group = "verification"
    description = "Run custom editor CLI implied -load checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.PonyEditorCLITest")
}

tasks.register<JavaExec>("testEditorWindowFocus") {
    group = "verification"
    description = "Run editor owner-window dimming checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.EditorWindowFocusTest")
}

tasks.register<JavaExec>("testSpawnYBand") {
    group = "verification"
    description = "Run feet-anchored spawn Y inset checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.SpawnYBandTest")
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
    dependsOn("testEditorEffects")
    dependsOn("testSpritesFromField")
    dependsOn("testTimingsAdjust")
    dependsOn("testDpEffects")
    dependsOn("testEffectPlacement")
    dependsOn("testWanderTarget")
    dependsOn("testWorldFlow")
    dependsOn("testSpawnYBand")
    dependsOn("testUnpinnedLru")
    dependsOn("testDefinitionCache")
    dependsOn("testInactivePick")
    dependsOn("testInactiveRoster")
    dependsOn("testHerdDrain")
    dependsOn("testShuffleMixBag")
    dependsOn("testBackgroundAlbum")
    dependsOn("testDragExit")
    dependsOn("testEditorCli")
    dependsOn("testEditorWindowFocus")
}
