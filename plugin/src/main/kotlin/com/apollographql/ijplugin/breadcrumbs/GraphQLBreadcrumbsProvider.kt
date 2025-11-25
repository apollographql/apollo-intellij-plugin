package com.apollographql.ijplugin.breadcrumbs

import com.intellij.lang.Language
import com.intellij.lang.jsgraphql.GraphQLLanguage
import com.intellij.lang.jsgraphql.psi.GraphQLDirective
import com.intellij.lang.jsgraphql.psi.GraphQLDirectiveDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLEnumTypeDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLEnumTypeExtensionDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLEnumValue
import com.intellij.lang.jsgraphql.psi.GraphQLEnumValueDefinitions
import com.intellij.lang.jsgraphql.psi.GraphQLField
import com.intellij.lang.jsgraphql.psi.GraphQLFieldDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLFieldsDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLFragmentDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLFragmentSpread
import com.intellij.lang.jsgraphql.psi.GraphQLInlineFragment
import com.intellij.lang.jsgraphql.psi.GraphQLInterfaceTypeExtensionDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLNamedTypeDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLObjectTypeExtensionDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLScalarTypeDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLSchemaExtension
import com.intellij.lang.jsgraphql.psi.GraphQLTypedOperationDefinition
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider

class GraphQLBreadcrumbsProvider : BreadcrumbsProvider {
  override fun getLanguages(): Array<Language> = arrayOf(GraphQLLanguage.INSTANCE)

  override fun acceptElement(element: PsiElement): Boolean {
    // Executable
    return element is GraphQLTypedOperationDefinition ||
        element is GraphQLFragmentDefinition ||
        element is GraphQLField ||
        element is GraphQLFragmentSpread ||
        element is GraphQLInlineFragment ||

        // Schema
        element is GraphQLFieldsDefinition ||
        element is GraphQLFieldDefinition ||
        element is GraphQLEnumValueDefinitions ||
        element is GraphQLEnumValue ||
        element is GraphQLDirectiveDefinition ||
        element is GraphQLScalarTypeDefinition ||

        // Schema extensions
        element is GraphQLSchemaExtension ||
        element is GraphQLObjectTypeExtensionDefinition ||
        element is GraphQLInterfaceTypeExtensionDefinition ||
        element is GraphQLEnumTypeExtensionDefinition ||
        element is GraphQLDirective
  }

  override fun getElementInfo(element: PsiElement): @NlsSafe String {
    return when (element) {
      // Executable
      is GraphQLTypedOperationDefinition -> "${element.operationType.text} ${element.name ?: "<unnamed>"}"
      is GraphQLFragmentDefinition -> "fragment ${element.name ?: "<unnamed>"}"
      is GraphQLField -> element.name.orEmpty()
      is GraphQLFragmentSpread -> "...${element.name}"
      is GraphQLInlineFragment -> element.typeCondition?.typeName?.name.let { if (it != null) "... on $it" else "..."}

      // Schema
      is GraphQLFieldsDefinition -> (element.parent as GraphQLNamedTypeDefinition).typeNameDefinition?.name ?: "<unknown>"
      is GraphQLFieldDefinition -> element.name.orEmpty()
      is GraphQLEnumValueDefinitions -> (element.parent as? GraphQLEnumTypeDefinition)?.typeNameDefinition?.name ?: "<unknown>"
      is GraphQLEnumValue -> element.name.orEmpty()
      is GraphQLDirectiveDefinition -> "@${element.nameIdentifier?.text.orEmpty()}"
      is GraphQLScalarTypeDefinition -> "${element.typeNameDefinition}"

      // Schema extensions
      is GraphQLSchemaExtension -> "extend schema"
      is GraphQLObjectTypeExtensionDefinition -> "extend type ${element.typeName?.name ?: "<unknown>"}"
      is GraphQLInterfaceTypeExtensionDefinition -> "extend interface ${element.typeName?.name ?: "<unknown>"}"
      is GraphQLEnumTypeExtensionDefinition -> "extend enum ${element.typeName?.name ?: "<unknown>"}"
      is GraphQLDirective -> "@${element.nameIdentifier?.text.orEmpty()}"

      // else -> error("Unknown element $element")
      else -> element::class.simpleName ?: "Unknown element"
    }
  }
}
