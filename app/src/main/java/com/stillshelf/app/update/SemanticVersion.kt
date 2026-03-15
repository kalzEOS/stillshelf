package com.stillshelf.app.update

internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<PreReleaseToken>,
    val raw: String
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        val thisPre = preRelease
        val otherPre = other.preRelease
        if (thisPre.isEmpty() && otherPre.isEmpty()) return 0
        if (thisPre.isEmpty()) return 1
        if (otherPre.isEmpty()) return -1
        val size = maxOf(thisPre.size, otherPre.size)
        for (index in 0 until size) {
            val left = thisPre.getOrNull(index)
            val right = otherPre.getOrNull(index)
            if (left == null) return -1
            if (right == null) return 1
            val compare = left.compareTo(right)
            if (compare != 0) return compare
        }
        return 0
    }

    companion object {
        // Accept both x.y and x.y.z so release tags like v0.2 still parse as 0.2.0.
        private val VERSION_REGEX = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?$""")

        fun parse(rawValue: String?): SemanticVersion? {
            val raw = rawValue.orEmpty().trim()
            if (raw.isBlank()) return null
            val match = VERSION_REGEX.matchEntire(raw) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].ifBlank { "0" }.toIntOrNull() ?: return null
            val preReleaseRaw = match.groupValues.getOrNull(4).orEmpty()
            val preRelease = if (preReleaseRaw.isBlank()) {
                emptyList()
            } else {
                preReleaseRaw
                    .split(".")
                    .mapNotNull { part ->
                        val token = part.trim()
                        if (token.isBlank()) null else PreReleaseToken.parse(token)
                    }
            }
            return SemanticVersion(
                major = major,
                minor = minor,
                patch = patch,
                preRelease = preRelease,
                raw = raw.removePrefix("v")
            )
        }
    }
}

internal data class PreReleaseToken(
    val numericValue: Int?,
    val textValue: String
) : Comparable<PreReleaseToken> {
    override fun compareTo(other: PreReleaseToken): Int {
        val leftNumber = numericValue
        val rightNumber = other.numericValue
        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> -1
            rightNumber != null -> 1
            else -> textValue.compareTo(other.textValue)
        }
    }

    companion object {
        fun parse(raw: String): PreReleaseToken {
            val numeric = raw.toIntOrNull()
            return PreReleaseToken(
                numericValue = numeric,
                textValue = raw.lowercase()
            )
        }
    }
}
