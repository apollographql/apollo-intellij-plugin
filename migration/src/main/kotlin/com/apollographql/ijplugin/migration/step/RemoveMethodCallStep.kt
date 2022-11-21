package com.apollographql.ijplugin.migration.step

import com.apollographql.ijplugin.migration.KotlinEnvironment
import com.apollographql.ijplugin.migration.MigrationManager
import com.apollographql.ijplugin.migration.util.delete
import com.apollographql.ijplugin.migration.util.logi
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class RemoveMethodCallStep(
  migrationManager: MigrationManager,
  kotlinEnvironment: KotlinEnvironment,
  private val methodCallsToRemove: Collection<MethodName>,
) : KotlinFilesMigrationStep(migrationManager, kotlinEnvironment) {
  class MethodName(
    val containingDeclarationName: String,
    val methodName: String,
  )

  override fun processKtFile(ktFile: KtFile) {
    ktFile.accept(
      object : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          super.visitCallExpression(expression)
          for (methodToRename in methodCallsToRemove) {
            if ((expression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == methodToRename.methodName) {
              // Found a method with the old name, now check if it's on the right class
              val resolvedCall = expression.getResolvedCall(bindingContext) ?: continue
              val containingDeclarationName = resolvedCall.resultingDescriptor.containingDeclaration.fqNameSafe.asString()
              if (containingDeclarationName == methodToRename.containingDeclarationName) {
                logi("Found method to remove: ${methodToRename.containingDeclarationName}.${methodToRename.methodName} in ${ktFile.name} at ${expression.textOffset}")
                expression.getPrevSiblingIgnoringWhitespaceAndComments()?.delete(true)
                expression.delete(true)
                break
              }
            }
          }
        }
      }
    )
  }
}
