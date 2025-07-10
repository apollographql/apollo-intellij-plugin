@file:OptIn(ApolloExperimental::class)

package com.apollographql.ijplugin.inspection

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.ijplugin.project.apolloProjectService
import com.apollographql.ijplugin.util.linkedDefinitions
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.jsgraphql.psi.GraphQLDirective
import com.intellij.lang.jsgraphql.psi.GraphQLVisitor
import com.intellij.lang.jsgraphql.schema.library.GraphQLLibraryManager
import com.intellij.lang.jsgraphql.schema.library.LinkedGraphQLLibrarySet
import com.intellij.lang.jsgraphql.schema.library.LinkedGraphQLLibrarySetKey
import com.intellij.psi.PsiElementVisitor

/**
 * TODO
 */
class TestLibInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : GraphQLVisitor() {
      override fun visitDirective(o: GraphQLDirective) {
        super.visitDirective(o)
        if (!o.project.apolloProjectService.apolloVersion.isAtLeastV4) return
        visitDirective(o, holder)
      }
    }
  }

  private fun visitDirective(
      directiveElement: GraphQLDirective,
      holder: ProblemsHolder,
  ) {
    val currentLinkedGraphQLLibrarySet = directiveElement.project.getUserData(LinkedGraphQLLibrarySetKey)
    val newLinkedGraphQLLibrarySet = LinkedGraphQLLibrarySet(directiveElement.linkedDefinitions())
    if (newLinkedGraphQLLibrarySet != currentLinkedGraphQLLibrarySet) {
      directiveElement.project.putUserData(LinkedGraphQLLibrarySetKey, newLinkedGraphQLLibrarySet)
      GraphQLLibraryManager.getInstance(directiveElement.project).notifyLibrariesChanged()
    }
  }
}
