package com.pickuporganizer.extract

data class ExtractedPickup(
    val appSource: String,
    val station: String?,
    val pickupCode: String?,
    val confidence: Float,
    val normalizedText: String
) {
    val hasPickupCode: Boolean = !pickupCode.isNullOrBlank()
}

object PickupExtractor {
    private val whitespace = Regex("""\s+""")
    private val punctuation = mapOf('：' to ':', '，' to ',', '。' to '.', '；' to ';', '（' to '(', '）' to ')')

    private val codePatterns = listOf(
        Regex("""(?:提货号|取件码|取货码|提货码|领取码|凭码|编号|货架号)[:\s]*(?:为|是)?[:\s]*([A-Za-z0-9]{1,8}[-－—][A-Za-z0-9]{2,10}|[A-Za-z0-9]{4,12})"""),
        Regex("""凭(?:提货号|取件码|取货码|提货码|码)?[:\s]*([A-Za-z0-9]{1,8}[-－—][A-Za-z0-9]{2,10})"""),
        Regex("""(?:码|号)[:\s]*([A-Za-z0-9]{1,8}[-－—][A-Za-z0-9]{2,10})""")
    )

    private val stationAfterArrivalPatterns = listOf(
        Regex("""(?:已到|到达|送至|放至|放在|存放在|存放至)([^,.;，。；请]{3,50}(?:驿站|快递柜|代收点|京东点|丰巢|妈妈驿站)[^,.;，。；请]{0,20})"""),
        Regex("""([^,.;，。；请]{3,50}(?:菜鸟驿站|驿站|快递柜|代收点|京东点|丰巢|妈妈驿站)[^,.;，。；请]{0,20})""")
    )
    private val stationTailPattern = Regex("""(?:请)?(?:凭|取件码|取货码|提货码|提货号|领取码|凭码|编号|货架号).*$""")

    fun extract(
        rawText: String,
        packageName: String,
        appName: String? = null
    ): ExtractedPickup {
        val normalized = normalize(rawText)
        val appSource = SourceResolver.resolve(packageName, normalized, appName)
        return ExtractedPickup(
            appSource = appSource,
            station = extractStation(normalized),
            pickupCode = extractPickupCode(normalized),
            confidence = 0f,
            normalizedText = normalized
        ).withRuleConfidence()
    }

    fun normalize(text: String): String =
        text.map { punctuation[it] ?: it }
            .joinToString("")
            .replace(whitespace, " ")
            .trim()

    private fun extractPickupCode(text: String): String? =
        codePatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groups?.get(1)?.value
        }?.replace('－', '-')
            ?.replace('—', '-')
            ?.trim()

    private fun extractStation(text: String): String? =
        stationAfterArrivalPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groups?.get(1)?.value
        }?.replace(stationTailPattern, "")
            ?.trim(' ', ',', '.', ';', ':')
            ?.takeIf { it.length >= 3 }

    private fun ExtractedPickup.withRuleConfidence(): ExtractedPickup {
        var score = 0.15f
        if (!pickupCode.isNullOrBlank()) score += 0.55f
        if (!station.isNullOrBlank()) score += 0.2f
        if (normalizedText.contains("快递") || normalizedText.contains("包裹")) score += 0.1f
        return copy(confidence = score.coerceAtMost(0.98f))
    }
}
