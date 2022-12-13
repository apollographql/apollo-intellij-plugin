package com.apollographql.ijplugin.refactoring.migration.item

import com.apollographql.ijplugin.refactoring.findMethodReferences
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMigration
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.nj2k.postProcessing.resolve
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

object UpdateAddCustomTypeAdapter : MigrationItem() {
  override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): List<MigrationItemUsageInfo> {
    return findMethodReferences(
      project = project,
      className = "com.apollographql.apollo.ApolloClient.Builder",
      methodName = "addCustomTypeAdapter"
    ).toMigrationItemUsageInfo()
  }

  override fun performRefactoring(project: Project, migration: PsiMigration, usage: MigrationItemUsageInfo) {
    val callExpression = usage.element.parent as? KtCallExpression ?: return
    // 1st argument is (generally) a reference to the generated custom type, e.g. `CustomType.DATETIME`
    val firstArgument = callExpression.valueArguments.firstOrNull() ?: return
    val argumentDotQualifiedExpression = firstArgument.getArgumentExpression() as? KtDotQualifiedExpression ?: return
    // e.g. `DATETIME`
    val typeReference = argumentDotQualifiedExpression.selectorExpression as? KtNameReferenceExpression ?: return
    val enumEntry = typeReference.resolve() as? KtEnumEntry ?: return
    val classBody = enumEntry.body ?: return
    // 1st function looks like `override fun typeName(): String = "DateTime"`
    val typeNameFunction = classBody.functions.firstOrNull() ?: return
    // `"DateTime"`
    val bodyExpression = typeNameFunction.bodyExpression as? KtStringTemplateExpression ?: return
    val typeName = bodyExpression.entries.firstOrNull()?.text ?: return

    val psiFactory = KtPsiFactory(project)
    callExpression.calleeExpression?.replace(psiFactory.createExpression("addCustomScalarAdapter"))
    firstArgument.replace(psiFactory.createExpression("$typeName.type"))
  }
}
