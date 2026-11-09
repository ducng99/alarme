package dev.tomng.alarme.feature.missions.engine

/** The barcode rule: exact equality on the raw scanned value — never a containment match. */
object BarcodeMatcher {

    fun matches(registered: String, scanned: String): Boolean =
        registered.isNotEmpty() && scanned == registered
}
