package app.undertow.core.model

/**
 * An app the user has asked to measure.
 *
 * [packageName] is the identity; [displayName] is a label resolved on-device and may
 * change between releases of the target app, so it is never used as a key.
 */
data class TrackedApp(
    val packageName: String,
    val displayName: String,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
    }
}
