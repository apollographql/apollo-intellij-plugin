package com.example

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.ApolloMutationCall
import com.apollographql.apollo3.api.ApolloResponse

suspend fun main() {
  val apolloClient = ApolloClient.Builder()
    .serverUrl("http://example.com")
    .build()

  val call: ApolloMutationCall<MyMutation1Mutation.Data> = apolloClient.mutation(MyMutation1Mutation())
  val response: com.apollographql.apollo3.api.ApolloResponse<MyMutation1Mutation.Data> = call

  println(response.data)
}
