package dev.tomng.alarme.feature.settings.optimize

/** The vendor skins whose battery managers kill alarm apps; each gets its own how-to card. */
enum class OemGuide {
    XIAOMI,
    HUAWEI,
    HONOR,
    OPPO,
    VIVO,
    ONEPLUS,
    GENERIC,
}

/**
 * Vendor detection from `Build.MANUFACTURER` plus `Build.BRAND`: several Chinese OEMs ship the same
 * skin under different brands (Redmi/POCO under Xiaomi, realme under OPPO, iQOO under vivo), and
 * the brand is often the more specific of the two.
 */
object OemDetector {

    fun detect(manufacturer: String, brand: String): OemGuide {
        val keys = listOf(manufacturer, brand).map { it.trim().lowercase() }
        return when {
            keys.any { it in XIAOMI_KEYS } -> OemGuide.XIAOMI
            keys.any { it in HUAWEI_KEYS } -> OemGuide.HUAWEI
            keys.any { it in HONOR_KEYS } -> OemGuide.HONOR
            keys.any { it in ONEPLUS_KEYS } -> OemGuide.ONEPLUS
            keys.any { it in OPPO_KEYS } -> OemGuide.OPPO
            keys.any { it in VIVO_KEYS } -> OemGuide.VIVO
            else -> OemGuide.GENERIC
        }
    }

    private val XIAOMI_KEYS = setOf("xiaomi", "redmi", "poco")
    private val HUAWEI_KEYS = setOf("huawei")
    private val HONOR_KEYS = setOf("honor")
    private val OPPO_KEYS = setOf("oppo", "realme")
    private val VIVO_KEYS = setOf("vivo", "iqoo")
    private val ONEPLUS_KEYS = setOf("oneplus")
}
