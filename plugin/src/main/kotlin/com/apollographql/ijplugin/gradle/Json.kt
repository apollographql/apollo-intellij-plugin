package com.apollographql.ijplugin.gradle

import com.apollographql.apollo.compiler.CodegenOptions
import com.apollographql.apollo.compiler.CodegenSchemaOptions
import com.apollographql.apollo.compiler.IrOptions
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json {
  classDiscriminator = "#class"
  ignoreUnknownKeys = true
  explicitNulls = false
  coerceInputValues = true
}

private inline fun <reified T> File.parseFromJson(): T {
  return json.decodeFromString<T>(readText())
}

fun File.toCodegenOptions(): CodegenOptions = runCatching { parseFromJson<CodegenOptions>() }.getOrElse {
  CodegenOptions(
      targetLanguage = null,
      packageName = null,
      rootPackageName = null,
      decapitalizeFields = null,
      useSemanticNaming = null,
      generateMethods = null,
      operationManifestFormat = null,
      generateSchema = null,
      generatedSchemaName = null,
      sealedClassesForEnumsMatching = null,
      generateApolloEnums = null,
      generateAsInternal = null,
      addUnknownForEnums = null,
      addDefaultArgumentForInputObjects = null,
      generateFilterNotNull = null,
      generateInputBuilders = null,
      addJvmOverloads = null,
      requiresOptInAnnotation = null,
      jsExport = null,
      generateModelBuilders = null,
      classesForEnumsMatching = null,
      generatePrimitiveTypes = null,
      nullableFieldStyle = null,
      generateFragmentImplementations = null,
      generateQueryDocument = null,
  )
}

fun File.toCodegenSchemaOptions(): CodegenSchemaOptions = runCatching { parseFromJson<CodegenSchemaOptions>() }.getOrElse {
  CodegenSchemaOptions()
}

fun File.toIrOptions(): IrOptions = runCatching { parseFromJson<IrOptions>() }.getOrElse {
  IrOptions(
      fieldsOnDisjointTypesMustMerge = null,
      decapitalizeFields = null,
      flattenModels = null,
      warnOnDeprecatedUsages = null,
      failOnWarnings = null,
      addTypename = null,
      generateOptionalOperationVariables = null,
      alwaysGenerateTypesMatching = null,
      codegenModels = null,
  )
}
