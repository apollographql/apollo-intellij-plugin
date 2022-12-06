package com.apollographql.ijplugin.refactoring.migration

import com.apollographql.ijplugin.ApolloBundle
import com.apollographql.ijplugin.refactoring.migration.item.MigrationItemUsageInfo
import com.apollographql.ijplugin.refactoring.migration.item.RemoveDependenciesInBuildKts
import com.apollographql.ijplugin.refactoring.migration.item.RemoveDependenciesInToml
import com.apollographql.ijplugin.refactoring.migration.item.RemoveMethodCall
import com.apollographql.ijplugin.refactoring.migration.item.RemoveMethodImport
import com.apollographql.ijplugin.refactoring.migration.item.UpdateClassName
import com.apollographql.ijplugin.refactoring.migration.item.UpdateFieldName
import com.apollographql.ijplugin.refactoring.migration.item.UpdateGradleDependenciesBuildKts
import com.apollographql.ijplugin.refactoring.migration.item.UpdateGradleDependenciesInToml
import com.apollographql.ijplugin.refactoring.migration.item.UpdateGradlePluginInBuildKts
import com.apollographql.ijplugin.refactoring.migration.item.UpdateMethodName
import com.apollographql.ijplugin.refactoring.migration.item.UpdatePackageName
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
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.psi.KtElement

/**
 * Migrations of Apollo Android v2 to Apollo Kotlin v3.
 *
 * Implementation is based on [com.intellij.refactoring.migration.MigrationProcessor] and
 * [org.jetbrains.android.refactoring.MigrateToAndroidxProcessor].
 */
class ApolloV2ToV3MigrationProcessor(project: Project) : BaseRefactoringProcessor(project) {
  private companion object {
    private const val apollo2 = "com.apollographql.apollo"
    private const val apollo3 = "com.apollographql.apollo3"
    private const val apollo3LatestVersion = "3.7.1"

    private val migrationItems = arrayOf(
      RemoveMethodImport("$apollo2.coroutines.CoroutinesExtensionsKt", "toFlow"),
      UpdateClassName("$apollo2.api.Response", "$apollo3.api.ApolloResponse"),
      UpdateClassName("$apollo2.ApolloQueryCall", "$apollo3.ApolloCall"),
      UpdateMethodName("$apollo2.ApolloClient", "mutate", "mutation"),
      UpdateMethodName("$apollo2.ApolloClient", "subscribe", "subscription"),
      UpdateMethodName("$apollo2.ApolloClient", "builder", "Builder"),
      UpdateMethodName("$apollo2.coroutines.CoroutinesExtensionsKt", "await", "execute"),

      // Http cache
      UpdateMethodName("$apollo2.ApolloQueryCall.Builder", "httpCachePolicy", "httpFetchPolicy"),
      UpdateClassName("$apollo2.api.cache.http.HttpCachePolicy", "$apollo3.cache.http.HttpFetchPolicy"),
      UpdateFieldName("$apollo2.api.cache.http.HttpCachePolicy", "CACHE_ONLY", "CacheOnly"),
      UpdateFieldName("$apollo2.api.cache.http.HttpCachePolicy", "NETWORK_ONLY", "NetworkOnly"),
      UpdateFieldName("$apollo2.api.cache.http.HttpCachePolicy", "CACHE_FIRST", "CacheFirst"),
      UpdateFieldName("$apollo2.api.cache.http.HttpCachePolicy", "NETWORK_FIRST", "NetworkFirst"),

      // Normalized cache
      UpdateMethodName("$apollo2.ApolloQueryCall.Builder", "responseFetcher", "fetchPolicy"),
      UpdateClassName("$apollo2.fetcher.ApolloResponseFetchers", "$apollo3.cache.normalized.FetchPolicy"),
      UpdateFieldName("$apollo2.fetcher.ApolloResponseFetchers", "CACHE_ONLY", "CacheOnly"),
      UpdateFieldName("$apollo2.fetcher.ApolloResponseFetchers", "NETWORK_ONLY", "NetworkOnly"),
      UpdateFieldName("$apollo2.fetcher.ApolloResponseFetchers", "CACHE_FIRST", "CacheFirst"),
      UpdateFieldName("$apollo2.fetcher.ApolloResponseFetchers", "NETWORK_FIRST", "NetworkFirst"),

      RemoveMethodCall("$apollo2.ApolloQueryCall", "toBuilder"),
      RemoveMethodCall("$apollo2.ApolloQueryCall.Builder", "build"),

      UpdatePackageName(apollo2, apollo3),

      // Gradle
      RemoveDependenciesInBuildKts("$apollo2:apollo-coroutines-support", "$apollo2:apollo-android-support"),
      RemoveDependenciesInToml("apollo-coroutines-support", "apollo-android-support"),
      UpdateGradlePluginInBuildKts(apollo2, apollo3, apollo3LatestVersion),
      UpdateGradleDependenciesInToml(apollo2, apollo3, apollo3LatestVersion),
      UpdateGradleDependenciesBuildKts(apollo2, apollo3)
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
      migrationItem.prepare(myProject, migration!!)
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
        val elementToShorten = (usage as MigrationItemUsageInfo).migrationItem.performRefactoring(myProject, migration!!, usage)
        if (elementToShorten != null) {
          refsToShorten += smartPointerManager.createSmartPsiElementPointer(elementToShorten)
        }
      }
    } finally {
      action.finish()
      finishMigration()
    }
  }

  override fun performPsiSpoilingRefactoring() {
    logd()
    // TODO: this doesn't seem to work for Kotlin
    // In more recent versions of the platform, there's a new way to do this.
    // See https://github.com/JetBrains/intellij-community/blob/7c17222938b93877bf62b7448766c7f821a7180a/java/java-impl-refactorings/src/com/intellij/refactoring/migration/MigrationProcessor.java#L164
    val styleManager = JavaCodeStyleManager.getInstance(myProject)
    for (pointer in refsToShorten) {
      pointer.element?.let {
        if (it is KtElement) {
          // Kotlin
          ShortenReferences.DEFAULT.process(it)
        } else {
          // Java
          styleManager.shortenClassReferences(it)
        }
      }
    }
    refsToShorten.clear()
    finishMigration()
  }
}

