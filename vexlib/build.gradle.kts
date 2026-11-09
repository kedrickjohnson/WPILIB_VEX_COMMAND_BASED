plugins {
    `java-library`
}

dependencies {
    // Only lib/hal/hydrozoa uses this directly; everything else is plain Java.
    api("dev.vexide:hydrozoa:0.1.0-alpha.1")
}
