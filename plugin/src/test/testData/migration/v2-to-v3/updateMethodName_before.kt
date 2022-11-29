package com.example

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.coroutines.await

suspend fun main() {
  val apolloClient = ApolloClient.builder()
    .serverUrl("http://example.com")
    .build()

  val myMutation: Mutation<*, *, *>? = null
  apolloClient.mutate(myMutation!!).await()
}
