package com.yonsn76.freeaiconnect.models

sealed class MarkdownBlock {
    data class Text(val content: String) : MarkdownBlock()
    data class Code(val language: String, val code: String) : MarkdownBlock()
}
