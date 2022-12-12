package com.example

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.api.cache.http.HttpCachePolicy
import com.apollographql.apollo.cache.http.ApolloHttpCache
import com.apollographql.apollo.cache.http.DiskLruHttpCacheStore
import com.apollographql.apollo.cache.http.internal.FileSystem
import java.io.File

suspend fun main() {
  val maxSize = 10000L
  val apolloHttpCache1 = ApolloHttpCache(DiskLruHttpCacheStore(File(""), maxSize))
  val apolloHttpCache2 = ApolloHttpCache(DiskLruHttpCacheStore(FileSystem.SYSTEM, File(""), maxSize))
  val apolloHttpCache3: ApolloHttpCache? = null

  val apolloClient = ApolloClient.builder()
    .httpCache(ApolloHttpCache(DiskLruHttpCacheStore(File(""), maxSize)))
    .httpCache(ApolloHttpCache(DiskLruHttpCacheStore(FileSystem.SYSTEM, File(""), maxSize)))
    .httpCache(apolloHttpCache1)
    .httpCache(apolloHttpCache2)
    .httpCache(apolloHttpCache3!!)
    .build()

  val myQuery: Query<*, *, *>? = null
  apolloClient!!
    .query(myQuery!!)
    .toBuilder()
    .httpCachePolicy(HttpCachePolicy.NETWORK_ONLY)
    .build()
}
