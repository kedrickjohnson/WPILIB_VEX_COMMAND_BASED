plugins {
    `java-library`
    `maven-publish`
}

group = property("vexlibGroup") as String
version = property("vexlibVersion") as String
val hydrozoaVersion = property("hydrozoaVersion") as String
val siteUrl = property("vexlibSiteUrl") as String

dependencies {
    // Only lib/hal/hydrozoa uses this directly; everything else is plain Java.
    api("dev.vexide:hydrozoa:$hydrozoaVersion")
}

java {
    withSourcesJar()  // go-to-definition in the IDE
    withJavadocJar()  // hover docs in the IDE
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

// ---------------------------------------------------------------- vendor library release
//
//   ./gradlew :vexlib:publish
//
// writes the Maven artifacts to docs/maven/ and the vendordep JSON to docs/vendordeps/.
// Commit docs/ and GitHub Pages serves them; teams install the JSON like an FRC vendordep.

publishing {
    publications {
        create<MavenPublication>("vexlib") {
            artifactId = "vexlib"
            from(components["java"])
            pom {
                name.set("VexLib")
                description.set("FRC-style command-based programming for VEX V5 in Java (Hydrozoa).")
                url.set(siteUrl)
                licenses { license { name.set("MIT License"); url.set("https://opensource.org/licenses/MIT") } }
            }
        }
    }
    repositories {
        maven {
            name = "docs"
            url = uri(rootProject.layout.projectDirectory.dir("docs/maven"))
        }
    }
}

val vendordepJson by tasks.registering {
    description = "Writes docs/vendordeps/VexLib.json for this version."
    val out = rootProject.layout.projectDirectory.file("docs/vendordeps/VexLib.json")
    outputs.file(out)
    doLast {
        out.asFile.parentFile.mkdirs()
        out.asFile.writeText(
            """
            {
              "fileName": "VexLib.json",
              "name": "VexLib",
              "version": "$version",
              "uuid": "${property("vexlibUuid")}",
              "hydrozoaVersion": "$hydrozoaVersion",
              "mavenUrls": ["$siteUrl/maven"],
              "jsonUrl": "$siteUrl/vendordeps/VexLib.json",
              "javaDependencies": [
                { "groupId": "$group", "artifactId": "vexlib", "version": "$version" }
              ]
            }
            """.trimIndent() + "\n"
        )
    }
}

tasks.named("publish") { dependsOn(vendordepJson) }
