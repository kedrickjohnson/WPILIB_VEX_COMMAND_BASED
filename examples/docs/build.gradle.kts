// Code shown in the documentation. It isn't a robot: it's compiled (and the sim example run)
// in CI so every snippet in the guides is real, working code. The guides quote the regions
// between "// [name]" and "// [/name]" comments.
plugins {
    java
}

dependencies {
    implementation(project(":vexlib"))
}

sourceSets {
    create("sim") {
        java.srcDir("src/sim/java")
        compileClasspath += sourceSets["main"].output + sourceSets["main"].compileClasspath
        runtimeClasspath += sourceSets["main"].output + sourceSets["main"].runtimeClasspath
    }
}

tasks.register<JavaExec>("simulate") {
    group = "application"
    description = "Runs the documentation's simulator example."
    classpath = sourceSets["sim"].runtimeClasspath
    mainClass.set("com.robojacks.docs.DocsSimExample")
}

tasks.named("build") { dependsOn(tasks.named("simClasses")) }
