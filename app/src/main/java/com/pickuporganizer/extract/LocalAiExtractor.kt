package com.pickuporganizer.extract

interface LocalAiExtractor {
    fun extract(normalizedText: String, packageName: String): ExtractedPickup?
}

object NoOpLocalAiExtractor : LocalAiExtractor {
    override fun extract(normalizedText: String, packageName: String): ExtractedPickup? = null
}
