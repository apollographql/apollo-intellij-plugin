@file:OptIn(ApolloInternal::class, ApolloExperimental::class)

package com.apollographql.ijplugin.util

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.annotations.ApolloInternal
import com.apollographql.apollo.ast.GQLDefinition
import com.apollographql.apollo.ast.GQLDirectiveDefinition
import com.apollographql.apollo.ast.GQLNamed
import com.apollographql.apollo.ast.rawType
import com.apollographql.ijplugin.graphql.importUrlName
import com.intellij.lang.jsgraphql.psi.GraphQLArrayValue
import com.intellij.lang.jsgraphql.psi.GraphQLDirective
import com.intellij.lang.jsgraphql.psi.GraphQLElement
import com.intellij.lang.jsgraphql.psi.GraphQLElementFactory
import com.intellij.lang.jsgraphql.psi.GraphQLFile
import com.intellij.lang.jsgraphql.psi.GraphQLNamedElement
import com.intellij.lang.jsgraphql.psi.GraphQLSchemaDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLSchemaExtension
import com.intellij.lang.jsgraphql.psi.GraphQLStringValue
import com.intellij.openapi.project.Project


fun List<GQLDefinition>.directives(): List<GQLDirectiveDefinition> {
  return filterIsInstance<GQLDirectiveDefinition>()
}

fun GraphQLNamedElement.isImported(): Boolean {
  for (schemaFile in schemaFiles()) {
    if (schemaFile.hasImportFor(this.name!!, this is GraphQLDirective)) return true
  }
  return false
}

fun isImported(element: GraphQLElement, enumName: String): Boolean {
  for (schemaFile in element.schemaFiles()) {
    if (schemaFile.hasImportFor(enumName, false)) return true
  }
  return false
}

fun GraphQLFile.linkDirectives(): List<GraphQLDirective> {
  val schemaDirectives = typeDefinitions.filterIsInstance<GraphQLSchemaExtension>().flatMap { it.directives } +
      typeDefinitions.filterIsInstance<GraphQLSchemaDefinition>().flatMap { it.directives }
  return schemaDirectives.filter { directive ->
    directive.name == "link"
  }
}

fun GraphQLFile.linkDirectives(definitionsUrl: String): List<GraphQLDirective> {
  return linkDirectives().filter { directive ->
    directive.arguments?.argumentList.orEmpty().any { arg -> arg.name == "url" && arg.value?.text == definitionsUrl.quoted() }
  }
}

private fun GraphQLFile.linkedDefinitions(): Set<String> = linkDirectives().mapNotNull { directive ->
  directive.arguments?.argumentList?.firstOrNull { it.name == "url" }?.value?.text?.unquoted()
}.toSet()

fun GraphQLElement.linkedDefinitions(): Set<String> = schemaFiles().flatMap { it.linkedDefinitions() }.toSet()

private fun GraphQLFile.hasImportFor(name: String, isDirective: Boolean): Boolean {
  for (directive in linkDirectives()) {
    val importArgValue = directive.argumentValue("import") as? GraphQLArrayValue
    if (importArgValue == null) {
      // Default import is everything - see https://specs.apollo.dev/link/v1.0/#@link.url
      val asArgValue = directive.argumentValue("as") as? GraphQLStringValue
      // Default prefix is the name part of the url
      val prefix = (asArgValue?.text?.unquoted() ?: directive.importUrlName() ?: "") + "__"
      if (name.startsWith(prefix)) return true
    } else {
      if (importArgValue.valueList.any { it.text == name.nameForImport(isDirective).quoted() }) {
        return true
      }
    }
  }
  return false
}

private fun GraphQLDirective.importUrlName(): String? {
  return (argumentValue("url") as? GraphQLStringValue)?.valueAsString?.importUrlName()
}

private val String.nameWithoutPrefix get() = substringAfter("__")

val GraphQLNamedElement.nameWithoutPrefix get() = name!!.nameWithoutPrefix

fun String.nameForImport(isDirective: Boolean) = "${if (isDirective) "@" else ""}${this.nameWithoutPrefix}"

val GraphQLNamedElement.nameForImport get() = if (this is GraphQLDirective) "@$nameWithoutPrefix" else nameWithoutPrefix


fun createLinkDirectiveSchemaExtension(
    project: Project,
    importedNames: Set<String>,
    definitions: List<GQLDefinition>,
    definitionsUrl: String,
): GraphQLSchemaExtension {
  // If any of the imported name is a directive, add its argument types to the import list
  val knownDefinitionNames = definitions.filterIsInstance<GQLNamed>().map { it.name }
  val additionalNames = importedNames.flatMap { importedName ->
    definitions.directives().firstOrNull { "@${it.name}" == importedName }
        ?.arguments
        ?.map { it.type.rawType().name }
        ?.filter { it in knownDefinitionNames }.orEmpty()
  }.toSet()

  return GraphQLElementFactory.createFile(
      project,
      """
        extend schema
        @link(
          url: "$definitionsUrl",
          import: [${(importedNames + additionalNames).joinToString { it.quoted() }}]
        )
      """.trimIndent()
  )
      .findChildrenOfType<GraphQLSchemaExtension>().single()
}

fun createLinkDirective(
    project: Project,
    importedNames: Set<String>,
    definitions: List<GQLDefinition>,
    definitionsUrl: String,
): GraphQLDirective {
  return createLinkDirectiveSchemaExtension(project, importedNames, definitions, definitionsUrl).directives.last()
}
