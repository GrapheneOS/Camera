plugins {
    id("com.android.application") version "8.8.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}

allprojects {
    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-Xlint", "-Xlint:-cast", "-Xlint:-classfile", "-Xlint:-rawtypes", "-Xlint:-serial"))
    }
}
