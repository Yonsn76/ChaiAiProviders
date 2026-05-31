package com.yonsn76.freeaiconnect.providers

class OpenRouterProvider : OpenAICompatibleProvider() {
    override val name = "OpenRouter"
    override val baseUrl = "https://openrouter.ai/api/v1/chat/completions"
    override val models = listOf(
        "google/gemini-2.5-flash",
        "anthropic/claude-sonnet-4",
        "meta-llama/llama-4-maverick",
        "deepseek/deepseek-r1",
        "openai/gpt-4o",
        "mistralai/mistral-large-latest"
    )
}
