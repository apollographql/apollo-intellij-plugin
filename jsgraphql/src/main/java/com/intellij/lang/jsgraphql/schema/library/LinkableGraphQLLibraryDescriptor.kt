package com.intellij.lang.jsgraphql.schema.library

import com.intellij.openapi.project.Project

class LinkableGraphQLLibraryDescriptor(
    identifier: String,
    private val presentableText: String,
) : GraphQLLibraryDescriptor(identifier) {
  override fun isEnabled(project: Project): Boolean {
    return project.getUserData(LinkedGraphQLLibrarySetKey)?.isLinked(myIdentifier) == true
  }

  override fun getPresentableText(): String {
    return presentableText
  }
}
