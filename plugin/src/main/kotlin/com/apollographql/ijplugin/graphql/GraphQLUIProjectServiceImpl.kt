package com.apollographql.ijplugin.graphql

import com.apollographql.ijplugin.studio.sandbox.OpenInSandboxAction
import com.intellij.lang.jsgraphql.ui.AbstractGraphQLUIProjectService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

class GraphQLUIProjectServiceImpl(project: Project) : AbstractGraphQLUIProjectService(project) {
  override fun stripClientDirectives(editor: Editor, query: String): String {
    return stripApolloClientDirectives(editor, query)
  }

  override fun getOpenInSandboxAction(): AnAction {
    return OpenInSandboxAction()
  }
}
