package com.apollographql.ijplugin.refactoring.migration.item

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.migration.MigrationUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.castSafelyTo
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.resolve.ImportPath

object UpdateLruNormalizedCacheFactory : MigrationItem() {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): List<MigrationItemUsageInfo> {
    return MigrationUtil.findClassUsages(
      project,
      migration,
      "com.apollographql.apollo.cache.normalized.lru.LruNormalizedCacheFactory",
      searchScope
    ).toMigrationItemUsageInfo()
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null

    val psiFactory = KtPsiFactory(project)
    val importDirective = element.parentOfType<KtImportDirective>()
    if (importDirective != null) {
      // Reference is an import
      importDirective.replace(psiFactory.createImportDirective(ImportPath.fromString("com.apollographql.apollo3.cache.normalized.api.MemoryCacheFactory")))
    } else {
      // Reference is a class reference
      // Looking for something like:
      //     EvictionPolicy.builder()
      //      .maxSizeBytes(10 * 1024 * 1024)
      //      .expireAfterWrite(10, TimeUnit.MILLISECONDS)
      //      .build()
      val callExpression = element.parent as? KtCallExpression ?: return null
      val argumentExpression = callExpression.valueArguments.first().getArgumentExpression() as? KtDotQualifiedExpression ?: return null

      var maxSizeBytesValue: String? = null
      val maxSizeBytesCall = argumentExpression.findDescendantOfType<KtNameReferenceExpression> { it.getReferencedName() == "maxSizeBytes" }
      if (maxSizeBytesCall != null) {
        maxSizeBytesValue = maxSizeBytesCall.parent.castSafelyTo<KtCallExpression>()?.valueArguments?.firstOrNull()?.text
      }

      var expireAfterWriteTimeValue: String? = null
      var expireAfterWriteUnitValue: String? = null
      val expireAfterWriteCall =
        argumentExpression.findDescendantOfType<KtNameReferenceExpression> { it.getReferencedName() == "expireAfterWrite" }
      if (expireAfterWriteCall != null) {
        val arguments = expireAfterWriteCall.parent.castSafelyTo<KtCallExpression>()?.valueArguments
        expireAfterWriteTimeValue = arguments?.firstOrNull()?.text
        expireAfterWriteUnitValue = arguments?.getOrNull(1)?.text
      }

      val arguments = mutableListOf<String>()
      if (maxSizeBytesValue != null) {
        arguments.add("maxSizeBytes = $maxSizeBytesValue")
      }
      if (expireAfterWriteTimeValue != null && expireAfterWriteUnitValue?.startsWith("TimeUnit.") == true) {
        arguments.add("expireAfterMillis = $expireAfterWriteUnitValue.toMillis($expireAfterWriteTimeValue)")
      }

      element.parent.replace(psiFactory.createExpression("MemoryCacheFactory(${arguments.joinToString(", ")})"))
    }
    return null
  }
}
