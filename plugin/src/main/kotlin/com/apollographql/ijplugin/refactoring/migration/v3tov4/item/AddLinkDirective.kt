@file:OptIn(ApolloExperimental::class)

package com.apollographql.ijplugin.refactoring.migration.v3tov4.item

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.ast.ForeignSchema
import com.apollographql.ijplugin.graphql.ForeignSchemas
import com.apollographql.ijplugin.graphql.url
import com.apollographql.ijplugin.refactoring.migration.item.MigrationItem
import com.apollographql.ijplugin.refactoring.migration.item.MigrationItemUsageInfo
import com.apollographql.ijplugin.util.cast
import com.apollographql.ijplugin.util.createLinkDirective
import com.apollographql.ijplugin.util.createLinkDirectiveSchemaExtension
import com.apollographql.ijplugin.util.findPsiFilesByName
import com.apollographql.ijplugin.util.isImported
import com.apollographql.ijplugin.util.linkDirectives
import com.apollographql.ijplugin.util.nameForImport
import com.apollographql.ijplugin.util.unquoted
import com.intellij.lang.jsgraphql.psi.GraphQLArrayValue
import com.intellij.lang.jsgraphql.psi.GraphQLDirective
import com.intellij.lang.jsgraphql.psi.GraphQLElementFactory
import com.intellij.lang.jsgraphql.psi.GraphQLFile
import com.intellij.lang.jsgraphql.psi.GraphQLRecursiveVisitor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope

object AddLinkDirective : MigrationItem() {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): List<MigrationItemUsageInfo> {
    val usages = mutableListOf<MigrationItemUsageInfo>()
    val extraGraphqlsFiles: List<GraphQLFile> = project.findPsiFilesByName("extra.graphqls", searchScope).filterIsInstance<GraphQLFile>()
    for (file in extraGraphqlsFiles) {
      file.accept(
          object : GraphQLRecursiveVisitor() {
            override fun visitDirective(o: GraphQLDirective) {
              super.visitDirective(o)
              val foreignSchema = ForeignSchemas.getForeignSchemaForDirective(o.name!!) ?: return
              if (!o.isImported()) {
                usages.add(o.toMigrationItemUsageInfo(foreignSchema))
              }
            }
          }
      )
    }
    return usages
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: MigrationItemUsageInfo) {
    val directive = usage.element as GraphQLDirective
    val extraSchemaFile = directive.containingFile as GraphQLFile
    val foreignSchema = usage.attachedData() as ForeignSchema
    val linkDirective = extraSchemaFile.linkDirectives(foreignSchema.url).firstOrNull()
    if (linkDirective == null) {
      val linkDirectiveSchemaExtension =
        createLinkDirectiveSchemaExtension(project, setOf(directive.nameForImport), foreignSchema.definitions, foreignSchema.url)
      val addedElement = extraSchemaFile.addBefore(linkDirectiveSchemaExtension, extraSchemaFile.firstChild)
      extraSchemaFile.addAfter(GraphQLElementFactory.createWhiteSpace(project, "\n\n"), addedElement)
    } else {
      val importedNames = buildSet {
        addAll(linkDirective.arguments!!.argumentList.firstOrNull { it.name == "import" }?.value?.cast<GraphQLArrayValue>()?.valueList.orEmpty()
            .map { it.text.unquoted() })
        add(directive.nameForImport)
      }
      linkDirective.replace(createLinkDirective(project, importedNames, foreignSchema.definitions, foreignSchema.url))
    }
  }
}
