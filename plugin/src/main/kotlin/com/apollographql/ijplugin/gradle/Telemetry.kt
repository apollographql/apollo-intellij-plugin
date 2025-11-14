package com.apollographql.ijplugin.gradle

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.annotations.ApolloInternal
import com.apollographql.apollo.compiler.TargetLanguage
import com.apollographql.apollo.gradle.api.ApolloGradleToolingModel
import com.apollographql.apollo.tooling.model.TelemetryData
import com.apollographql.ijplugin.telemetry.TelemetryProperty
import com.apollographql.ijplugin.telemetry.TelemetryProperty.AndroidCompileSdk
import com.apollographql.ijplugin.telemetry.TelemetryProperty.AndroidGradlePluginVersion
import com.apollographql.ijplugin.telemetry.TelemetryProperty.AndroidMinSdk
import com.apollographql.ijplugin.telemetry.TelemetryProperty.AndroidTargetSdk
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloAddJvmOverloads
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloAddTypename
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloCodegenModels
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloDecapitalizeFields
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloFailOnWarnings
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloFieldsOnDisjointTypesMustMerge
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloFlattenModels
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGenerateApolloMetadata
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGenerateAsInternal
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGenerateDataBuilders
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGenerateFragmentImplementations
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGenerateInputBuilders
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGenerateKotlinModels
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGenerateMethods
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGenerateModelBuilders
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGenerateOptionalOperationVariables
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGeneratePrimitiveTypes
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGenerateQueryDocument
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGenerateSchema
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloGenerateSourcesDuringGradleSync
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloJsExport
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloLanguageVersion
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloLinkSqlite
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloNullableFieldStyle
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloOperationManifestFormat
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloServiceCount
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloUseSemanticNaming
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloUsedOptions
import com.apollographql.ijplugin.telemetry.TelemetryProperty.ApolloWarnOnDeprecatedUsages
import com.apollographql.ijplugin.telemetry.TelemetryProperty.GradleVersion
import com.apollographql.ijplugin.util.toCamelCase

fun ApolloGradleToolingModel.toTelemetryProperties(): Set<TelemetryProperty> = buildSet {
  // telemetryData was introduced in 1.2, accessing it on an older version will throw an exception
  if (versionMajor == 1 && versionMinor < 2) return@buildSet
  with(telemetryData) {
    gradleVersion?.let { add(GradleVersion(it)) }

    androidMinSdk?.let { add(AndroidMinSdk(it)) }
    androidTargetSdk?.let { add(AndroidTargetSdk(it)) }
    androidCompileSdk?.let { add(AndroidCompileSdk(it)) }
    androidAgpVersion?.let { add(AndroidGradlePluginVersion(it)) }

    apolloGenerateSourcesDuringGradleSync?.let { add(ApolloGenerateSourcesDuringGradleSync(it)) }
    apolloLinkSqlite?.let { add(ApolloLinkSqlite(it)) }
    add(ApolloServiceCount(apolloServiceCount))

    apolloServiceTelemetryData.forEach {
      it.codegenModels?.let { add(ApolloCodegenModels(it)) }
      it.operationManifestFormat?.let { add(ApolloOperationManifestFormat(it)) }
      it.warnOnDeprecatedUsages?.let { add(ApolloWarnOnDeprecatedUsages(it)) }
      it.failOnWarnings?.let { add(ApolloFailOnWarnings(it)) }
      it.generateKotlinModels?.let { add(ApolloGenerateKotlinModels(it)) }
      it.languageVersion?.let { add(ApolloLanguageVersion(it)) }
      it.useSemanticNaming?.let { add(ApolloUseSemanticNaming(it)) }
      it.addJvmOverloads?.let { add(ApolloAddJvmOverloads(it)) }
      it.generateAsInternal?.let { add(ApolloGenerateAsInternal(it)) }
      it.generateFragmentImplementations?.let { add(ApolloGenerateFragmentImplementations(it)) }
      it.generateQueryDocument?.let { add(ApolloGenerateQueryDocument(it)) }
      it.generateSchema?.let { add(ApolloGenerateSchema(it)) }
      it.generateOptionalOperationVariables?.let { add(ApolloGenerateOptionalOperationVariables(it)) }
      it.generateDataBuilders?.let { add(ApolloGenerateDataBuilders(it)) }
      it.generateModelBuilders?.let { add(ApolloGenerateModelBuilders(it)) }
      it.generateMethods?.let { add(ApolloGenerateMethods(it)) }
      it.generatePrimitiveTypes?.let { add(ApolloGeneratePrimitiveTypes(it)) }
      it.generateInputBuilders?.let { add(ApolloGenerateInputBuilders(it)) }
      it.nullableFieldStyle?.let { add(ApolloNullableFieldStyle(it)) }
      it.decapitalizeFields?.let { add(ApolloDecapitalizeFields(it)) }
      it.jsExport?.let { add(ApolloJsExport(it)) }
      it.addTypename?.let { add(ApolloAddTypename(it)) }
      it.flattenModels?.let { add(ApolloFlattenModels(it)) }
      it.fieldsOnDisjointTypesMustMerge?.let { add(ApolloFieldsOnDisjointTypesMustMerge(it)) }
      it.generateApolloMetadata?.let { add(ApolloGenerateApolloMetadata(it)) }
      add(ApolloUsedOptions(it.usedOptions.toList()))
    }
  }
}

@OptIn(ApolloInternal::class)
fun TelemetryData.toTelemetryProperties(): Set<TelemetryProperty> = buildSet {
  gradleVersion?.let { add(GradleVersion(it)) }

  androidMinSdk?.let { add(AndroidMinSdk(it)) }
  androidTargetSdk?.let { add(AndroidTargetSdk(it)) }
  androidCompileSdk?.let { add(AndroidCompileSdk(it)) }
  androidAgpVersion?.let { add(AndroidGradlePluginVersion(it)) }

  apolloGenerateSourcesDuringGradleSync?.let { add(ApolloGenerateSourcesDuringGradleSync(it)) }
  apolloLinkSqlite?.let { add(ApolloLinkSqlite(it)) }
  add(ApolloUsedOptions(usedServiceOptions.toList()))
}

@OptIn(ApolloExperimental::class)
fun ApolloKotlinService.toTelemetryProperties(): Set<TelemetryProperty> {
  val irOptions = irOptionsFile!!.toIrOptions()
  val codegenOptions = codegenOptionsFile!!.toCodegenOptions()
  val codegenSchemaOptions = codegenSchemaOptionsFile!!.toCodegenSchemaOptions()
  return buildSet {
    irOptions.codegenModels?.let { add(ApolloCodegenModels(it)) }
    codegenOptions.operationManifestFormat?.let { add(ApolloOperationManifestFormat(it)) }
    irOptions.failOnWarnings?.let { add(ApolloFailOnWarnings(it)) }
    codegenOptions.targetLanguage?.let { add(ApolloGenerateKotlinModels(it != TargetLanguage.JAVA)) }
    codegenOptions.targetLanguage?.let {
      if (it.name.startsWith("KOTLIN_")) {
        add(ApolloLanguageVersion(it.name.removePrefix("KOTLIN_").replace("_", ".")))
      }
    }
    codegenOptions.useSemanticNaming?.let { add(ApolloUseSemanticNaming(it)) }
    codegenOptions.addJvmOverloads?.let { add(ApolloAddJvmOverloads(it)) }
    codegenOptions.generateAsInternal?.let { add(ApolloGenerateAsInternal(it)) }
    codegenOptions.generateFragmentImplementations?.let { add(ApolloGenerateFragmentImplementations(it)) }
    codegenOptions.generateQueryDocument?.let { add(ApolloGenerateQueryDocument(it)) }
    codegenOptions.generateSchema?.let { add(ApolloGenerateSchema(it)) }
    irOptions.generateOptionalOperationVariables?.let { add(ApolloGenerateOptionalOperationVariables(it)) }
    add(ApolloGenerateDataBuilders(codegenSchemaOptions.generateDataBuilders))
    codegenOptions.generateModelBuilders?.let { add(ApolloGenerateModelBuilders(it)) }
    codegenOptions.generateMethods?.let { add(ApolloGenerateMethods(it.map { it.name.toCamelCase() })) }
    codegenOptions.generatePrimitiveTypes?.let { add(ApolloGeneratePrimitiveTypes(it)) }
    codegenOptions.generateInputBuilders?.let { add(ApolloGenerateInputBuilders(it)) }
    codegenOptions.nullableFieldStyle?.let { add(ApolloNullableFieldStyle(it.name.toCamelCase())) }
    codegenOptions.decapitalizeFields?.let { add(ApolloDecapitalizeFields(it)) }
    codegenOptions.jsExport?.let { add(ApolloJsExport(it)) }
    irOptions.addTypename?.let { add(ApolloAddTypename(it)) }
    irOptions.flattenModels?.let { add(ApolloFlattenModels(it)) }
  }
}
