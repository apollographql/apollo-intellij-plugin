@file:OptIn(ApolloInternal::class, ApolloExperimental::class)

package com.apollographql.ijplugin.gradle

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.annotations.ApolloInternal
import com.apollographql.apollo.compiler.model.CompilationUnitModel
import com.apollographql.apollo.compiler.model.ProjectModel
import com.apollographql.apollo.compiler.model.toCompilationUnitModel
import com.apollographql.apollo.compiler.model.toProjectModel
import com.apollographql.apollo.gradle.api.ApolloGradleToolingModel
import com.apollographql.apollo.tooling.model.TelemetryData
import com.apollographql.apollo.tooling.model.toTelemetryData
import com.apollographql.ijplugin.project.ApolloProjectListener
import com.apollographql.ijplugin.project.ApolloProjectService
import com.apollographql.ijplugin.project.apolloProjectService
import com.apollographql.ijplugin.settings.ProjectSettingsListener
import com.apollographql.ijplugin.settings.ProjectSettingsState
import com.apollographql.ijplugin.settings.projectSettingsState
import com.apollographql.ijplugin.telemetry.telemetryService
import com.apollographql.ijplugin.util.dispose
import com.apollographql.ijplugin.util.isNotDisposed
import com.apollographql.ijplugin.util.logd
import com.apollographql.ijplugin.util.logw
import com.apollographql.ijplugin.util.newDisposable
import com.apollographql.ijplugin.util.toJsonString
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.GradleProject
import java.io.File

const val GENERATE_PROJECT_MODEL_TASK_NAME = "generateApolloProjectModel"

/**
 * Manages the Apollo Kotlin 'project model' as configured in the project's Gradle build files.
 *
 * The model is fetched via Gradle and represented as a set of [ApolloKotlinService] instances in [apolloKotlinServices]. These are also
 * persisted in the project settings as a cache mechanism.
 */
@Service(Service.Level.PROJECT)
class ApolloKotlinProjectModelService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) : Disposable {
  private var gradleHasSyncedDisposable: CheckedDisposable? = null

  private var fetchProjectModelTask: FetchProjectModelTask? = null

  private var apolloKotlinServices: Map<ApolloKotlinService.Id, ApolloKotlinService> =
    project.projectSettingsState.apolloKotlinServices.associateBy { it.id }

  init {
    logd("project=${project.name}")
    startObserveApolloProject()
    startOrStopObserveGradleHasSynced()
    startObserveSettings()

    logd("shouldFetchProjectModel=${shouldFetchProjectModel()}")
    if (shouldFetchProjectModel()) {
      // Contribute immediately, even though the ApolloKotlinServices are not available yet. They will be contributed later when available.
      // This avoids falling back to the default schema discovery of the GraphQL plugin which can be problematic (see https://github.com/apollographql/apollo-kotlin/issues/6219)
      project.messageBus.syncPublisher(ApolloKotlinServiceListener.TOPIC).apolloKotlinServicesAvailable()
    }
  }

  private fun startObserveApolloProject() {
    logd()
    project.messageBus.connect(this).subscribe(ApolloProjectListener.TOPIC, object : ApolloProjectListener {
      override fun apolloProjectChanged(apolloVersion: ApolloProjectService.ApolloVersion) {
        logd("apolloVersion=$apolloVersion")
        startOrStopObserveGradleHasSynced()
        startOrAbortFetchProjectModel()
      }
    })
  }

  private fun shouldFetchProjectModel() = project.apolloProjectService.apolloVersion.isAtLeastV4 &&
      project.projectSettingsState.contributeConfigurationToGraphqlPlugin

  private fun startOrStopObserveGradleHasSynced() {
    logd()
    if (shouldFetchProjectModel()) {
      startObserveGradleHasSynced()
    } else {
      stopObserveGradleHasSynced()
    }
  }

  private fun startObserveGradleHasSynced() {
    logd()
    if (gradleHasSyncedDisposable.isNotDisposed()) {
      logd("Already observing")
      return
    }
    val disposable = newDisposable()
    gradleHasSyncedDisposable = disposable
    project.messageBus.connect(disposable).subscribe(GradleHasSyncedListener.TOPIC, object : GradleHasSyncedListener {
      override fun gradleHasSynced() {
        logd()
        startOrAbortFetchProjectModel()
      }
    })
  }

  private fun stopObserveGradleHasSynced() {
    logd()
    dispose(gradleHasSyncedDisposable)
    gradleHasSyncedDisposable = null
  }

  private fun startObserveSettings() {
    logd()
    project.messageBus.connect(this).subscribe(ProjectSettingsListener.TOPIC, object : ProjectSettingsListener {
      private var contributeConfigurationToGraphqlPlugin: Boolean = project.projectSettingsState.contributeConfigurationToGraphqlPlugin

      override fun settingsChanged(projectSettingsState: ProjectSettingsState) {
        val contributeConfigurationToGraphqlPluginChanged =
          contributeConfigurationToGraphqlPlugin != projectSettingsState.contributeConfigurationToGraphqlPlugin
        contributeConfigurationToGraphqlPlugin = projectSettingsState.contributeConfigurationToGraphqlPlugin
        logd("contributeConfigurationToGraphqlPluginChanged=$contributeConfigurationToGraphqlPluginChanged")
        if (contributeConfigurationToGraphqlPluginChanged) {
          startOrAbortFetchProjectModel()
        }
      }
    })
  }

  fun triggerFetchProjectModel() {
    logd()
    startOrAbortFetchProjectModel()
  }

  private fun startOrAbortFetchProjectModel() {
    logd()
    abortFetchProjectModel()
    if (shouldFetchProjectModel()) {
      fetchProjectModel()
    }
  }

  /**
   * Fetch the Apollo Kotlin project model, either by fetching the JSON models generated by the Apollo Gradle plugin
   * (`generateApolloProjectModel` task introduced in v5) or by fetching the Gradle tooling models (legacy).
   *
   * This populates [apolloKotlinServices].
   */
  private fun fetchProjectModel() {
    logd()

    if (fetchProjectModelTask?.gradleCancellation != null) {
      logd("Already running")
      return
    }

    fetchProjectModelTask = FetchProjectModelTask().also { coroutineScope.launch { it.run() } }
  }

  private class CompilationUnitModelWithOptions(
      val compilationUnitModel: CompilationUnitModel,
      val codegenSchemaOptionsFile: File,
      val irOptionsFile: File,
      val codegenOptionsFile: File,
      val codegenOutputDir: File,
      val operationManifestFile: File,
      val dataBuildersOutputDir: File,
  )

  private inner class FetchProjectModelTask : Runnable {
    var abortRequested: Boolean = false
    var gradleCancellation: CancellationTokenSource? = null

    override fun run() {
      try {
        doRun()
      } finally {
        fetchProjectModelTask = null
      }
    }

    private fun doRun() {
      val allApolloGradleProjects = getAllApolloGradleProjects()
      if (allApolloGradleProjects == null) {
        logw("Failed to fetch Gradle project model, aborting")
        return
      }
      val projectModelsFetched = fetchProjectModels(allApolloGradleProjects)
      logd("projectModelsFetched=$projectModelsFetched")
      if (!projectModelsFetched) {
        logd("Failed to fetch project models, fall back to fetching tooling models")
        fetchToolingModels(allApolloGradleProjects)
      }
      return
    }

    /**
     * Fetch the project model via JSON files generated by the Apollo Gradle plugin (the `generateApolloProjectModel` task).
     * This is the preferred way, introduced in Apollo Kotlin v5, which is more efficient than fetching Gradle tooling models.
     */
    private fun fetchProjectModels(allApolloGradleProjects: List<GradleProject>): Boolean {
      logd()
      gradleCancellation = GradleConnector.newCancellationTokenSource()
      logd("Start Gradle $GENERATE_PROJECT_MODEL_TASK_NAME task")
      var projectModelsFetched = false
      try {
        val cancellationToken = gradleCancellation!!.token()
        val gradleProjectPath = project.getGradleRootPath()
        if (gradleProjectPath == null) {
          logw("Could not get Gradle root project path")
          return false
        }
        runGradleBuild(project, gradleProjectPath) { buildLauncher ->
          buildLauncher.forTasks(GENERATE_PROJECT_MODEL_TASK_NAME)
              .withCancellationToken(cancellationToken)
              .addProgressListener(object : SimpleProgressListener() {
                override fun onSuccess() {
                  logd("Gradle $GENERATE_PROJECT_MODEL_TASK_NAME task success, reading models")
                  val readProjectModels = readProjectModels(allApolloGradleProjects)
                  logd("readProjectModels=$readProjectModels")
                  if (!readProjectModels) {
                    logw("Failed to read project models, falling back to fetching tooling models")
                    fetchToolingModels(allApolloGradleProjects)
                  } else {
                    projectModelsFetched = true
                  }
                }
              })
        }
        logd("Gradle $GENERATE_PROJECT_MODEL_TASK_NAME task finished")
      } catch (t: Throwable) {
        logw(t, "Gradle $GENERATE_PROJECT_MODEL_TASK_NAME task failed")
      } finally {
        gradleCancellation = null
      }
      return projectModelsFetched
    }

    private fun readProjectModels(allApolloGradleProjects: List<GradleProject>): Boolean {
      val allCompilationUnitModels = mutableListOf<CompilationUnitModelWithOptions>()
      var apolloTasksDependencies: Set<String> = emptySet()
      val allTelemetryData = mutableListOf<TelemetryData>()
      for (gradleProject in allApolloGradleProjects) {
        val projectDirectory = gradleProject.projectDirectory
        val projectModel = readProjectModel(projectDirectory)
        if (projectModel == null) {
          logw("Failed to read project model from $projectDirectory")
          return false
        }

        val telemetryData = readTelemetryData(projectDirectory)
        if (telemetryData != null) {
          allTelemetryData.add(telemetryData)
        } else {
          logw("Failed to read telemetry data from $projectDirectory")
        }

        val compilationUnitModels = readCompilationUnitModels(projectDirectory, projectModel)
        if (compilationUnitModels == null) {
          logw("Failed to read compilation unit models from $projectDirectory")
          return false
        }
        allCompilationUnitModels.addAll(compilationUnitModels)

        // The apolloTasksDependencies should be the same for all projects
        apolloTasksDependencies = projectModel.apolloTasksDependencies
      }
      val apolloKotlinServices = compilationUnitModelsToApolloKotlinServices(allCompilationUnitModels)

      project.telemetryService.telemetryProperties = allTelemetryData.flatMap { it.toTelemetryProperties() }.toSet() +
          apolloKotlinServices.flatMap { it.toTelemetryProperties() }.toSet()

      saveApolloKotlinServices(apolloKotlinServices, apolloTasksDependencies)
      return true
    }

    private fun readProjectModel(projectDirectory: File): ProjectModel? {
      val projectModelFile = projectModelFile(projectDirectory)
      if (!projectModelFile.exists()) {
        logw("Project model file does not exist: $projectModelFile")
        return null
      }
      return projectModelFile.toProjectModel()
    }

    private fun readTelemetryData(projectDirectory: File): TelemetryData? {
      val telemetryDataFile = telemetryDataFile(projectDirectory)
      if (!telemetryDataFile.exists()) {
        logw("Telemetry data file does not exist: $telemetryDataFile")
        return null
      }
      return telemetryDataFile.toTelemetryData()
    }

    private fun readCompilationUnitModels(projectDirectory: File, projectModel: ProjectModel): List<CompilationUnitModelWithOptions>? {
      return projectModel.serviceNames.map { serviceName ->
        val compilationUnitModelFile = compilationUnitModelFile(projectDirectory, serviceName).also {
          if (!it.exists()) {
            logw("Compilation unit model file does not exist: $it")
            return@readCompilationUnitModels null
          }
        }

        val codegenSchemaOptionsFile = codegenSchemaOptionsFile(projectDirectory, serviceName).also {
          if (!it.exists()) {
            logw("Codegen schema options file does not exist: $it")
            return@readCompilationUnitModels null
          }
        }

        val irOptionsFile = irOptionsFile(projectDirectory, serviceName).also {
          if (!it.exists()) {
            logw("IR options file does not exist: $it")
            return@readCompilationUnitModels null
          }
        }

        val codegenOptionsFile = codegenOptionsFile(projectDirectory, serviceName).also {
          if (!it.exists()) {
            logw("Codegen options file does not exist: $it")
            return@readCompilationUnitModels null
          }
        }

        CompilationUnitModelWithOptions(
            compilationUnitModel = compilationUnitModelFile.toCompilationUnitModel(),
            codegenSchemaOptionsFile = codegenSchemaOptionsFile,
            irOptionsFile = irOptionsFile,
            codegenOptionsFile = codegenOptionsFile,
            codegenOutputDir = codegenOutputDir(projectDirectory, serviceName),
            operationManifestFile = operationManifestFile(projectDirectory, serviceName),
            dataBuildersOutputDir = dataBuildersOutputDir(projectDirectory, serviceName),
        )
      }
    }

    /**
     * Fetch the project model via Gradle tooling models exposed by the Apollo Gradle plugin.
     * This is the legacy (Apollo Kotlin v3 and v4) way which is not efficient because it needs to iterate over all Gradle projects.
     */
    private fun fetchToolingModels(allApolloGradleProjects: List<GradleProject>) {
      logd()
      val allToolingModels = allApolloGradleProjects.mapNotNull { gradleProject ->
        if (isAbortRequested()) return

        gradleCancellation = GradleConnector.newCancellationTokenSource()
        logd("Fetch tooling model for ${gradleProject.path}")
        try {
          getGradleModel<ApolloGradleToolingModel>(project, gradleProject.projectDirectory.canonicalPath) {
            it.withCancellationToken(gradleCancellation!!.token())
          }
              ?.takeIf {
                val isCompatibleVersion = it.versionMajor == ApolloGradleToolingModel.VERSION_MAJOR
                if (!isCompatibleVersion) {
                  logw("Incompatible version of Apollo Gradle plugin in module ${gradleProject.path}: ${it.versionMajor} != ${ApolloGradleToolingModel.VERSION_MAJOR}, ignoring")
                }
                isCompatibleVersion
              }
        } catch (t: Throwable) {
          logw(t, "Couldn't fetch tooling model for ${gradleProject.path}")
          null
        } finally {
          gradleCancellation = null
        }
      }

      logd("allToolingModels=$allToolingModels")
      project.telemetryService.telemetryProperties = allToolingModels.flatMap { it.toTelemetryProperties() }.toSet()
      if (isAbortRequested()) return

      val apolloKotlinServices = toolingModelsToApolloKotlinServices(allToolingModels)
      saveApolloKotlinServices(apolloKotlinServices, emptySet())
    }

    private fun getAllApolloGradleProjects(): List<GradleProject>? {
      logd("Fetch Gradle project model")
      gradleCancellation = GradleConnector.newCancellationTokenSource()
      val gradleProjectPath = project.getGradleRootPath()
      if (gradleProjectPath == null) {
        logw("Could not get Gradle root project path")
        return null
      }
      val rootGradleProject: GradleProject = try {
        getGradleModel(project, gradleProjectPath) {
          it.withCancellationToken(gradleCancellation!!.token())
        }
      } catch (t: Throwable) {
        logw(t, "Couldn't fetch Gradle project model")
        null
      } finally {
        gradleCancellation = null
      } ?: return null
      project.telemetryService.gradleModuleCount = rootGradleProject.children.size + 1

      // We're only interested in projects that apply the Apollo plugin - and thus have the codegen task registered
      val allApolloGradleProjects: List<GradleProject> = rootGradleProject.allChildrenRecursively()
          .filter { gradleProject -> gradleProject.tasks.any { task -> task.name == CODEGEN_GRADLE_TASK_NAME } }
      logd("allApolloGradleProjects=${allApolloGradleProjects.map { it.path }}")

      project.telemetryService.apolloKotlinModuleCount = allApolloGradleProjects.size
      return allApolloGradleProjects
    }

    private fun isAbortRequested(): Boolean {
      if (abortRequested) {
        logd("Aborted")
        return true
      }
      try {
        ProgressManager.checkCanceled()
      } catch (@Suppress("IncorrectCancellationExceptionHandling") _: ProcessCanceledException) {
        logd("Canceled by user")
        return true
      }
      return false
    }
  }

  /**
   * Compute the ApolloKotlinServices from service models, taking into account the dependencies between projects.
   */
  private fun compilationUnitModelsToApolloKotlinServices(compilationUnitModels: List<CompilationUnitModelWithOptions>): List<ApolloKotlinService> {
    val projectServiceToApolloKotlinServices = mutableMapOf<String, ApolloKotlinService>()

    fun getApolloKotlinService(projectPath: String, serviceName: String): ApolloKotlinService {
      val key = "$projectPath/$serviceName"
      return projectServiceToApolloKotlinServices.getOrPut(key) {
        val compilationUnitModelWithOptions =
          compilationUnitModels.first { it.compilationUnitModel.gradleProjectPath == projectPath && it.compilationUnitModel.serviceName == serviceName }
        val compilationUnitModel = compilationUnitModelWithOptions.compilationUnitModel
        val upstreamApolloKotlinServices = compilationUnitModel.upstreamGradleProjectPaths
            .map { upstreamProjectPath -> getApolloKotlinService(upstreamProjectPath, serviceName) }
        ApolloKotlinService(
            gradleProjectPath = projectPath,
            serviceName = serviceName,
            schemaPaths = compilationUnitModel.schemaFiles.toList(),
            allSchemaPaths = (compilationUnitModel.schemaFiles.toList() +
                upstreamApolloKotlinServices.flatMap { it.allSchemaPaths })
                .distinct(),
            operationPaths = compilationUnitModel.graphqlSrcDirs.toList(),
            allOperationPaths = (compilationUnitModel.graphqlSrcDirs.toList() +
                upstreamApolloKotlinServices.flatMap { it.allOperationPaths })
                .distinct(),
            endpointUrl = compilationUnitModel.endpointUrl,
            endpointHeaders = compilationUnitModel.endpointHeaders,
            upstreamServiceIds = upstreamApolloKotlinServices.map { it.id },
            downstreamServiceIds = compilationUnitModel.downstreamGradleProjectPaths.map { downstreamProjectPath -> ApolloKotlinService.Id(downstreamProjectPath, serviceName) },
            useSemanticNaming = compilationUnitModelWithOptions.codegenOptionsFile.toCodegenOptions().useSemanticNaming ?: true,

            codegenSchemaOptionsFilePath = compilationUnitModelWithOptions.codegenSchemaOptionsFile.path,
            irOptionsFilePath = compilationUnitModelWithOptions.irOptionsFile.path,
            codegenOptionsFilePath = compilationUnitModelWithOptions.codegenOptionsFile.path,
            pluginDependencies = compilationUnitModelWithOptions.compilationUnitModel.pluginDependencies.toList(),
            pluginArgumentsJson = compilationUnitModelWithOptions.compilationUnitModel.pluginArguments.toJsonString(),

            codegenOutputDirPath = compilationUnitModelWithOptions.codegenOutputDir.path,
            operationManifestFilePath = compilationUnitModelWithOptions.operationManifestFile.path,
            dataBuildersOutputDirPath = compilationUnitModelWithOptions.dataBuildersOutputDir.path,
        )
      }
    }

    val apolloKotlinServices = mutableListOf<ApolloKotlinService>()
    for (compilationUnitModel in compilationUnitModels) {
      apolloKotlinServices += getApolloKotlinService(compilationUnitModel.compilationUnitModel.gradleProjectPath, compilationUnitModel.compilationUnitModel.serviceName)
    }
    logd("apolloKotlinServices=\n${apolloKotlinServices.joinToString(",\n")}")
    return apolloKotlinServices
  }

  /**
   * Compute the ApolloKotlinServices from Gradle Tooling Models, taking into account the dependencies between projects.
   */
  private fun toolingModelsToApolloKotlinServices(toolingModels: List<ApolloGradleToolingModel>): List<ApolloKotlinService> {
    val allKnownProjectPaths = toolingModels.map { it.projectPathCompat }
    val projectServiceToApolloKotlinServices = mutableMapOf<String, ApolloKotlinService>()

    fun getApolloKotlinService(projectPath: String, serviceName: String): ApolloKotlinService {
      val key = "$projectPath/$serviceName"
      return projectServiceToApolloKotlinServices.getOrPut(key) {
        val toolingModel = toolingModels.first { it.projectPathCompat == projectPath }
        val serviceInfo = toolingModel.serviceInfos.first { it.name == serviceName }
        val upstreamApolloKotlinServices = serviceInfo.upstreamProjectPathsCompat(toolingModel)
            // The tooling model for some upstream projects might not have been fetched successfully - filter them out
            .filter { upstreamProjectPath -> upstreamProjectPath in allKnownProjectPaths }
            .map { upstreamProjectPath -> getApolloKotlinService(upstreamProjectPath, serviceName) }
        ApolloKotlinService(
            gradleProjectPath = projectPath,
            serviceName = serviceName,
            schemaPaths = serviceInfo.schemaFiles.map { it.absolutePath },
            allSchemaPaths = (serviceInfo.schemaFiles.map { it.absolutePath } +
                upstreamApolloKotlinServices.flatMap { it.allSchemaPaths })
                .distinct(),
            operationPaths = serviceInfo.graphqlSrcDirs.map { it.absolutePath },
            allOperationPaths = (serviceInfo.graphqlSrcDirs.map { it.absolutePath } +
                upstreamApolloKotlinServices.flatMap { it.allOperationPaths })
                .distinct(),
            endpointUrl = serviceInfo.endpointUrlCompat(toolingModel),
            endpointHeaders = serviceInfo.endpointHeadersCompat(toolingModel),
            upstreamServiceIds = upstreamApolloKotlinServices.map { it.id },
            useSemanticNaming = serviceInfo.useSemanticNamingCompat(toolingModel),
        )
      }
    }

    val apolloKotlinServices = mutableListOf<ApolloKotlinService>()
    for (toolingModel in toolingModels) {
      for (serviceInfo in toolingModel.serviceInfos) {
        apolloKotlinServices += getApolloKotlinService(toolingModel.projectPathCompat, serviceInfo.name)
      }
    }
    logd("apolloKotlinServices=\n${apolloKotlinServices.joinToString(",\n")}")
    return apolloKotlinServices
  }

  private fun saveApolloKotlinServices(apolloKotlinServices: List<ApolloKotlinService>, apolloTasksDependencies: Set<String>) {
    this@ApolloKotlinProjectModelService.apolloKotlinServices = apolloKotlinServices.associateBy { it.id }
    // Cache the ApolloKotlinServices into the project settings
    project.projectSettingsState.apolloKotlinServices = apolloKotlinServices
    project.projectSettingsState.apolloTasksDependencies = apolloTasksDependencies.toList()

    // Services are available, notify interested parties
    project.messageBus.syncPublisher(ApolloKotlinServiceListener.TOPIC).apolloKotlinServicesAvailable()
  }

  private fun abortFetchProjectModel() {
    logd()
    fetchProjectModelTask?.abortRequested = true
    fetchProjectModelTask?.gradleCancellation?.cancel()
    fetchProjectModelTask = null
  }

  override fun dispose() {
    logd("project=${project.name}")
    abortFetchProjectModel()
  }

  fun getApolloKotlinServices(): List<ApolloKotlinService> {
    return apolloKotlinServices.values.toList()
  }

  fun getApolloKotlinService(id: ApolloKotlinService.Id): ApolloKotlinService? {
    return apolloKotlinServices[id]
  }
}

private val ApolloGradleToolingModel.projectPathCompat: String
  get() = if (versionMinor >= 3) {
    projectPath
  } else {
    @Suppress("DEPRECATION")
    projectName
  }

private fun ApolloGradleToolingModel.ServiceInfo.upstreamProjectPathsCompat(toolingModel: ApolloGradleToolingModel) =
  if (toolingModel.versionMinor >= 3) {
    upstreamProjectPaths
  } else {
    @Suppress("DEPRECATION")
    upstreamProjects
  }

private fun ApolloGradleToolingModel.ServiceInfo.endpointUrlCompat(toolingModel: ApolloGradleToolingModel) =
  if (toolingModel.versionMinor >= 1) endpointUrl else null

private fun ApolloGradleToolingModel.ServiceInfo.endpointHeadersCompat(toolingModel: ApolloGradleToolingModel) =
  if (toolingModel.versionMinor >= 1) endpointHeaders else null

private fun ApolloGradleToolingModel.ServiceInfo.useSemanticNamingCompat(toolingModel: ApolloGradleToolingModel) =
  if (toolingModel.versionMinor >= 4) useSemanticNaming else true

val Project.apolloKotlinProjectModelService get() = service<ApolloKotlinProjectModelService>()
