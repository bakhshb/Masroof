package com.baraa.masroof.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Typed models for the OpenAI-compatible chat-completions endpoint.
 *
 * We use kotlinx.serialization for all wire-format (de)serialization:
 *  - the **outer envelope** returned by the server
 *  - the **inner** categorization result embedded inside
 *    `choices[0].message.content`
 *
 * The handwritten JSON parser and escape-aware substring extractor used in
 * earlier versions have been removed — see [AiResponseValidator] for the
 * single source of truth.
 */
object AiJson {

    /** Lenient-but-strict JSON: ignore unknown keys, reject malformed input. */
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = false
    }

    /** Outer OpenAI-compatible chat-completions envelope. */
    @Serializable
    data class ChatCompletionsResponse(
        @SerialName("id") val id: String? = null,
        @SerialName("object") val objectType: String? = null,
        @SerialName("model") val model: String? = null,
        @SerialName("choices") val choices: List<Choice> = emptyList(),
        @SerialName("usage") val usage: Usage? = null,
        @SerialName("error") val error: ProviderError? = null,
    )

    @Serializable
    data class Choice(
        @SerialName("index") val index: Int = 0,
        @SerialName("message") val message: Message? = null,
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    data class Message(
        @SerialName("role") val role: String? = null,
        @SerialName("content") val content: String? = null,
    )

    @Serializable
    data class Usage(
        @SerialName("prompt_tokens") val promptTokens: Int = 0,
        @SerialName("completion_tokens") val completionTokens: Int = 0,
        @SerialName("total_tokens") val totalTokens: Int = 0,
    )

    @Serializable
    data class ProviderError(
        @SerialName("message") val message: String,
        @SerialName("type") val type: String? = null,
        @SerialName("code") val code: String? = null,
    )

    /** Inner categorization payload. */
    @Serializable
    data class CategorizationPayload(
        @SerialName("category_id") val categoryId: Long? = null,
        @SerialName("category_name") val categoryName: String? = null,
        @SerialName("normalized_merchant_name") val normalizedMerchantName: String? = null,
        @SerialName("confidence") val confidence: Int? = null,
        @SerialName("explanation") val explanation: String? = null,
    )

    /** Outgoing request body. */
    @Serializable
    data class ChatCompletionsRequest(
        @SerialName("model") val model: String,
        @SerialName("temperature") val temperature: Double = 0.0,
        @SerialName("response_format") val responseFormat: ResponseFormat? = ResponseFormat(type = "json_object"),
        @SerialName("messages") val messages: List<ChatMessage>,
    )

    @Serializable
    data class ResponseFormat(
        @SerialName("type") val type: String,
    )

    @Serializable
    data class ChatMessage(
        @SerialName("role") val role: String,
        @SerialName("content") val content: String,
    )
}