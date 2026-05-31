package com.yonsn76.freeaiconnect.providers

class CustomProvider(
    override val name: String,
    val endpoint: String,
    override val models: List<String>
) : OpenAICompatibleProvider() {
    override val baseUrl: String get() = endpoint
    override val requiresApiKey: Boolean = true
}
