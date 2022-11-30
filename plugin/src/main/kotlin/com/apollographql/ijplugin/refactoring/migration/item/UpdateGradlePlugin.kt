package com.apollographql.ijplugin.refactoring.migration.item

import com.apollographql.ijplugin.util.logd
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

open class UpdateGradlePlugin(
  private val oldPluginId: String,
  private val newPluginId: String,
  private val newPluginVersion: String,
) : MigrationItem {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): Array<UsageInfo> {
    val buildGradleKtsFiles: Array<PsiFile> = FilenameIndex.getFilesByName(project, "build.gradle.kts", searchScope)
    val usages = mutableListOf<UsageInfo>()
    for (file in buildGradleKtsFiles) {
      logd("Found build.gradle.kts file: ${file.virtualFile.path} type: ${file.virtualFile.fileType} / ${file.virtualFile.fileType.name} / ${file::class.java}")
      if (file !is KtFile) continue
      file.accept(object : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          super.visitCallExpression(expression)
          if ((expression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == "id") {
            // id("xxx")
            val dependencyText =
              (expression.valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression)?.entries?.first()?.text
            if (dependencyText == oldPluginId) {
              // id("xxx") version "yyy"  /  id("xxx").version("yyy")
              if (expression.parent is KtBinaryExpression || expression.parent is KtDotQualifiedExpression) {
                usages.add(UsageInfo(expression.parent))
              } else {
                usages.add(UsageInfo(expression))
              }
            }
          }
        }
      })
    }
    return usages.toTypedArray()
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null
    if (element is KtBinaryExpression || element is KtDotQualifiedExpression) {
      element.replace(KtPsiFactory(project).createExpression("""id("$newPluginId").version("$newPluginVersion")"""))
    } else {
      element.replace(KtPsiFactory(project).createExpression("""id("$newPluginId")"""))
    }
    return null
  }
}
