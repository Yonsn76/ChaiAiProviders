package com.yonsn76.freeaiconnect.providers

class OpenAIProvider : OpenAICompatibleProvider() {
    override val name = "OpenAI"
    override val baseUrl = "https://api.openai.com/v1/chat/completions"
    override val models = listOf(
        "gpt-4o",
        "gpt-4o-mini",
        "gpt-4.1",
        "gpt-4.1-mini",
        "o4-mini"
    )
}
