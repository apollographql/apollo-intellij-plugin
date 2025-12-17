package com.apollographql.ijplugin.inspection

import com.apollographql.ijplugin.ApolloBundle
import com.apollographql.ijplugin.navigation.compat.KotlinFindUsagesHandlerFactoryCompat
import com.apollographql.ijplugin.navigation.findKotlinOperationDefinitions
import com.apollographql.ijplugin.project.apolloProjectService
import com.apollographql.ijplugin.telemetry.TelemetryEvent
import com.apollographql.ijplugin.util.isProcessCanceled
import com.apollographql.ijplugin.util.logd
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.jsgraphql.psi.GraphQLIdentifier
import com.intellij.lang.jsgraphql.psi.GraphQLTypedOperationDefinition
import com.intellij.lang.jsgraphql.psi.GraphQLVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.childrenOfType

class ApolloUnusedOperationInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : GraphQLVisitor() {
      override fun visitTypedOperationDefinition(o: GraphQLTypedOperationDefinition) {
        logd("XXX Visiting operation definition: ${o.name}")
        if (isUnusedOperation(o)) {
          logd("XXX Found unused operation: ${o.name}")
          val nameElement = o.childrenOfType<GraphQLIdentifier>().firstOrNull() ?: return
          holder.registerProblem(
              nameElement,
              ApolloBundle.message("inspection.unusedOperation.reportText"),
              DeleteElementQuickFix(label = "inspection.unusedOperation.quickFix", telemetryEvent = { TelemetryEvent.ApolloIjUnusedOperationQuickFix() }) { it.parent }
          )
        }
      }
    }
  }
}

fun isUnusedOperation(operationDefinition: GraphQLTypedOperationDefinition): Boolean {
  logd("XXX Checking if operation is unused: ${operationDefinition.name}")
  if (isProcessCanceled()) return false
  logd("XXX Apollo version: ${operationDefinition.project.apolloProjectService.apolloVersion}")
  if (!operationDefinition.project.apolloProjectService.apolloVersion.isAtLeastV3) return false
  val ktClasses = findKotlinOperationDefinitions(operationDefinition).ifEmpty {
    // Didn't find any generated class: maybe in the middle of writing a new operation, let's not report an error yet.
    logd("XXX No generated class found for operation: ${operationDefinition.name}")
    return false
  }
  logd("XXX Found generated classes for operation ${operationDefinition.name}: ${ktClasses.map { it.name }}")
  val kotlinFindUsagesHandlerFactory = KotlinFindUsagesHandlerFactoryCompat(operationDefinition.project)
  val hasUsageProcessor = HasUsageProcessor()
  for (kotlinDefinition in ktClasses) {
    if (kotlinFindUsagesHandlerFactory.canFindUsages(kotlinDefinition)) {
      val kotlinFindUsagesHandler = kotlinFindUsagesHandlerFactory.createFindUsagesHandler(kotlinDefinition, false).also {
        if (it == null) {
          logd("XXX createFindUsagesHandler returned null for: ${kotlinDefinition.name}")
        }
      }
          ?: return false
      val findUsageOptions = kotlinFindUsagesHandlerFactory.findClassOptions.also {
        if (it == null) {
          logd("XXX findClassOptions returned null for: ${kotlinDefinition.name}")
        }
      } ?: return false
      logd("XXX Calling processElementUsages for ${kotlinDefinition.name}")
      kotlinFindUsagesHandler.processElementUsages(kotlinDefinition, hasUsageProcessor, findUsageOptions)
      logd("XXX Usage found for ${kotlinDefinition.name}: ${hasUsageProcessor.foundUsage}")
      if (hasUsageProcessor.foundUsage) return false
    } else {
      logd("XXX canFindUsages returned false for: ${kotlinDefinition.name}")
      return false
    }
  }
  return true
}
