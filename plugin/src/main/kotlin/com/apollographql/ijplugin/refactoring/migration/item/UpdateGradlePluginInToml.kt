package com.apollographql.ijplugin.refactoring.migration.item

import com.apollographql.ijplugin.util.quoted
import com.apollographql.ijplugin.util.unquoted
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlPsiFactory
import org.toml.lang.psi.TomlRecursiveVisitor
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

open class UpdateGradlePluginInToml(
  private val oldPluginId: String,
  private val newPluginId: String,
  private val newPluginVersion: String,
) : MigrationItem {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): List<MigrationItemUsageInfo> {
    val libsVersionTomlFiles: Array<PsiFile> = FilenameIndex.getFilesByName(project, "libs.versions.toml", searchScope)
    val usages = mutableListOf<MigrationItemUsageInfo>()
    for (file in libsVersionTomlFiles) {
      if (file !is TomlFile) continue
      file.accept(object : TomlRecursiveVisitor() {
        override fun visitLiteral(element: TomlLiteral) {
          super.visitLiteral(element)
          if (element.kind is TomlLiteralKind.String) {
            val dependencyText = element.text.unquoted()
            if (dependencyText == oldPluginId) {
              usages.add(UsageInfo(this@UpdateGradlePluginInToml, element.firstChild, UsageInfo.Kind.ID))
              // Find the associated version
              val versionEntry = (element.parent.parent as? TomlInlineTable)?.entries
                ?.first { it.key.text == "version" || it.key.text == "version.ref" }
              if (versionEntry != null) {
                if (versionEntry.key.text == "version") {
                  versionEntry.value?.let { usages.add(UsageInfo(this@UpdateGradlePluginInToml, it.firstChild, UsageInfo.Kind.VERSION)) }
                } else {
                  // Resolve the reference
                  val versionsTable = element.containingFile.children.filterIsInstance<TomlTable>()
                    .firstOrNull { it.header.key?.text == "versions" }
                  val versionRefKey = versionEntry.value?.text?.unquoted()
                  val refTarget = versionsTable?.entries?.firstOrNull { it.key.text == versionRefKey }
                  refTarget?.value?.let { usages.add(UsageInfo(this@UpdateGradlePluginInToml, it.firstChild, UsageInfo.Kind.VERSION)) }
                }
              }
            } else if (dependencyText.startsWith("$oldPluginId:")) {
              if ((((element.parent?.parent) as? TomlTable)?.header)?.key?.text == "plugins") {
                usages.add(UsageInfo(this@UpdateGradlePluginInToml, element.firstChild, UsageInfo.Kind.SHORT_NOTATION))
              }
            }
          }
        }
      })
    }
    return usages
  }

  private class UsageInfo(migrationItem: UpdateGradlePluginInToml, element: PsiElement, val kind: Kind) :
    MigrationItemUsageInfo(migrationItem, element) {
    enum class Kind {
      SHORT_NOTATION, ID, VERSION
    }
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: com.intellij.usageView.UsageInfo): PsiElement? {
    val element = usage.element
    if (element == null || !element.isValid) return null
    val kind = (usage as UsageInfo).kind
    when (kind) {
      UsageInfo.Kind.SHORT_NOTATION -> element.replace(TomlPsiFactory(project).createLiteral("$newPluginId:$newPluginVersion".quoted()))
      UsageInfo.Kind.ID -> element.replace(TomlPsiFactory(project).createLiteral(newPluginId.quoted()))
      UsageInfo.Kind.VERSION -> element.replace(TomlPsiFactory(project).createLiteral(newPluginVersion.quoted()))
    }
    return null
  }
}
