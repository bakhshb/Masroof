package com.baraa.masroof.ai

/**
 * Where categorization runs. Remote keeps today's OpenAI-compatible HTTP
 * path. On-device uses a local MediaPipe / LiteRT model file — no SMS or
 * merchant payload leaves the phone.
 */
enum class AiDeploymentMode {
    REMOTE,
    ON_DEVICE,
}
