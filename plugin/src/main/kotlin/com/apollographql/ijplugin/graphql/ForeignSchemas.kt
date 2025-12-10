@file:OptIn(ApolloExperimental::class)

package com.apollographql.ijplugin.graphql

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.ast.ForeignSchema
import com.apollographql.apollo.ast.GQLDefinition
import com.apollographql.apollo.ast.parseAsGQLDocument
import com.apollographql.ijplugin.util.directives
import com.intellij.lang.jsgraphql.schema.library.GraphQLLibraryManager
import com.intellij.lang.jsgraphql.schema.library.GraphQLLibraryTypes
import okio.buffer
import okio.source

object ForeignSchemas {
  val foreignSchemas = listOf(
      foreignSchema(GraphQLLibraryTypes.CACHE_V0_4.identifier),
      foreignSchema(GraphQLLibraryTypes.CACHE_V0_3.identifier),
      foreignSchema(GraphQLLibraryTypes.CACHE_V0_1.identifier),

      foreignSchema(GraphQLLibraryTypes.KOTLIN_LABS_V0_5.identifier),
      foreignSchema(GraphQLLibraryTypes.KOTLIN_LABS_V0_4.identifier),
      foreignSchema(GraphQLLibraryTypes.KOTLIN_LABS_V0_3.identifier),

      foreignSchema(GraphQLLibraryTypes.NULLABILITY_V0_4.identifier),

      foreignSchema(GraphQLLibraryTypes.FAKES_V0_0.identifier),
  )

  val lastestKotlinLabsForeignSchema = foreignSchema(GraphQLLibraryTypes.KOTLIN_LABS_V0_5.identifier)

  fun getForeignSchemaForDirective(name: String): ForeignSchema? {
    return foreignSchemas.firstOrNull { foreignSchema ->
      foreignSchema.definitions.directives().any { it.name == name.withoutPrefix() }
    }
  }

  private fun String.withoutPrefix(): String = substringAfter("__")

  private fun foreignSchema(name: String, version: String): ForeignSchema =
    ForeignSchema(name, version, definitions("$name-$version.graphqls"))

  private fun foreignSchema(url: String): ForeignSchema {
    require(url.startsWith("https://specs.apollo.dev/")) { "Invalid foreign schema url: $url" }
    val parts = url.removePrefix("https://specs.apollo.dev/").split("/")
    require(parts.size == 2) { "Invalid foreign schema url: $url" }
    return foreignSchema(name = parts[0], version = parts[1])
  }

  private fun definitions(resource: String): List<GQLDefinition> =
    javaClass.getClassLoader().getResourceAsStream("${GraphQLLibraryManager.DEFINITIONS_RESOURCE_DIR}/$resource")!!.source()
        .buffer().parseAsGQLDocument().getOrThrow().definitions
}

val ForeignSchema.url: String get() = "https://specs.apollo.dev/$name/$version"

fun String.importUrlName(): String {
  return removeSuffix("/").substringAfter("://").split('/').last { !it.startsWith("v") }
}
