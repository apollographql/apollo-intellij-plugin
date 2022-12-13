package com.apollographql.ijplugin.refactoring.migration.item

import com.apollographql.ijplugin.refactoring.findFieldReferences
import com.apollographql.ijplugin.refactoring.findInheritorsOfClass
import com.apollographql.ijplugin.util.unquoted
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.classes.KtLightClassBase
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry

object UpdateEnumValueCase : MigrationItem() {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): List<MigrationItemUsageInfo> {
    val usageInfo = mutableListOf<MigrationItemUsageInfo>()
    val allEnums = findInheritorsOfClass(project, "com.apollographql.apollo.api.EnumValue").filterIsInstance<KtLightClassBase>()
    for (enum in allEnums) {
      val enumConstants = enum.fields.filterIsInstance<PsiEnumConstant>()
      for (enumConstant in enumConstants) {
        val enumOldName = enumConstant.name

        @Suppress("UNCHECKED_CAST")
        val originEnumEntry = (enumConstant as KtLightElement<KtEnumEntry, *>).kotlinOrigin ?: continue
        val superTypeCallEntry = originEnumEntry.initializerList?.initializers?.firstOrNull() as? KtSuperTypeCallEntry ?: continue
        val enumNewName = superTypeCallEntry.valueArguments.firstOrNull()?.asElement()?.text?.unquoted() ?: continue
        if (enumOldName == enumNewName) {
          // Enum is upper case in the schema: no need to update
          continue
        }
        for (enumUsage in findFieldReferences(project = project, className = enum.qualifiedName!!, fieldName = enumOldName)) {
          usageInfo.add(MigrationItemUsageInfo(this@UpdateEnumValueCase, enumUsage, enumOldName to enumNewName))
        }
      }
    }

    return usageInfo
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: MigrationItemUsageInfo) {
    usage.element.replace(KtPsiFactory(project).createExpression(usage.attachedData<Pair<String, String>>().second))
  }
}
