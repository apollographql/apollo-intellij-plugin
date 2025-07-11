@file:OptIn(ApolloExperimental::class)

package com.apollographql.ijplugin.inspection

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.ijplugin.project.apolloProjectService
import com.apollographql.ijplugin.util.linkedDefinitions
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.jsgraphql.psi.GraphQLFile
import com.intellij.lang.jsgraphql.psi.GraphQLVisitor
import com.intellij.lang.jsgraphql.schema.library.GraphQLLibraryManager
import com.intellij.lang.jsgraphql.schema.library.LinkedGraphQLLibrarySet
import com.intellij.lang.jsgraphql.schema.library.LinkedGraphQLLibrarySetKey
import com.intellij.psi.PsiElementVisitor

/**
 * Looks for the linked (`@link`) GraphQL definitions in the schema related of the file being inspected, and stores that information in the
 * project. The associated libraries are then enabled or disabled in the GraphQL Library Manager based on that information.
 */
class ApolloLinkedGraphQLDefinitionsInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : GraphQLVisitor() {
      override fun visitGraphQLFile(file: GraphQLFile) {
        super.visitGraphQLFile(file)
        if (!file.project.apolloProjectService.apolloVersion.isAtLeastV4) return
        val currentLinkedGraphQLLibrarySet = file.project.getUserData(LinkedGraphQLLibrarySetKey)
        val newLinkedGraphQLLibrarySet = LinkedGraphQLLibrarySet(file.linkedDefinitions())
        if (newLinkedGraphQLLibrarySet != currentLinkedGraphQLLibrarySet) {
          file.project.putUserData(LinkedGraphQLLibrarySetKey, newLinkedGraphQLLibrarySet)
          GraphQLLibraryManager.getInstance(file.project).notifyLibrariesChanged()
        }
      }
    }
  }
}
