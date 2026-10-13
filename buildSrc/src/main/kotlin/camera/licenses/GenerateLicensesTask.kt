package camera.licenses

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateLicensesTask : DefaultTask() {

    @get:Input
    abstract val title: Property<String>

    @get:Input
    abstract val configurationName: Property<String>

    @get:Input
    abstract val copyrightOverrides: ListProperty<CopyrightOverride>

    @get:Input
    abstract val extraNotices: ListProperty<ExtraNotice>

    @get:Optional
    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:Optional
    @get:OutputFile
    abstract val jsonOutput: RegularFileProperty

    init {
        title.convention(DEFAULT_TITLE)
        configurationName.convention(DEFAULT_CONFIGURATION_NAME)
        copyrightOverrides.convention(emptyList())
        extraNotices.convention(emptyList())
        notCompatibleWithConfigurationCache(
            "Uses ArtifactResolutionQuery to fetch POMs at execution time",
        )
    }

    @TaskAction
    fun run() {
        val configName = configurationName.get()
        val configuration = project.configurations.findByName(configName)
            ?: throw GradleException(
                "Configuration '$configName' not found in ${project.path}",
            )

        val extractor = LicenseExtractor(
            dependencies = project.dependencies,
            gradleUserHomeDir = project.gradle.gradleUserHomeDir,
        )

        val components = collectComponents(configuration, extractor)
        logger.lifecycle("Processing ${components.size} components from $configName")

        val resolved = resolveAll(components, extractor)
        val overridden = applyOverrides(resolved, copyrightOverrides.get())

        for (rendered in overridden) {
            SpdxPolicy.checkOwner(rendered.record)
        }

        val licenseBlocks = overridden.map(::toNoticeBlock)
        val extraBlocks = resolveExtraNotices(extraNotices.get(), extractor)
        val blocks = (licenseBlocks + extraBlocks).sortedWith(BY_HEADING)

        val htmlFile = output.orNull?.asFile?.also { outputFile ->
            outputFile.parentFile.mkdirs()
            outputFile.writeText(HtmlRenderer.render(title.get(), blocks))
        }
        val jsonFile = jsonOutput.orNull?.asFile?.also { outputFile ->
            outputFile.parentFile.mkdirs()
            outputFile.writeText(JsonRenderer.render(overridden, extraNotices.get().zip(extraBlocks)))
        }

        logSummary(overridden, extraBlocks, listOfNotNull(htmlFile, jsonFile))
    }

    private fun toNoticeBlock(rendered: RenderedLicense): NoticeBlock {
        return NoticeBlock(
            heading = licenseHeading(rendered.record),
            body = withSource(rendered.record.projectUrl, rendered.text),
        )
    }

    private fun licenseHeading(record: LicenseRecord): String {
        val coordinates = record.coordinates
        val projectName = record.projectName?.trim().orEmpty()
        val isMissing = projectName.isEmpty()
        val sameAsArtifact = projectName.equals(coordinates.name, ignoreCase = true)

        return when {
            isMissing || sameAsArtifact -> "Notice for $coordinates"
            else -> "Notice for $projectName ($coordinates)"
        }
    }

    private fun resolveExtraNotices(
        notices: List<ExtraNotice>,
        extractor: LicenseExtractor,
    ): List<NoticeBlock> {
        return notices.map { notice ->
            val spdxBody = notice.spdxId?.let { id ->
                extractor.bundledSpdxText(id)
                    ?: throw GradleException(
                        "Extra notice '${notice.name}' references unknown SPDX id '$id'. " +
                            "Add the text to buildSrc/src/main/resources/licenses/$id.txt.",
                    )
            }

            val body = buildNoticeBody(notice.text, spdxBody)
                ?: throw GradleException(
                    "Extra notice '${notice.name}' must define text and/or spdxId.",
                )

            NoticeBlock(
                heading = "Notice for ${notice.name}",
                body = withSource(notice.url, body),
            )
        }
    }

    private fun buildNoticeBody(
        inlineText: String?,
        spdxBody: String?,
    ): String? {
        val inline = inlineText?.trimEnd()?.ifEmpty { null }
        val spdx = spdxBody?.trim()?.ifEmpty { null }

        return when {
            inline != null && spdx != null -> "$inline\n\n$spdx"
            inline != null -> inline
            else -> spdx
        }
    }

    private fun withSource(
        sourceUrl: String?,
        body: String,
    ): String {
        val url = sourceUrl?.trim().orEmpty()
        return when {
            url.isEmpty() -> body
            else -> "Source: $url\n\n$body"
        }
    }

    private fun collectComponents(
        configuration: Configuration,
        extractor: LicenseExtractor,
    ): List<Component> {
        resolveArtifactsToCache(configuration)

        val raw = mutableListOf<Component>()
        for (resolvedComponent in configuration.incoming.resolutionResult.allComponents) {
            val id = resolvedComponent.id as? ModuleComponentIdentifier ?: continue
            if (SKIP_NAME_SUFFIXES.any { id.module.endsWith(it) }) continue

            val coordinates = Coordinates(
                group = id.group,
                name = id.module,
                version = id.version,
            )
            raw += Component(
                coordinates = coordinates,
                file = extractor.findCachedArtifact(coordinates),
            )
        }

        return raw.dedupeKeepingHighestVersion()
    }

    private fun resolveArtifactsToCache(configuration: Configuration) {
        configuration.incoming.artifactView {
            componentFilter { it is ModuleComponentIdentifier }
            isLenient = true
        }.files.files
    }

    private fun List<Component>.dedupeKeepingHighestVersion(): List<Component> {
        val topByModule = mutableMapOf<String, Component>()
        for (component in this) {
            val moduleKey = component.coordinates.moduleId
            val currentTop = topByModule[moduleKey]

            val isHigherVersion = when {
                currentTop != null -> {
                    compareVersions(
                        left = component.coordinates.version,
                        right = currentTop.coordinates.version,
                    ) > 0
                }

                else -> true
            }

            if (isHigherVersion) {
                topByModule[moduleKey] = component
            }
        }

        return topByModule.values.toList()
    }

    private fun resolveAll(
        components: List<Component>,
        extractor: LicenseExtractor,
    ): List<RenderedLicense> {
        return components.map { component ->
            val result = extractor.extract(
                coordinates = component.coordinates,
                artifactFile = component.file,
            )
            val record = result.record
                ?: throwUnresolved(component.coordinates, result.declared)

            SpdxPolicy.checkAllowed(record)

            val licenseText = extractor.materializeText(record)
                ?: throwUnresolved(component.coordinates, record.declared)

            RenderedLicense(record, licenseText)
        }
    }

    private fun throwUnresolved(
        coordinates: Coordinates,
        declared: List<LicenseRef>,
    ): Nothing {
        val hint = declared.firstOrNull()
            ?.let { it.name.ifEmpty { it.url } }
            ?: "no license metadata found"

        throw GradleException(
            "Unresolved license for $coordinates (declared: $hint). " +
                "Add the SPDX text to plugin resources (buildSrc/src/main/resources/licenses/) " +
                "or supply a copyrightOverrides entry in the generateLicenses task configuration.",
        )
    }

    private fun applyOverrides(
        items: List<RenderedLicense>,
        overrides: List<CopyrightOverride>,
    ): List<RenderedLicense> {
        if (overrides.isEmpty()) {
            return items
        }

        val copyrightByPattern = overrides.associate { it.coordinates to it.copyright }
        return items.map { item ->
            val copyright = matchOverride(item.record.coordinates, copyrightByPattern)
                ?: return@map item

            val combinedText = listOf(
                copyright,
                "",
                item.text,
            ).joinToString(separator = "\n")

            RenderedLicense(
                record = item.record.copy(overrideApplied = true),
                text = combinedText,
            )
        }
    }

    private fun matchOverride(
        coordinates: Coordinates,
        copyrightByKey: Map<String, String>,
    ): String? {
        val lookupKeys = listOf(
            coordinates.toString(),
            coordinates.moduleId,
            "${coordinates.group}:$WILDCARD_MARKER",
        )

        for (key in lookupKeys) {
            val match = copyrightByKey[key]
            if (match != null) {
                return match
            }
        }

        return null
    }

    private fun logSummary(
        items: List<RenderedLicense>,
        extras: List<NoticeBlock>,
        outputFiles: List<File>,
    ) {
        val embeddedCount = items.count { it.record.source is LicenseSource.Embedded }
        val viaSpdxCount = items.count { it.record.source is LicenseSource.Spdx }
        val withNoticeCount = items.count { it.record.noticeText != null }

        val spdxUsage = items
            .mapNotNull { it.record.spdxId }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()

        logger.lifecycle(
            "Done. embedded=$embeddedCount via-spdx=$viaSpdxCount " +
                "with-notice=$withNoticeCount extra=${extras.size} " +
                "total=${items.size + extras.size}",
        )
        logger.lifecycle("SPDX texts used: $spdxUsage")
        outputFiles.forEach { logger.lifecycle("Output: ${it.absolutePath}") }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split(VERSION_SPLIT_RE)
        val rightParts = right.split(VERSION_SPLIT_RE)
        val maxLength = maxOf(leftParts.size, rightParts.size)

        for (index in 0 until maxLength) {
            val leftPart = leftParts.getOrNull(index).orEmpty()
            val rightPart = rightParts.getOrNull(index).orEmpty()

            val comparison = compareVersionPart(leftPart, rightPart)
            if (comparison != 0) {
                return comparison
            }
        }

        return 0
    }

    private fun compareVersionPart(
        left: String,
        right: String,
    ): Int {
        val leftIsNumeric = left.isNotEmpty() && left.all(Char::isDigit)
        val rightIsNumeric = right.isNotEmpty() && right.all(Char::isDigit)

        return when {
            leftIsNumeric && rightIsNumeric -> left.toLong().compareTo(right.toLong())
            leftIsNumeric -> 1
            rightIsNumeric -> -1
            else -> left.lowercase().compareTo(right.lowercase())
        }
    }

    private data class Component(
        val coordinates: Coordinates,
        val file: File?,
    )

    private companion object {
        const val DEFAULT_TITLE = "Open source licenses"
        const val DEFAULT_CONFIGURATION_NAME = "releaseRuntimeClasspath"

        const val WILDCARD_MARKER = "*"

        val SKIP_NAME_SUFFIXES = listOf("-bom", "-parent", "-platform")

        val VERSION_SPLIT_RE = Regex("[.\\-_+]")

        val BY_HEADING: Comparator<NoticeBlock> = Comparator { left, right ->
            left.heading.compareTo(right.heading, ignoreCase = true)
        }
    }
}
