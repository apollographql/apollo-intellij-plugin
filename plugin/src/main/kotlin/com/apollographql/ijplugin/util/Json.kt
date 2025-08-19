package com.apollographql.ijplugin.util

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
