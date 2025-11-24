package com.apollographql.ijplugin.action

import com.apollographql.ijplugin.telemetry.TelemetryEvent
import com.apollographql.ijplugin.telemetry.telemetryService
import com.apollographql.ijplugin.util.logd
import com.apollographql.ijplugin.util.toJsonObject
import com.intellij.lang.jsgraphql.GraphQLFileType
import com.intellij.lang.jsgraphql.ide.config.model.GraphQLConfigEndpoint
import com.intellij.lang.jsgraphql.ide.introspection.promptForEnvVariables
import com.intellij.lang.jsgraphql.psi.GraphQLFragmentDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLFragmentSpread
import com.intellij.lang.jsgraphql.psi.GraphQLRecursiveVisitor
import com.intellij.lang.jsgraphql.ui.GraphQLUIProjectService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.parentOfType
import kotlinx.serialization.json.JsonObject
import java.util.function.Supplier
import javax.swing.Icon

abstract class AbstractOperationAction(dynamicText: Supplier<@NlsActions.ActionText String>, icon: Icon) : AnAction(dynamicText, icon) {

  override fun update(e: AnActionEvent) {
    // Only care about GraphQL files
    val isGraphQLFile = e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { file ->
      e.project != null && GraphQLFileType.isGraphQLFile(file)
    } == true
    e.presentation.isEnabled = isGraphQLFile

    // Only show the action if there's an editor (i.e. not in 'Open in' from the project view)
    e.presentation.isVisible = isGraphQLFile || e.getData(CommonDataKeys.EDITOR) != null
  }

  abstract val telemetryEvent: TelemetryEvent

  override fun actionPerformed(e: AnActionEvent) {
    logd()
    val project = e.project ?: return
    project.telemetryService.logEvent(telemetryEvent)

    // Editor will be present if the action is triggered from the editor toolbar, the main menu, the Open In popup inside the editor
    // Otherwise it will be null, and we fallback to the File (but no endpoint / variables)
    val editor = e.getData(CommonDataKeys.EDITOR)
    val psiFile = editor?.document?.let { PsiDocumentManager.getInstance(project).getPsiFile(it) }
        ?: e.getData(CommonDataKeys.VIRTUAL_FILE)?.let { virtualFile ->
          PsiManager.getInstance(project).findFile(virtualFile)
        }
        ?: return
    val contents = contentsWithReferencedFragments(psiFile)
    val endpointsModel = editor?.getUserData(GraphQLUIProjectService.GRAPH_QL_ENDPOINTS_MODEL)
    val graphQLConfigEndpoint: GraphQLConfigEndpoint? = endpointsModel?.let { promptForEnvVariables(project, it.selectedItem) }
    val selectedEndpointUrl = graphQLConfigEndpoint?.url
    val headers = graphQLConfigEndpoint?.headers?.mapValues { (_, v) -> v.toString() }
    val variablesEditor = editor?.getUserData(GraphQLUIProjectService.GRAPH_QL_VARIABLES_EDITOR)
    val variablesJson = variablesEditor?.document?.text?.let { it.ifBlank { null } }?.toJsonObject()

    doAction(
        project = project,
        contents = contents,
        endpointUrl = selectedEndpointUrl,
        headers = headers,
        variablesJson = variablesJson,
    )
  }

  abstract fun doAction(
      project: Project,
      contents: String,
      endpointUrl: String?,
      headers: Map<String, String>?,
      variablesJson: JsonObject?,
  )

  /**
   * Get contents of the file, including all referenced fragments, recursively, if they belong to a different file
   */
  protected fun contentsWithReferencedFragments(psiFile: PsiFile, fragmentDefinition: GraphQLFragmentDefinition? = null): String {
    val contents = StringBuilder(fragmentDefinition?.text ?: psiFile.text)
    val visitor = object : GraphQLRecursiveVisitor() {
      override fun visitFragmentSpread(o: GraphQLFragmentSpread) {
        super.visitFragmentSpread(o)
        val referencedFragmentDefinition = o.nameIdentifier.reference?.resolve()?.parentOfType<GraphQLFragmentDefinition>() ?: return
        if (referencedFragmentDefinition.containingFile != psiFile) {
          contents.append("\n\n# From ${referencedFragmentDefinition.containingFile.virtualFile.name}\n")
          contents.append(contentsWithReferencedFragments(referencedFragmentDefinition.containingFile, referencedFragmentDefinition))
        }
      }
    }
    if (fragmentDefinition != null) {
      visitor.visitFragmentDefinition(fragmentDefinition)
    } else {
      visitor.visitFile(psiFile)
    }
    return contents.toString()
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
