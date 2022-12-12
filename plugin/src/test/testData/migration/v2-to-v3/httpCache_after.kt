package com.example

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.cache.http.HttpFetchPolicy
import com.apollographql.apollo3.cache.http.ApolloHttpCache
import java.io.File

suspend fun main() {
  val maxSize = 10000L
  val apolloHttpCache3: ApolloHttpCache? = null

  val apolloClient = ApolloClient.Builder()
    .httpCache(File(""), maxSize)
    .httpCache(File(""), maxSize)
    .httpCache(File(""), maxSize)
    .httpCache(File(""), maxSize)
    .httpCache(/* TODO: This could not be migrated automatically. Please check the migration guide at https://www.apollographql.com/docs/kotlin/migration/3.0/ */)
    .build()

  val myQuery: Query<*, *, *>? = null
  apolloClient!!
    .query(myQuery!!)
    .httpFetchPolicy(com.apollographql.apollo3.cache.http.HttpFetchPolicy.NetworkOnly)
}
