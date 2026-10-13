package camera.licenses

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

internal class LicenseExtractor(
    private val dependencies: DependencyHandler,
    private val gradleUserHomeDir: File,
) {

    private val spdxTextCache = mutableMapOf<String, String?>()

    fun extract(
        coordinates: Coordinates,
        artifactFile: File?,
    ): ExtractionResult {
        val embedded = readEmbedded(artifactFile)
        val pomRoot = resolvePom(coordinates)?.let(::parseXml)
        val declared = pomRoot?.let {
            collectDeclared(
                pomRoot = it,
                depth = 0,
            )
        } ?: emptyList()

        val source = chooseSource(
            embeddedText = embedded.licenseText,
            declared = declared,
        )

        if (source == null) {
            return ExtractionResult(
                record = null,
                declared = declared,
            )
        }

        val projectMetadata = readProjectMetadata(pomRoot)
        val record = LicenseRecord(
            coordinates = coordinates,
            projectName = projectMetadata.name,
            projectUrl = projectMetadata.url,
            declared = declared,
            noticeText = embedded.noticeText,
            source = source,
        )

        return ExtractionResult(record, declared)
    }

    fun materializeText(record: LicenseRecord): String? {
        val licenseText = when (val source = record.source) {
            is LicenseSource.Embedded -> source.text
            is LicenseSource.Spdx -> spdxText(source.id)
        } ?: return null

        val notice = record.noticeText ?: return licenseText

        return joinLicenseWithNotice(licenseText, notice)
    }

    private fun joinLicenseWithNotice(
        licenseText: String,
        noticeText: String,
    ): String {
        return listOf(
            licenseText.trimEnd(),
            "",
            "-----",
            "NOTICE:",
            "",
            noticeText,
        ).joinToString(separator = "\n")
    }

    fun findCachedArtifact(coordinates: Coordinates): File? {
        val moduleDir = File(
            gradleUserHomeDir,
            "$GRADLE_MODULES_CACHE_PATH/${coordinates.group}/${coordinates.name}/${coordinates.version}",
        )

        if (!moduleDir.isDirectory) return null

        val expectedJarName = "${coordinates.name}-${coordinates.version}.$JAR_EXTENSION"
        val expectedAarName = "${coordinates.name}-${coordinates.version}.$AAR_EXTENSION"

        val hashDirs = moduleDir.listFiles { it.isDirectory } ?: return null
        val cachedFiles = hashDirs.flatMap { hashDir ->
            hashDir.listFiles()?.toList().orEmpty()
        }

        return cachedFiles.firstOrNull { it.name == expectedJarName }
            ?: cachedFiles.firstOrNull { it.name == expectedAarName }
    }

    private fun chooseSource(
        embeddedText: String?,
        declared: List<LicenseRef>,
    ): LicenseSource? {
        if (embeddedText != null) {
            return LicenseSource.Embedded(embeddedText)
        }

        for (license in declared) {
            val spdxId = SpdxPolicy.resolve(
                name = license.name,
                url = license.url,
            ) ?: continue

            return LicenseSource.Spdx(spdxId)
        }

        return null
    }

    private fun readProjectMetadata(pomRoot: Element?): ProjectMetadata {
        if (pomRoot == null) {
            return ProjectMetadata.EMPTY
        }

        val name = pomRoot.findChildText(PomTag.NAME)
        val directUrl = pomRoot.findChildText(PomTag.URL)
        val scmUrl = pomRoot.findChild(PomTag.SCM)?.findChildText(PomTag.URL)

        return ProjectMetadata(
            name = name,
            url = directUrl ?: scmUrl,
        )
    }

    private fun resolvePom(coordinates: Coordinates): File? {
        val queryResult = dependencies.createArtifactResolutionQuery()
            .forModule(coordinates.group, coordinates.name, coordinates.version)
            .withArtifacts(
                MavenModule::class.java,
                MavenPomArtifact::class.java,
            )
            .execute()

        for (component in queryResult.resolvedComponents) {
            val pomArtifacts = component.getArtifacts(MavenPomArtifact::class.java)
            for (artifact in pomArtifacts) {
                if (artifact is ResolvedArtifactResult) {
                    return artifact.file
                }
            }
        }

        return null
    }

    private tailrec fun collectDeclared(
        pomRoot: Element,
        depth: Int,
    ): List<LicenseRef> {
        if (depth > MAX_POM_PARENT_DEPTH) {
            return emptyList()
        }

        val localLicenses = readLicensesFromBlock(pomRoot)
        if (localLicenses.isNotEmpty()) {
            return localLicenses
        }

        val parentCoordinates = readParentCoordinates(pomRoot) ?: return emptyList()
        val parentPom = resolvePom(parentCoordinates) ?: return emptyList()
        val parentRoot = parseXml(parentPom) ?: return emptyList()

        return collectDeclared(
            pomRoot = parentRoot,
            depth = depth + 1,
        )
    }

    private fun readLicensesFromBlock(pomRoot: Element): List<LicenseRef> {
        val licensesBlock = pomRoot.findChild(PomTag.LICENSES) ?: return emptyList()

        return licensesBlock.findChildren(PomTag.LICENSE).mapNotNull { licenseElement ->
            val name = licenseElement.findChildText(PomTag.NAME)
            val url = licenseElement.findChildText(PomTag.URL)

            when {
                name == null && url == null -> null
                else -> LicenseRef(name.orEmpty(), url.orEmpty())
            }
        }
    }

    private fun readParentCoordinates(pomRoot: Element): Coordinates? {
        val parent = pomRoot.findChild(PomTag.PARENT) ?: return null
        val group = parent.findChildText(PomTag.GROUP_ID) ?: return null
        val name = parent.findChildText(PomTag.ARTIFACT_ID) ?: return null
        val version = parent.findChildText(PomTag.VERSION) ?: return null

        return Coordinates(group, name, version)
    }

    private fun readEmbedded(artifact: File?): EmbeddedContent {
        if (artifact == null || !artifact.isFile) {
            return EmbeddedContent.EMPTY
        }

        return try {
            ZipFile(artifact).use { zipFile ->
                val licenseCandidates = mutableListOf<String>()
                val noticeCandidates = mutableListOf<String>()

                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name

                    if (LICENSE_FILE_RE.containsMatchIn(name)) {
                        licenseCandidates += name
                    }

                    if (NOTICE_FILE_RE.containsMatchIn(name)) {
                        noticeCandidates += name
                    }
                }

                licenseCandidates.sortWith(SHALLOWEST_ENTRIES_FIRST)
                noticeCandidates.sortWith(SHALLOWEST_ENTRIES_FIRST)

                EmbeddedContent(
                    licenseText = readBestText(zipFile, licenseCandidates),
                    noticeText = readBestText(zipFile, noticeCandidates),
                )
            }
        } catch (_: Exception) {
            EmbeddedContent.EMPTY
        }
    }

    private fun readBestText(
        zipFile: ZipFile,
        candidateNames: List<String>,
    ): String? {
        for (entryName in candidateNames) {
            val zipEntry = zipFile.getEntry(entryName) ?: continue
            val text = readZipEntryText(zipFile, zipEntry) ?: continue

            if (text.length > MIN_LICENSE_LENGTH) {
                return text
            }
        }

        return null
    }

    private fun readZipEntryText(
        zipFile: ZipFile,
        entry: ZipEntry,
    ): String? {
        return try {
            zipFile.getInputStream(entry)
                .use { it.readBytes() }
                .toString(Charsets.UTF_8)
                .trim()
        } catch (_: Exception) {
            null
        }
    }

    fun bundledSpdxText(spdxId: String): String? {
        return spdxText(spdxId)
    }

    private fun spdxText(spdxId: String): String? {
        return spdxTextCache.getOrPut(spdxId) {
            val resourcePath = "$LICENSE_BUNDLE_PATH/$spdxId.txt"
            javaClass.classLoader.getResourceAsStream(resourcePath)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }
    }

    private fun parseXml(file: File): Element? {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.setFeature(DISALLOW_DOCTYPE_FEATURE, true)

        return try {
            factory.newDocumentBuilder().parse(file).documentElement
        } catch (_: Exception) {
            null
        }
    }

    private fun Element.findChild(tag: String): Element? {
        val children = childNodes ?: return null

        for (index in 0 until children.length) {
            val node = children.item(index) ?: continue
            if (node is Element && node.tagName == tag) {
                return node
            }
        }

        return null
    }

    private fun Element.findChildren(tag: String): List<Element> {
        val children = childNodes ?: return emptyList()

        val result = mutableListOf<Element>()
        for (index in 0 until children.length) {
            val node = children.item(index) ?: continue
            if (node is Element && node.tagName == tag) {
                result += node
            }
        }

        return result
    }

    private fun Element.findChildText(tag: String): String? {
        return findChild(tag)?.textContent?.trim()?.ifEmpty { null }
    }

    private data class EmbeddedContent(
        val licenseText: String?,
        val noticeText: String?,
    ) {
        companion object {
            val EMPTY = EmbeddedContent(
                licenseText = null,
                noticeText = null,
            )
        }
    }

    private data class ProjectMetadata(
        val name: String?,
        val url: String?,
    ) {
        companion object {
            val EMPTY = ProjectMetadata(
                name = null,
                url = null,
            )
        }
    }

    private object PomTag {
        const val LICENSES = "licenses"
        const val LICENSE = "license"
        const val NAME = "name"
        const val URL = "url"
        const val PARENT = "parent"
        const val GROUP_ID = "groupId"
        const val ARTIFACT_ID = "artifactId"
        const val VERSION = "version"
        const val SCM = "scm"
    }

    private companion object {
        const val MAX_POM_PARENT_DEPTH = 5
        const val MIN_LICENSE_LENGTH = 40

        const val GRADLE_MODULES_CACHE_PATH = "caches/modules-2/files-2.1"
        const val LICENSE_BUNDLE_PATH = "licenses"
        const val AAR_EXTENSION = "aar"
        const val JAR_EXTENSION = "jar"
        const val DISALLOW_DOCTYPE_FEATURE = "http://apache.org/xml/features/disallow-doctype-decl"

        val LICENSE_FILE_RE = Regex(
            pattern = "(^|/)(license|licence|copying)(\\.(txt|md))?$",
            option = RegexOption.IGNORE_CASE,
        )
        val NOTICE_FILE_RE = Regex(
            pattern = "(^|/)notice(\\.(txt|md))?$",
            option = RegexOption.IGNORE_CASE,
        )

        val SHALLOWEST_ENTRIES_FIRST: Comparator<String> = Comparator { left, right ->
            val leftDepth = left.count { it == '/' }
            val rightDepth = right.count { it == '/' }

            when {
                leftDepth != rightDepth -> leftDepth.compareTo(rightDepth)
                else -> left.length.compareTo(right.length)
            }
        }
    }
}
