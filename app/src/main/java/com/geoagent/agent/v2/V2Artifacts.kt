package com.geoagent.agent.v2

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

private val artifactGson = Gson()

fun v2Artifact(type: String, values: Map<String, Any?>): String =
    "$type:${artifactGson.toJson(values)}"

fun String.v2ArtifactType(): String? = substringBefore(":", missingDelimiterValue = "")
    .takeIf { it.isNotBlank() }

fun String.v2ArtifactJson(): JsonObject? {
    val json = substringAfter(":", missingDelimiterValue = "").takeIf { it.isNotBlank() } ?: return null
    return runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
}

fun String.v2ArtifactString(key: String): String =
    v2ArtifactJson()
        ?.get(key)
        ?.takeIf { !it.isJsonNull }
        ?.asString
        .orEmpty()
