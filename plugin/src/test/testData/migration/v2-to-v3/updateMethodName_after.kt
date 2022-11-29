package com.example

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Mutation

suspend fun main() {
  val apolloClient = ApolloClient.Builder()
    .serverUrl("http://example.com")
    .build()

  val myMutation: Mutation<*, *, *>? = null
  apolloClient.mutation(myMutation!!).execute()
}
