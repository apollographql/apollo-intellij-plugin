package com.apollographql.ijplugin.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

fun JsonElement.toAny(): Any? = when (this) {
  is JsonObject -> this.mapValues { it.value.toAny() }
  is JsonArray -> this.map { it.toAny() }
  is JsonPrimitive -> {
    when {
      isString -> this.content
      this is JsonNull -> null
      else -> booleanOrNull ?: intOrNull ?: longOrNull ?: doubleOrNull ?: error("cannot decode $this")
    }
  }
}

fun String.toJsonObject(): JsonObject? {
  return runCatching { Json.parseToJsonElement(this) as? JsonObject }.getOrNull()
}

fun JsonObject.toJsonString(): String = Json.encodeToString(this)

fun Map<String, JsonElement>.toJsonString(): String = JsonObject(this).toJsonString()

fun String.toMapOfAny(): Map<String, Any?> {
  return toJsonObject()!!.mapValues { it.value.toAny() }
}
