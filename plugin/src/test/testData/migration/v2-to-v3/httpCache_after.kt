package com.example

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.cache.http.HttpFetchPolicy

suspend fun main() {
  val apolloClient: ApolloClient? = null
  val myQuery: Query<*, *, *>? = null
    apolloClient!!
      .query(myQuery!!)
      .httpFetchPolicy(com.apollographql.apollo3.cache.http.HttpFetchPolicy.NetworkOnly)
}
