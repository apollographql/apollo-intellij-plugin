package com.apollographql.ijplugin.refactoring.migration.item

import com.apollographql.ijplugin.refactoring.findMethodReferences
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

object UpdateHttpCache : MigrationItem() {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): List<MigrationItemUsageInfo> {
    return findMethodReferences(
      project = project,
      className = "com.apollographql.apollo.ApolloClient.Builder",
      methodName = "httpCache"
    ).toMigrationItemUsageInfo()
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null
    val psiFactory = KtPsiFactory(project)

    // httpCache(...)
    val httpCacheCallExpression = element.parent as? KtCallExpression ?: return null
    val httpCacheArgumentExpression = httpCacheCallExpression.valueArguments.firstOrNull()?.getArgumentExpression() ?: return null
    when (httpCacheArgumentExpression) {
      is KtCallExpression -> {
        val apolloHttpCacheArguments = extractApolloHttpCacheArguments(httpCacheArgumentExpression)
        if (apolloHttpCacheArguments != null) {
          element.parent.replace(psiFactory.createExpression("httpCache(${apolloHttpCacheArguments.joinToString(", ")})"))
        }
      }

      is KtNameReferenceExpression -> {
        // Look for `httpCache(xxx)` where xxx is a val defined as `val xxx = ApolloHttpCache(...)`
        val referencedVal = httpCacheArgumentExpression.resolve()
        if (referencedVal is KtProperty) {
          val initializerExpression = referencedVal.initializer
          if (initializerExpression is KtCallExpression) {
            val apolloHttpCacheArguments = extractApolloHttpCacheArguments(initializerExpression)
            if (apolloHttpCacheArguments != null) {
              element.parent.replace(psiFactory.createExpression("httpCache(${apolloHttpCacheArguments.joinToString(", ")})"))
              referencedVal.delete()
            }
          }
        }
      }

      else -> {
        // Not supported, add a TODO comment
        element.parent.replace(psiFactory.createExpression("httpCache(/* TODO: This could not be migrated automatically. Please check the migration guide at https://www.apollographql.com/docs/kotlin/migration/3.0/ */)"))
      }
    }
    return null
  }

  private fun extractApolloHttpCacheArguments(callExpression: KtCallExpression): List<String>? {
    // Look for `ApolloHttpCache(...)`
    if (callExpression.calleeExpression?.text == "ApolloHttpCache") {
      val apolloHttpCacheCtorArgument = callExpression.valueArguments.firstOrNull()?.getArgumentExpression()
      // Look for `DiskLruHttpCacheStore(...)` call
      if (apolloHttpCacheCtorArgument is KtCallExpression) {
        if (apolloHttpCacheCtorArgument.calleeExpression?.text == "DiskLruHttpCacheStore") {
          // There are 2 variants of `DiskLruHttpCacheStore` constructor: with 3 and 2 arguments
          val diskLruHttpCacheStore = if (apolloHttpCacheCtorArgument.valueArguments.size == 3) {
            // Ignore the first argument (FileSystem)
            apolloHttpCacheCtorArgument.valueArguments.drop(1)
          } else {
            apolloHttpCacheCtorArgument.valueArguments
          }
          if (diskLruHttpCacheStore.size == 2) {
            val fileArgumentExpression = diskLruHttpCacheStore[0].getArgumentExpression()?.text
            val maxSizeArgumentExpression = diskLruHttpCacheStore[1].getArgumentExpression()?.text
            if (fileArgumentExpression != null && maxSizeArgumentExpression != null) {
              return listOf(fileArgumentExpression, maxSizeArgumentExpression)
            }
          }
        }
      }
    }
    return null
  }
}
