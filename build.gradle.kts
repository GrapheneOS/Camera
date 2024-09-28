plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
}

allprojects {
    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xlint", "-Xlint:-cast", "-Xlint:-rawtypes", "-Xlint:-serial"))
    }
}
