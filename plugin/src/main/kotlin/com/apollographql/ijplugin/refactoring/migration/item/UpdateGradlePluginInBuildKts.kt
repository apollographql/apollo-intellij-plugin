package com.apollographql.ijplugin.refactoring.migration.item

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

open class UpdateGradlePluginInBuildKts(
  private val oldPluginId: String,
  private val newPluginId: String,
  private val newPluginVersion: String,
) : MigrationItem {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): List<MigrationItemUsageInfo> {
    val buildGradleKtsFiles: Array<PsiFile> = FilenameIndex.getFilesByName(project, "build.gradle.kts", searchScope)
    val usages = mutableListOf<MigrationItemUsageInfo>()
    for (file in buildGradleKtsFiles) {
      if (file !is KtFile) continue
      file.accept(object : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          super.visitCallExpression(expression)
          if ((expression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == "id") {
            // id("xxx")
            val dependencyText =
              (expression.valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression)?.entries?.first()?.text
            if (dependencyText == oldPluginId) {
              val parent = expression.parent
              if (parent is KtBinaryExpression || parent is KtDotQualifiedExpression) {
                // id("xxx") version "yyy"  /  id("xxx").version("yyy")
                usages.add(parent.toMigrationItemUsageInfo())
              } else {
                usages.add(expression.toMigrationItemUsageInfo())
              }
            }
          }
        }
      })
    }
    return usages
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null
    if (element is KtBinaryExpression || element is KtDotQualifiedExpression) {
      // id("xxx") version "yyy"  /  id("xxx").version("yyy")
      element.replace(KtPsiFactory(project).createExpression("""id("$newPluginId").version("$newPluginVersion")"""))
    } else {
      // id("xxx")
      element.replace(KtPsiFactory(project).createExpression("""id("$newPluginId")"""))
    }
    return null
  }
}
