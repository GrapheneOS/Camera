buildscript {
    repositories {
        // dependabot cannot handle google()
        maven {
            url = uri("https://dl.google.com/dl/android/maven2")
        }
        // dependabot cannot handle mavenCentral()
        maven {
            url = uri("https://repo.maven.apache.org/maven2")
        }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.2.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21")
    }
}

allprojects {
    tasks.withType<JavaCompile> {
        val compilerArgs = options.compilerArgs
        compilerArgs.add("-Xlint:unchecked")
        compilerArgs.add("-Xlint:deprecation")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
