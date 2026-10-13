package camera.licenses

import java.io.Serializable

data class CopyrightOverride(
    val coordinates: String,
    val copyright: String,
) : Serializable

data class ExtraNotice(
    val name: String,
    val url: String? = null,
    val spdxId: String? = null,
    val text: String? = null,
) : Serializable

internal data class Coordinates(
    val group: String,
    val name: String,
    val version: String,
) {
    val moduleId: String
        get() = "$group:$name"

    override fun toString(): String {
        return "$group:$name:$version"
    }
}

internal sealed class LicenseSource {
    data class Embedded(val text: String) : LicenseSource()
    data class Spdx(val id: String) : LicenseSource()
}

internal data class LicenseRef(
    val name: String,
    val url: String,
)

internal data class LicenseRecord(
    val coordinates: Coordinates,
    val projectName: String?,
    val projectUrl: String?,
    val declared: List<LicenseRef>,
    val noticeText: String?,
    val source: LicenseSource,
    val overrideApplied: Boolean = false,
) {
    val spdxId: String?
        get() = (source as? LicenseSource.Spdx)?.id

    val needsOwnerCheck: Boolean
        get() = spdxId in SpdxPolicy.SHORT_LICENSES_NEED_OWNER
}

internal data class RenderedLicense(
    val record: LicenseRecord,
    val text: String,
)

internal data class NoticeBlock(
    val heading: String,
    val body: String,
)

internal data class ExtractionResult(
    val record: LicenseRecord?,
    val declared: List<LicenseRef>,
)
