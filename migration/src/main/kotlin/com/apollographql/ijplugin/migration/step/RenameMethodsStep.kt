package com.apollographql.ijplugin.migration.step

import com.apollographql.ijplugin.migration.MigrationManager
import com.apollographql.ijplugin.migration.logi
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class RenameMethodsStep(
  migrationManager: MigrationManager,
  classpath: List<String>,
  private val methodsToRename: Collection<MethodName>,
) : KotlinFilesMigrationStep(migrationManager, classpath) {
  class MethodName(
    val className: String,
    val oldMethodName: String,
    val newMethodName: String,
  )

  override fun processKtFile(ktFile: KtFile) {
    ktFile.accept(
      object : KtTreeVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
          super.visitCallExpression(expression)
          for (methodToRename in methodsToRename) {
            if ((expression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == methodToRename.oldMethodName) {
              // Found a method with the old name, now check if it's on the right class
              val resolvedCall = expression.getResolvedCall(bindingContext) ?: continue
              val containingDeclarationName = resolvedCall.resultingDescriptor.containingDeclaration.fqNameSafe.asString()
              if (containingDeclarationName == methodToRename.className) {
                logi("Found method to rename: ${methodToRename.className}.${methodToRename.oldMethodName} -> ${methodToRename.newMethodName} in ${ktFile.name} at ${expression.textOffset}")
                expression.calleeExpression?.replace(ktPsiFactory.createExpression(methodToRename.newMethodName))
                break
              }
            }
          }
        }
      }
    )
  }
}
