package com.apollographql.ijplugin.lsp

import com.apollographql.ijplugin.ApolloBundle
import com.apollographql.ijplugin.settings.appSettingsState
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.lang.jsgraphql.psi.GraphQLDirective
import com.intellij.lang.jsgraphql.psi.GraphQLFieldDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLObjectTypeDefinition
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Provides gutter icons for running connectors (instances of @connect directive) in LSP mode.
 * Only active when LSP mode is enabled and not in Kotlin/Apollo Kotlin mode.
 */
class ConnectorRunLineMarkerProvider : LineMarkerProvider {

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    // Add simple debug logging
    println("ConnectorRunLineMarkerProvider called for ${element.javaClass.simpleName} in file ${element.containingFile?.name}")
    
    // Only operate in LSP mode - restore this check once debugging is complete
    if (!appSettingsState.lspModeEnabled) {
      println("ConnectorRunLineMarkerProvider: LSP mode disabled, skipping")
      return null
    }

    // Check if this element is a GraphQL directive with name "connect"
    val directive = element as? GraphQLDirective ?: return null
    val directiveName = directive.name ?: return null
    
    println("ConnectorRunLineMarkerProvider: Found directive with name: '$directiveName'")
    
    if (directiveName != "connect") {
      return null
    }

    println("ConnectorRunLineMarkerProvider: Found @connect directive, creating marker")

    // Determine the connector ID based on the directive's location
    val connectorId = generateConnectorId(directive)

    println("ConnectorRunLineMarkerProvider: Generated connector ID: $connectorId")

    // Create simple line marker info
    return LineMarkerInfo(
      element,
      element.textRange,
      AllIcons.Actions.Execute,
      { "Run Connector" },
      { _, _ ->
        println("Connector marker clicked for connector: $connectorId")
        val project = element.project
        ConnectorsToolWindow.showToolWindow(project, connectorId)
      },
      GutterIconRenderer.Alignment.CENTER,
      { "Run Connector" }
    )
  }

  private fun generateConnectorId(directive: GraphQLDirective): String {
    // Find the parent type definition
    val typeDefinition = PsiTreeUtil.getParentOfType(directive, GraphQLObjectTypeDefinition::class.java)
    val typeName = typeDefinition?.typeNameDefinition?.name ?: "UnknownType"

    // Check if the directive is on a field
    val fieldDefinition = PsiTreeUtil.getParentOfType(directive, GraphQLFieldDefinition::class.java)

    return if (fieldDefinition != null) {
      // Field-level connector
      val fieldName = fieldDefinition.nameIdentifier.text
      "$typeName.$fieldName"
    } else {
      // Type-level connector
      typeName
    }
  }
}
