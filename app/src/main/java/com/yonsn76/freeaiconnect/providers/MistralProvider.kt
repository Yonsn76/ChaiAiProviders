package com.yonsn76.freeaiconnect.providers

class MistralProvider : OpenAICompatibleProvider() {
    override val name = "Mistral"
    override val baseUrl = "https://api.mistral.ai/v1/chat/completions"
    override val models = listOf(
        "mistral-large-latest",
        "mistral-medium-latest",
        "mistral-small-latest",
        "codestral-latest"
    )
}
