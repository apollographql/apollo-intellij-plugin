package com.apollographql.ijplugin.codegen.helper

import com.apollographql.ijplugin.gradle.ApolloKotlinService
import com.apollographql.ijplugin.util.logw
import com.intellij.openapi.project.Project
import java.io.File
import java.net.URL
import java.net.URLClassLoader

/**
 * Invokes [ApolloCompilerHelper] via introspection and a dedicated classloader using the project's dependencies.
 * This ensures the same version of the Apollo libraries as the project's are used to generate the code.
 */
class DynamicApolloCompilerHelper(
    private val project: Project,
    private val apolloTasksDependencies: Set<String>,
) {
  private lateinit var apolloCompilerHelperClass: Class<*>
  private lateinit var instance: Any

  private fun init() {
    if (this::instance.isInitialized && this::apolloCompilerHelperClass.isInitialized) return
    val dependencies = apolloTasksDependencies.map { File(it.trim()).toURI().toURL() }
    val classLoader = ChildFirstClassLoader(dependencies.toTypedArray<URL>(), ApolloCompilerHelper::class.java.classLoader)
    apolloCompilerHelperClass = classLoader.loadClass(ApolloCompilerHelper::class.java.name)
    instance = apolloCompilerHelperClass.getDeclaredConstructor(Project::class.java).newInstance(project)
  }

  fun generateAllSources() {
    try {
      init()
      val method = apolloCompilerHelperClass.getMethod("generateAllSources")
      method.invoke(instance)
    } catch (e: Exception) {
      logw(e, "Failed to generate sources for all services")
    }
  }

  fun generateSources(service: ApolloKotlinService) {
    try {
      init()
      val method = apolloCompilerHelperClass.getMethod("generateSources", ApolloKotlinService::class.java)
      method.invoke(instance, service)
    } catch (e: Exception) {
      logw(e, "Failed to generate sources for service ${service.id}")
    }
  }
}

private class ChildFirstClassLoader(urls: Array<URL>, parent: ClassLoader) : URLClassLoader(urls, parent) {
  override fun loadClass(name: String, resolve: Boolean): Class<*> {
    val loadedClass = findLoadedClass(name)
    if (loadedClass != null) return loadedClass

    // Load the helper from this classloader, but read the classes in the parent
    if (name.startsWith("com.apollographql.ijplugin.codegen.helper")) {
      val resourceName = name.replace('.', '/') + ".class"
      val bytes = parent.getResourceAsStream(resourceName)?.readBytes()
          ?: throw ClassNotFoundException("Could not read $name from parent classloader")
      val clazz = defineClass(name, bytes, 0, bytes.size)
      if (resolve) resolveClass(clazz)
      return clazz
    }

    return try {
      val clazz = findClass(name)
      if (resolve) resolveClass(clazz)
      clazz
    } catch (_: ClassNotFoundException) {
      // Fallback to parent
      super.loadClass(name, resolve)
    }
  }
}
