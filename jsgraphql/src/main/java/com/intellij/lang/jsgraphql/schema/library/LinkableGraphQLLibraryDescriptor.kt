package com.intellij.lang.jsgraphql.schema.library

import com.intellij.openapi.project.Project

class LinkableGraphQLLibraryDescriptor(
    identifier: String,
    presentableText: String,
) : GraphQLLibraryDescriptor(identifier, presentableText) {
  override fun isEnabled(project: Project): Boolean {
    return project.getUserData(LinkedGraphQLLibrarySetKey)?.isLinked(identifier) == true
  }
}
