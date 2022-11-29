package com.apollographql.ijplugin

import com.apollographql.ijplugin.refactoring.migration.ApolloV2ToV3MigrationProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TODO At the moment, a checkout of intellij-community is required for this test to work
 * This of course won't work on the CI, so the tests are marked `@Ignore` for now.
 * See https://jetbrains-platform.slack.com/archives/CPL5291JP/p1664105522154139 and https://youtrack.jetbrains.com/issue/IJSDK-321
 */
@TestDataPath("\$CONTENT_ROOT/testData/migration/v2-to-v3")
@RunWith(JUnit4::class)
class ApolloV2ToV3MigrationTest : LightJavaCodeInsightFixtureTestCase() {
  private val mavenLibraries = listOf(
    "com.apollographql.apollo:apollo-runtime:2.5.14",
    "com.apollographql.apollo:apollo-coroutines-support:2.5.14",
  )

  override fun getTestDataPath() = "src/test/testData/migration/v2-to-v3"

  private val projectDescriptor = object : DefaultLightProjectDescriptor() {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      for (library in mavenLibraries) {
        MavenDependencyUtil.addFromMaven(model, library)
      }
    }
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return projectDescriptor
  }

  @Test
  fun testUpdatePackageName() = runMigration()

  @Test
  fun testUpdateMethodName() = runMigration()

  @Test
  fun testUpdateClassName() = runMigration()

  @Test
  fun testHttpCache() = runMigration()

  @Test
  fun testNormalizedCache() = runMigration()


  private fun runMigration() {
    myFixture.configureByFile(getTestName(false) + "_before.kt")
    ApolloV2ToV3MigrationProcessor(project).run()
    FileDocumentManager.getInstance().saveAllDocuments()
    myFixture.checkResultByFile(getTestName(false) + "_after.kt")
  }
}
