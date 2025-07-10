@file:OptIn(ApolloExperimental::class)

package com.apollographql.ijplugin.graphql

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.ast.ForeignSchema
import com.apollographql.apollo.ast.GQLDefinition
import com.apollographql.apollo.ast.parseAsGQLDocument
import com.apollographql.ijplugin.util.directives
import com.intellij.lang.jsgraphql.schema.library.GraphQLLibraryManager
import okio.buffer
import okio.source

object ForeignSchemas {
  val foreignSchemas = listOf(
      foreignSchema("kotlin_labs", "v0.5"),
      foreignSchema("kotlin_labs", "v0.4"),
      foreignSchema("kotlin_labs", "v0.3"),

      foreignSchema("cache", "v0.1"),

      foreignSchema("nullability", "v0.4"),

      foreignSchema("fakes", "v0.0"),
  )

  fun getForeignSchemaForDirective(name: String): ForeignSchema? {
    return foreignSchemas.firstOrNull { foreignSchema ->
      foreignSchema.definitions.directives().any { it.name == name }
    }
  }

  private fun foreignSchema(name: String, version: String): ForeignSchema =
    ForeignSchema(name, version, definitions("$name-$version.graphqls"))

  private fun definitions(resource: String): List<GQLDefinition> =
    javaClass.getClassLoader().getResourceAsStream("${GraphQLLibraryManager.DEFINITIONS_RESOURCE_DIR}/$resource")!!.source()
        .buffer().parseAsGQLDocument().getOrThrow().definitions
}

val ForeignSchema.url: String get() = "https://specs.apollo.dev/$name/$version"
