package com.apollographql.ijplugin.gradle

import com.apollographql.ijplugin.util.capitalizeFirstLetter
import java.io.File

fun projectModelFile(projectDirectory: File) =
  File(projectDirectory, "build/gtask/generateApolloProjectModel/projectModelFile")

fun serviceModelFile(projectDirectory: File, serviceName: String) =
  File(projectDirectory, "build/gtask/generate${serviceName.capitalizeFirstLetter()}ApolloServiceModel/serviceModelFile")

fun codegenSchemaOptionsFile(projectDirectory: File, serviceName: String) =
  File(projectDirectory, "build/gtask/generate${serviceName.capitalizeFirstLetter()}ApolloOptions/codegenSchemaOptionsFile")

fun irOptionsFile(projectDirectory: File, serviceName: String) =
  File(projectDirectory, "build/gtask/generate${serviceName.capitalizeFirstLetter()}ApolloOptions/irOptionsFile")

fun codegenOptionsFile(projectDirectory: File, serviceName: String) =
  File(projectDirectory, "build/gtask/generate${serviceName.capitalizeFirstLetter()}ApolloOptions/codegenOptions")

fun codegenOutputDir(projectDirectory: File, serviceName: String) =
  File(projectDirectory, "build/generated/source/apollo/$serviceName")

fun operationManifestFile(projectDirectory: File, serviceName: String) =
  File(projectDirectory, "build/generated/manifest/apollo/$serviceName/persistedQueryManifest.json")
