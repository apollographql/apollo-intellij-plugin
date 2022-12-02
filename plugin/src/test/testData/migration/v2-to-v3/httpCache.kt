package com.example

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.api.cache.http.HttpCachePolicy

suspend fun main() {
  val apolloClient: ApolloClient? = null
  val myQuery: Query<*, *, *>? = null
  apolloClient!!
    .query(myQuery!!)
    .toBuilder()
    .httpCachePolicy(HttpCachePolicy.NETWORK_ONLY)
    .build()
}
