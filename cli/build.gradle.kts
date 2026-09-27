import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

application {
    mainClass = "io.github.philtomlinson.fitgpx.cli.MainKt"
    applicationName = "fitgpx"
}

dependencies {
    implementation(project(":fit-core"))
}

/** A single runnable jar: `java -jar fitgpx-cli.jar …` */
val fatJar by tasks.registering(Jar::class) {
    group = "distribution"
    description = "Builds a self-contained runnable CLI jar."
    archiveFileName = "fitgpx-cli.jar"
    manifest { attributes["Main-Class"] = application.mainClass }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(sourceSets.main.map { it.output })
    from(configurations.runtimeClasspath.map { cp -> cp.map { if (it.isDirectory) it else zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**", "META-INF/*.kotlin_module")
    }
}
