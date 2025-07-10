package com.intellij.lang.jsgraphql.schema.library

import com.intellij.openapi.project.Project

open class GraphQLLibraryDescriptor(
    val identifier: String,
    val presentableText: String,
) {
  open fun isEnabled(project: Project): Boolean {
    return true
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GraphQLLibraryDescriptor) return false
    if (identifier != other.identifier) return false
    return true
  }

  override fun hashCode(): Int {
    return identifier.hashCode()
  }

  override fun toString(): String {
    return "GraphQLLibraryDescriptor(identifier='$identifier', presentableText='$presentableText')"
  }
}
