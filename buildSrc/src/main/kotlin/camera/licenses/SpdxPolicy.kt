package camera.licenses

import org.gradle.api.GradleException

internal object SpdxPolicy {

    val SHORT_LICENSES_NEED_OWNER = setOf(
        "BSD-2-Clause",
    )

    private val HARD_FAIL_PREFIXES = listOf(
        "GPL-",
        "AGPL-",
        "EUPL-",
    )

    private val SOURCE_AVAILABILITY = setOf(
        "LGPL-2.1-only",
        "LGPL-2.1-or-later",
        "LGPL-3.0-only",
        "LGPL-3.0-or-later",
        "MPL-1.1",
        "MPL-2.0",
        "EPL-1.0",
        "EPL-2.0",
        "CDDL-1.0",
        "CDDL-1.1",
    )

    private val NAME_MAP = listOf(
        "apache" to "Apache-2.0",
        "simplified bsd" to "BSD-2-Clause",
        "bsd 2" to "BSD-2-Clause",
        "bsd-2" to "BSD-2-Clause",
        "2-clause" to "BSD-2-Clause",
        "eclipse public license v. 2" to "EPL-2.0",
        "eclipse public license v2" to "EPL-2.0",
        "eclipse public license - v 2" to "EPL-2.0",
        "eclipse public license 2" to "EPL-2.0",
        "eclipse public license" to "EPL-1.0",
        "mozilla public license 2" to "MPL-2.0",
        "mpl 2" to "MPL-2.0",
        "mozilla public license 1.1" to "MPL-1.1",
        "mpl 1.1" to "MPL-1.1",
        "common development and distribution" to "CDDL-1.0",
        "cddl" to "CDDL-1.0",
        "gnu lesser general public license v3" to "LGPL-3.0-only",
        "gnu lesser" to "LGPL-2.1-only",
        "gnu affero" to "AGPL-3.0-only",
        "gnu general public license v3" to "GPL-3.0-only",
        "gnu general public license v2" to "GPL-2.0-only",
        "gnu general public license" to "GPL-2.0-only",
        "european union public licence" to "EUPL-1.2",
        "european union public license" to "EUPL-1.2",
        "eupl" to "EUPL-1.2",
    )

    private val URL_MAP = listOf(
        "apache.org/licenses/license-2.0" to "Apache-2.0",
        "opensource.org/licenses/bsd-2" to "BSD-2-Clause",
        "eclipse.org/legal/epl-2.0" to "EPL-2.0",
        "eclipse.org/legal/epl-v10" to "EPL-1.0",
        "mozilla.org/en-us/mpl/2.0" to "MPL-2.0",
        "mozilla.org/mpl/2.0" to "MPL-2.0",
        "eupl" to "EUPL-1.2",
    )

    fun resolve(
        name: String,
        url: String,
    ): String? {
        val lowerName = name.lowercase()
        for ((needle, id) in NAME_MAP) {
            if (needle in lowerName) {
                return id
            }
        }

        val lowerUrl = url.lowercase()
        for ((needle, id) in URL_MAP) {
            if (needle in lowerUrl) {
                return id
            }
        }

        if (name.isEmpty()) return null

        val looksLikeSpdxId = SPDX_ID_RE.matches(name) && HAS_DIGIT_RE.containsMatchIn(name)
        return when {
            looksLikeSpdxId -> name
            else -> null
        }
    }

    fun checkAllowed(record: LicenseRecord) {
        for (spdx in policedSpdxIds(record)) {
            val isStrongCopyleft = HARD_FAIL_PREFIXES.any(spdx::startsWith)
            if (isStrongCopyleft) {
                throw GradleException(
                    "Strong copyleft ($spdx) detected in ${record.coordinates} — incompatible " +
                        "with closed distribution. Remove this dependency.",
                )
            }

            val isWeakCopyleft = spdx in SOURCE_AVAILABILITY
            if (isWeakCopyleft) {
                throw GradleException(
                    "Weak copyleft $spdx detected in ${record.coordinates} — source-availability " +
                        "handling is not supported by this plugin. Add explicit handling.",
                )
            }
        }
    }

    private fun policedSpdxIds(record: LicenseRecord): Set<String> {
        val chosenId = listOfNotNull(record.spdxId)
        val declaredIds = record.declared.mapNotNull {
            resolve(
                name = it.name,
                url = it.url,
            )
        }

        return (chosenId + declaredIds).toSet()
    }

    fun checkOwner(record: LicenseRecord) {
        if (record.needsOwnerCheck && !record.overrideApplied) {
            throw GradleException(
                "Short license ${record.spdxId} for ${record.coordinates} requires an " +
                    "explicit copyright holder. Add it via copyrightOverrides.add(" +
                    "CopyrightOverride(\"${record.coordinates.moduleId}\", \"Copyright ...\")) " +
                    "in the generateLicenses task configuration.",
            )
        }
    }

    private val SPDX_ID_RE = Regex("[A-Za-z0-9.\\-]+")
    private val HAS_DIGIT_RE = Regex("\\d")
}
