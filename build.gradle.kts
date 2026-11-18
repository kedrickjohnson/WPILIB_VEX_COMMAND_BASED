plugins {
    // Version declared once here; robot projects apply it with id("dev.vexide.hydrozoa").
    id("dev.vexide.hydrozoa") version "0.1.0-alpha.1" apply false
}

subprojects {
    apply(plugin = "java")

    repositories {
        // Hydrozoa (built from its main branch) and VexLib releases, published in this repo.
        maven { url = uri(rootProject.layout.projectDirectory.dir("docs/maven")) }
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21)) // same as Hydrozoa
        }
    }
}
