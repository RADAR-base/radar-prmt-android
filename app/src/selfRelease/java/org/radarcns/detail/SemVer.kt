package org.radarcns.detail

/**
 * Version number in [Semantic Versioning 2.0.0](http://semver.org/spec/v2.0.0.html) specification (SemVer).
 *
 * @property major major version, increment it when you make incompatible API changes.
 * @property minor minor version, increment it when you add functionality in a backwards-compatible manner.
 * @property patch patch version, increment it when you make backwards-compatible bug fixes.
 * @property preRelease pre-release version.
 * @property buildMetadata build metadata.
 */
data class SemVer(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
    val preRelease: String? = null,
    val buildMetadata: String? = null
) : Comparable<SemVer> {
    init {
        require(major >= 0) { "Major version must be a positive number" }
        require(minor >= 0) { "Minor version must be a positive number" }
        require(patch >= 0) { "Patch version must be a positive number" }
        if (preRelease != null) require(extraRegex matches preRelease) { "Pre-release version is not valid" }
        if (buildMetadata != null) require(extraRegex matches buildMetadata) { "Build metadata is not valid" }
    }

    override fun toString(): String = buildString(25) {
        append(major)
        append('.')
        append(minor)
        append('.')
        append(patch)
        if (preRelease != null) {
            append('-')
            append(preRelease)
        }
        if (buildMetadata != null) {
            append('+')
            append(buildMetadata)
        }
    }

    /**
     * Compare two SemVer objects using major, minor, patch and pre-release version as specified in SemVer specification.
     *
     * For comparing the whole SemVer object including build metadata, use [equals] instead.
     *
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
     */
    override fun compareTo(other: SemVer): Int {
        if (major > other.major) return 1
        if (major < other.major) return -1
        if (minor > other.minor) return 1
        if (minor < other.minor) return -1
        if (patch > other.patch) return 1
        if (patch < other.patch) return -1

        return preRelease.compareExtraVersionTo(other.preRelease) { part, otherPart ->
            val partInt = part.toIntOrNull()
            val otherPartInt = otherPart.toIntOrNull()
            when {
                partInt != null && otherPartInt != null -> partInt.compareTo(otherPartInt)
                // prefer numeric values
                partInt != null -> -1
                otherPartInt != null -> 1
                else -> part.compareTo(otherPart)
            }
        }
    }

    companion object {
        /**
         * Parse the version string to [SemVer] data object.
         * @param version version string.
         * @throws IllegalArgumentException if the version is not valid.
         */
        fun parse(version: String): SemVer {
            val result = requireNotNull(pattern.matchEntire(version)) { "Invalid version string [$version]" }
            return SemVer(
                major = result.groupValues[1].toInt(),
                minor = result.groupValues[2].toInt(),
                patch = result.groupValues[3].toInt(),
                preRelease = result.groupValues[4].ifEmpty { null },
                buildMetadata = result.groupValues[5].ifEmpty { null },
            )
        }

        private val extraRegex = """[\dA-z\-]+(?:\.[\dA-z\-]+)*""".toRegex()
        private val pattern = """(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([\dA-z\-]+(?:\.[\dA-z\-]+)*))?(?:\+([\dA-z\-]+(?:\.[\dA-z\-]+)*))?""".toRegex()

        private val terminal = Any()
        private fun String?.splitToSequenceWithTerminal(delimiter: Char): Sequence<Any> = if (this != null) {
            splitToSequence(delimiter) + terminal
        } else sequenceOf(terminal)

        private inline fun String?.compareExtraVersionTo(
            other: String?,
            crossinline compare: (String, String) -> Int,
        ): Int {
            if (this == other) return 0

            return splitToSequenceWithTerminal('.')
                .zip(other.splitToSequenceWithTerminal('.'))
                .map { (part, otherPart) ->
                    when {
                        part == otherPart -> 0
                        part === terminal -> -1
                        otherPart === terminal -> 1
                        else -> compare(part as String, otherPart as String)
                    }
                }
                .firstOrNull { it != 0 }
                ?: 0
        }
    }
}
