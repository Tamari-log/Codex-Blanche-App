package com.tamarilog.codexblanche.data

import kotlinx.serialization.json.Json

internal val CodexJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}
