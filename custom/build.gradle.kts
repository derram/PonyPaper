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

tasks.register<JavaExec>("testWaitExpiry") {
    group = "verification"
    description = "Run idle stay-or-go weight checks"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("uk.cpjsmith.ponypaper.custom.WaitExpiryTest")
}

tasks.named("check") {
    dependsOn("testPacker")
    dependsOn("testDefinition")
    dependsOn("testWaitExpiry")
}
