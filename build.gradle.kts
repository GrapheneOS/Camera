plugins {
    id("com.android.application") version "8.4.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

allprojects {
    tasks.withType<JavaCompile> {
        val compilerArgs = options.compilerArgs
        compilerArgs.add("-Xlint:unchecked")
        compilerArgs.add("-Xlint:deprecation")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
