package com.apollographql.ijplugin.refactoring

import com.apollographql.ijplugin.ApolloBundle
import com.apollographql.ijplugin.util.logd
import com.intellij.history.LocalHistory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMigration
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.migration.PsiMigrationManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.migration.MigrationUtil
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo

/**
 * Migrations of Apollo Android v2 to Apollo Kotlin v3.
 *
 * Implementation is based on [com.intellij.refactoring.migration.MigrationProcessor] and
 * [org.jetbrains.android.refactoring.MigrateToAndroidxProcessor].
 */
class ApolloV2ToV3MigrationProcessor(project: Project) : BaseRefactoringProcessor(project) {
  private companion object {
    private val migrationItems = arrayOf(
      MigrationItem.Package("com.apollographql.apollo", "com.apollographql.apollo3"),
      MigrationItem.Class("com.apollographql.apollo.api.Response", "com.apollographql.apollo3.api.ApolloResponse"),
    )

    private fun getRefactoringName() = ApolloBundle.message("ApolloV2ToV3MigrationProcessor.title")
  }

  private val migrationManager = PsiMigrationManager.getInstance(myProject)
  private var migration: PsiMigration? = null
  private val searchScope = GlobalSearchScope.projectScope(project)
  private val refsToShorten = mutableListOf<SmartPsiElementPointer<PsiElement>>()
  private val smartPointerManager = SmartPointerManager.getInstance(myProject)

  override fun getCommandName() = getRefactoringName()

  private val usageViewDescriptor = object : UsageViewDescriptorAdapter() {
    override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY

    override fun getProcessedElementsHeader() = ApolloBundle.message("ApolloV2ToV3MigrationProcessor.codeReferences")
  }

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>) = usageViewDescriptor

  private fun startMigration(): PsiMigration {
    return migrationManager.startMigration()
  }

  private fun finishMigration() {
    migrationManager?.currentMigration?.finish()
  }

  override fun doRun() {
    logd()
    migration = startMigration()
    // This will create classes / packages that we're finding references to in case they don't exist.
    // It must be done in doRun() as this is called from the EDT whereas findUsages() is not.
    for (migrationItem in migrationItems) {
      migrationItem.findUsages(myProject, migration!!, searchScope)
    }
    super.doRun()
  }

  override fun findUsages(): Array<UsageInfo> {
    logd()
    try {
      return migrationItems.flatMap { migrationItem ->
        migrationItem.findUsages(myProject, migration!!, searchScope)
          .filterNot { usageInfo ->
            // Filter out all generated code usages. We don't want generated code to come up in findUsages.
            // TODO: how to mark Apollo generated code as generated per this method?
            usageInfo.virtualFile?.let {
              GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(it, myProject)
            } == true
          }
          .map { usageInfo ->
            MigrationItemUsageInfo(
              source = usageInfo,
              migrationItem = migrationItem
            )
          }
      }
        .toTypedArray()
    } finally {
      ApplicationManager.getApplication().invokeLater({ WriteAction.run<Throwable>(::finishMigration) }, myProject.disposed)
    }
  }

  override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
    logd()
    if (refUsages.get().isEmpty()) {
      Messages.showInfoMessage(
        myProject,
        ApolloBundle.message("ApolloV2ToV3MigrationProcessor.noUsage"),
        getRefactoringName()
      )
      return false
    }
    isPreviewUsages = true
    return true
  }

  override fun performRefactoring(usages: Array<UsageInfo>) {
    logd()
    finishMigration()
    migration = startMigration()
    refsToShorten.clear()
    val action = LocalHistory.getInstance().startAction(commandName)
    try {
      for (usage in usages) {
        val element = (usage as MigrationItemUsageInfo).migrationItem.performRefactoring(myProject, migration!!, usage)
        if (element != null) {
          refsToShorten += smartPointerManager.createSmartPsiElementPointer(element)
        }
      }
    } finally {
      action.finish()
      finishMigration()
    }
  }

  override fun performPsiSpoilingRefactoring() {
    logd()
    // TODO: this works only for Java, not Kotlin
    // for Kotlin, we need to depend on a more recent version of the platform.
    // See https://github.com/JetBrains/intellij-community/blob/7c17222938b93877bf62b7448766c7f821a7180a/java/java-impl-refactorings/src/com/intellij/refactoring/migration/MigrationProcessor.java#L164
    val styleManager = JavaCodeStyleManager.getInstance(myProject)
    for (pointer in refsToShorten) {
      pointer.element?.let {
        styleManager.shortenClassReferences(it)
      }
    }
    refsToShorten.clear()
    finishMigration()
  }

  private sealed interface MigrationItem {
    fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): Array<UsageInfo>
    fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement?

    class Package(
      val oldName: String,
      val newName: String,
    ) : MigrationItem {
      override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): Array<UsageInfo> {
        return MigrationUtil.findPackageUsages(project, migration, oldName, searchScope)
      }

      override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
        val element = usage.element
        if (element == null || !element.isValid) return null
        val newPackage = findOrCreatePackage(project, migration, newName)
        return element.bindReferencesToElement(newPackage)
      }
    }

    class Class(
      val oldName: String,
      val newName: String,
    ) : MigrationItem {
      override fun findUsages(project: Project, migration: PsiMigration, searchScope: GlobalSearchScope): Array<UsageInfo> {
        return MigrationUtil.findClassUsages(project, migration, oldName, searchScope)
      }

      override fun performRefactoring(project: Project, migration: PsiMigration, usage: UsageInfo): PsiElement? {
        val element = usage.element
        if (element == null || !element.isValid) return null
        val newClass = findOrCreateClass(project, migration, newName)
        return element.bindReferencesToElement(newClass)
      }
    }
  }

  private class MigrationItemUsageInfo(
    source: UsageInfo,
    val migrationItem: MigrationItem,
  ) : UsageInfo(
    source.element!!,
    source.rangeInElement!!.startOffset,
    source.rangeInElement!!.endOffset
  )
}
