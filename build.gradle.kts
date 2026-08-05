import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ktlint)
}

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.ksp.gradle.plugin)
    }
}

val ktlintCliVersion: String = the<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("ktlint")
    .get()
    .requiredVersion

configure<KtlintExtension> {
    version.set(ktlintCliVersion)

    filter {
        exclude("**/build/**")
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<KtlintExtension> {
        version.set(ktlintCliVersion)

        filter {
            exclude("**/build/**")
        }
    }
}

allprojects {
    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(
            listOf(
                "-Xlint",
                "-Xlint:-cast",
                "-Xlint:-classfile",
                "-Xlint:-rawtypes",
                "-Xlint:-serial",
            ),
        )
    }
}
