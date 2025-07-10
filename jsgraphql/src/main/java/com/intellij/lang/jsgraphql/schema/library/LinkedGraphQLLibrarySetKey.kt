package com.intellij.lang.jsgraphql.schema.library

import com.intellij.openapi.util.Key

object LinkedGraphQLLibrarySetKey : Key<LinkedGraphQLLibrarySet>(LinkedGraphQLLibrarySetKey::class.java.name)

data class LinkedGraphQLLibrarySet(private val enabledLibraries: Set<String>) {
  fun isLinked(libraryName: String): Boolean {
    return enabledLibraries.contains(libraryName)
  }
}
