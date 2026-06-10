package com.apollographql.ijplugin.apollodebugserver

import com.android.adblib.ConnectedDevice
import com.android.adblib.SocketSpec
import com.android.adblib.selector
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.network.NetworkTransport
import com.apollographql.apollo.network.http.LoggingInterceptor
import com.apollographql.ijplugin.util.apollo3
import com.apollographql.ijplugin.util.apollo4
import com.apollographql.ijplugin.util.executeShellCommandCatching
import com.apollographql.ijplugin.util.isError
import com.apollographql.ijplugin.util.logd
import com.apollographql.ijplugin.util.logw
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import java.io.Closeable

private const val SOCKET_NAME_PREFIX = "apollo_debug_"
private const val BASE_PORT = 12200

class ApolloDebugClient(
    private val device: ConnectedDevice,
    val packageName: String,
) : Closeable {
  companion object {
    private var uniquePort = 0

    private fun getUniquePort(): Int {
      return BASE_PORT + uniquePort++
    }

    private fun ConnectedDevice.getApolloDebugPackageList(): Result<List<String>> {
      val commandResult = executeShellCommandCatching("cat /proc/net/unix | grep $SOCKET_NAME_PREFIX | cat")
      if (commandResult.isFailure) {
        val e = commandResult.exceptionOrNull()!!
        logw(e, "Could not list Apollo Debug packages")
        return Result.failure(e)
      }
      val result = commandResult.getOrThrow()
      if (result.isError) {
        val message = "Could not list Apollo Debug packages: ${result.stderr}"
        logw(message)
        return Result.failure(Exception(message))
      }
      // Results are in the form:
      // 0000000000000000: 00000002 00000000 00010000 0001 01 116651 @apollo_debug_com.example.myapplication
      val packageList = result.stdout.lines()
          .filter { it.contains(SOCKET_NAME_PREFIX) }
          .map { it.substringAfterLast(SOCKET_NAME_PREFIX) }
          .sorted()
      logd("Found Apollo Debug packages: $packageList")
      return Result.success(packageList)
    }

    fun ConnectedDevice.getApolloDebugClients(): Result<List<ApolloDebugClient>> {
      return getApolloDebugPackageList().map { packageNames ->
        packageNames.map { packageName ->
          ApolloDebugClient(this, packageName)
        }
      }
    }
  }

  private val port = getUniquePort()
  private var hasPortForward: Boolean = false

  private val apolloClient = ApolloClient.Builder()
      .serverUrl("http://localhost:$port")
      .addHttpInterceptor(LoggingInterceptor { line ->
        logd("ApolloDebugClient HTTP - $line")
      })
      // Use a dummy NetworkTransport for subscriptions (which are not used), because the default one
      // references CoroutineDispatcher.limitedParallelism which doesn't exist in certain IDE versions.
      // (See https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#coroutinesLibraries)
      .subscriptionNetworkTransport(object : NetworkTransport {
        override fun <D : Operation.Data> execute(request: ApolloRequest<D>): Flow<ApolloResponse<D>> = emptyFlow()
        override fun dispose() {}
      })
      .build()

  private suspend fun createPortForward() {
    logd("Creating port forward to $port for $packageName")
    try {
      device.session.hostServices.forward(
          device = device.selector,
          local = SocketSpec.Tcp(port),
          remote = SocketSpec.LocalAbstract("$SOCKET_NAME_PREFIX$packageName"),
      )
    } catch (e: Exception) {
      logw(e, "Could not create port forward to $port for $packageName")
      throw e
    }
    hasPortForward = true
  }

  private suspend fun removePortForward() {
    logd("Removing port forward to $port for $packageName")
    try {
      device.session.hostServices.killForward(device.selector, SocketSpec.Tcp(port))
    } catch (e: Exception) {
      logw(e, "Could not remove port forward to $port for $packageName")
    }
    hasPortForward = false
  }

  private suspend fun ensurePortForward() {
    if (!hasPortForward) {
      createPortForward()
    }
  }

  suspend fun getApolloClients(): Result<List<GetApolloClientsQuery.ApolloClient>> = runCatching {
    ensurePortForward()
    apolloClient.query(GetApolloClientsQuery()).execute().dataOrThrow().apolloClients
  }

  suspend fun getNormalizedCache(
      apolloClientId: String,
      normalizedCacheId: String,
  ): Result<GetNormalizedCacheQuery.NormalizedCache> = runCatching {
    ensurePortForward()
    apolloClient.query(GetNormalizedCacheQuery(apolloClientId, normalizedCacheId)).execute()
        .dataOrThrow().apolloClient?.normalizedCache
        ?: error("No normalized cache returned by server")
  }

  override fun close() {
    if (hasPortForward) {
      runBlocking { removePortForward() }
    }
    apolloClient.close()
  }
}

val String.normalizedCacheSimpleName: String
  get() = when (this) {
    "$apollo3.cache.normalized.api.MemoryCache",
    "$apollo4.cache.normalized.api.MemoryCache",
    "com.apollographql.cache.normalized.memory.MemoryCache",
      -> "MemoryCache"

    "$apollo3.cache.normalized.sql.SqlNormalizedCache",
    "$apollo4.cache.normalized.sql.SqlNormalizedCache",
    "com.apollographql.cache.normalized.sql.SqlNormalizedCache",
      -> "SqlNormalizedCache"

    else -> this
  }
