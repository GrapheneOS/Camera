package camera.licenses

import org.gradle.api.Plugin
import org.gradle.api.Project

class LicensesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.tasks.register("generateLicenses", GenerateLicensesTask::class.java) {
            group = "documentation"
            description = "Generates src/main/assets/licenses.json from the release runtime classpath."

            jsonOutput.set(target.file("src/main/assets/licenses.json"))
        }
    }
}
