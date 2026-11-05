package app.undertow.core.datastore

/**
 * Encodes the per-package estimation overrides into one preference value.
 *
 * Preferences DataStore has no map type, and a dynamic key per package would make
 * deletion and clearing awkward. The format is `pkg=seconds` joined by commas, which
 * needs no parser dependency and stays readable when inspecting the file.
 *
 * Decoding is deliberately lenient: unknown or malformed entries are dropped rather
 * than throwing, because a corrupt preference must not make the app unlaunchable.
 */
internal object EstimationOverrideCodec {

    fun encode(overrides: Map<String, Int>): String =
        overrides.entries
            .sortedBy { it.key }
            .joinToString(separator = ",") { (packageName, seconds) -> "$packageName=$seconds" }

    fun decode(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()

        return raw.split(',')
            .mapNotNull { entry ->
                val separator = entry.lastIndexOf('=')
                if (separator <= 0) return@mapNotNull null

                val packageName = entry.substring(0, separator).trim()
                val seconds = entry.substring(separator + 1).trim().toIntOrNull()
                if (packageName.isEmpty() || seconds == null || seconds <= 0) null
                else packageName to seconds
            }
            .toMap()
    }
}
