package camera.licenses

import groovy.json.JsonOutput

internal object JsonRenderer {

    fun render(
        licenses: List<RenderedLicense>,
        extras: List<Pair<ExtraNotice, NoticeBlock>>,
    ): String {
        val entries = (licenses.map(::libraryEntry) + extras.map(::extraEntry))
            .sortedWith(BY_NAME)
        val keysByText = licenseKeys(entries)

        val json = mapOf(
            "libraries" to entries.map { entry ->
                entry.toJson(licenseKey = keysByText.getValue(entry.text))
            },
            "licenses" to keysByText.entries
                .associate { (text, key) -> key to text }
                .toSortedMap(),
        )

        return JsonOutput.prettyPrint(JsonOutput.toJson(json)) + "\n"
    }

    private fun licenseKeys(entries: List<Entry>): Map<String, String> {
        val keysByText = linkedMapOf<String, String>()
        val textCountByLicenseId = mutableMapOf<String, Int>()

        entries
            .sortedByDescending { it.isBundledText }
            .forEach { entry ->
                keysByText.getOrPut(entry.text) {
                    val count = textCountByLicenseId.getOrDefault(entry.licenseId, 0) + 1
                    textCountByLicenseId[entry.licenseId] = count

                    when (count) {
                        1 -> entry.licenseId
                        else -> "${entry.licenseId} ($count)"
                    }
                }
            }

        return keysByText
    }

    private fun libraryEntry(rendered: RenderedLicense): Entry {
        val record = rendered.record
        val declaredSpdxId = record.declared.firstNotNullOfOrNull { license ->
            SpdxPolicy.resolve(
                name = license.name,
                url = license.url,
            )
        }

        return Entry(
            name = record.projectName?.trim()?.ifEmpty { null } ?: record.coordinates.name,
            coordinates = record.coordinates.toString(),
            url = record.projectUrl,
            licenseId = record.spdxId ?: declaredSpdxId ?: record.coordinates.moduleId,
            text = rendered.text,
            isBundledText = record.spdxId != null &&
                record.noticeText == null &&
                !record.overrideApplied,
        )
    }

    private fun extraEntry(extra: Pair<ExtraNotice, NoticeBlock>): Entry {
        val (notice, block) = extra

        return Entry(
            name = notice.name,
            coordinates = null,
            url = null,
            licenseId = notice.spdxId ?: notice.name,
            text = block.body,
            isBundledText = false,
        )
    }

    private data class Entry(
        val name: String,
        val coordinates: String?,
        val url: String?,
        val licenseId: String,
        val text: String,
        val isBundledText: Boolean,
    ) {
        fun toJson(licenseKey: String): Map<String, String> {
            return buildMap {
                put("name", name)
                coordinates?.let { put("coordinates", it) }
                url?.let { put("url", it) }
                put("license", licenseKey)
            }
        }
    }

    private val BY_NAME: Comparator<Entry> = Comparator { left, right ->
        left.name.compareTo(right.name, ignoreCase = true)
            .takeIf { it != 0 }
            ?: left.coordinates.orEmpty().compareTo(right.coordinates.orEmpty())
    }
}
