package com.example

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.fetcher.ApolloResponseFetchers

suspend fun main() {
  val apolloClient: ApolloClient? = null
  val myQuery: Query<*, *, *>? = null
  apolloClient!!
    .query(myQuery!!)
    .toBuilder()
    .responseFetcher(ApolloResponseFetchers.NETWORK_ONLY)
    .build()
}
