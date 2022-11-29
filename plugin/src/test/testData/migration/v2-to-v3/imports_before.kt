package com.example

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.ApolloMutationCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.coroutines.await

suspend fun main() {
  val apolloClient = ApolloClient.builder()
    .serverUrl("http://example.com")
    .build()

  val call: ApolloMutationCall<MyMutation1Mutation.Data> = apolloClient.mutate(MyMutation1Mutation())
  val response: Response<MyMutation1Mutation.Data> = call.await()

  println(response.data)
}
