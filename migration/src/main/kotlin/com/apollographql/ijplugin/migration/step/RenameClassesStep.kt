package com.apollographql.ijplugin.migration.step

import com.apollographql.ijplugin.migration.MigrationManager
import com.apollographql.ijplugin.migration.logi
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils

class RenameClassesStep(
  migrationManager: MigrationManager,
  classpath: List<String>,
  private val classesToRename: Collection<ClassName>,
) : KotlinFilesMigrationStep(migrationManager, classpath) {
  class ClassName(
    val oldFQName: String,
    val newSimpleName: String,
  )

  override fun processKtFile(ktFile: KtFile): Boolean {
    var changed = false
    ktFile.accept(
      object : KtTreeVisitorVoid() {
        override fun visitTypeReference(typeReference: KtTypeReference) {
          super.visitTypeReference(typeReference)
          val kotlinType: KotlinType = bindingContext[BindingContext.TYPE, typeReference] ?: return
          val classDescriptor: ClassDescriptor = TypeUtils.getClassDescriptor(kotlinType) ?: return
          val typeFqn = classDescriptor.fqNameOrNull()?.asString() ?: return
          for (classToRename in classesToRename) {
            if (typeFqn == classToRename.oldFQName) {
              logi("Found class to rename: ${classToRename.oldFQName} -> ${classToRename.newSimpleName} in ${ktFile.name} at ${typeReference.textOffset}")
              (typeReference.typeElement as? KtUserType)?.referenceExpression?.replace(ktPsiFactory.createType(classToRename.newSimpleName))
              changed = true
              break
            }
          }
        }
      }
    )
    ktFile.accept(
      object : KtTreeVisitorVoid() {
        override fun visitImportList(importList: KtImportList) {
          super.visitImportList(importList)
          for (importDirective in importList.imports) {
            val importPath = importDirective.importPath ?: continue
            for (classToRename in classesToRename) {
              if (importPath.pathStr == classToRename.oldFQName) {
                logi("Found class to rename: ${classToRename.oldFQName} -> ${classToRename.newSimpleName} in ${ktFile.name} at ${importDirective.textOffset}")
                val newFqName = importPath.fqName.parent().child(Name.identifier(classToRename.newSimpleName))
                importDirective.replace(ktPsiFactory.createImportDirective(importPath.copy(newFqName)))
                changed = true
                break
              }
            }
          }
        }
      }
    )
    return changed
  }
}
