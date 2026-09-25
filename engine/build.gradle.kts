plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin. No Android imports, ever (build plan Phase 1 acceptance) - that is what
// keeps the tests fast and lets the offline pack tool run the real solver.

// Bytecode target matches :app. No toolchain: the build runs on Android Studio's JBR.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Calibration tests are tagged "explore" and only run with -Pexplore.
val explore = providers.gradleProperty("explore").isPresent

tasks.test {
    useJUnitPlatform {
        if (explore) includeTags("explore") else excludeTags("explore")
    }
    maxHeapSize = "2g"
    testLogging.showStandardStreams = explore
    // Overrides the generated-puzzle sample size, e.g. -PperDifficulty=100.
    providers.gradleProperty("perDifficulty").orNull?.let { systemProperty("killer.perDifficulty", it) }
}

// Offline pack tool. Never runs on device.
//   ./gradlew :engine:generatePuzzlePack -Pcount=5000 -Pseed=1 -Pthreads=8
tasks.register<JavaExec>("generatePuzzlePack") {
    group = "killer"
    description = "Regenerates the bundled puzzle pack (app/src/main/assets/puzzles.bin)."
    mainClass.set("com.ganim.killersudoku.engine.tools.GeneratePackKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Xmx2g")
    args("--out=${rootProject.file("app/src/main/assets/puzzles.bin").absolutePath}")
    listOf("count", "seed", "threads").forEach { key ->
        (project.findProperty(key) as String?)?.let { args("--$key=$it") }
    }
}

tasks.register<JavaExec>("bench") {
    group = "killer"
    description = "Per-technique timing over sample solves (development aid)."
    mainClass.set("com.ganim.killersudoku.engine.tools.BenchKt")
    classpath = sourceSets["main"].runtimeClasspath
    (project.findProperty("n") as String?)?.let { args(it) }
}
