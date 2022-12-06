package com.example

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.api.internal.ResponseFieldMarshaller
import com.apollographql.apollo.fetcher.ApolloResponseFetchers

suspend fun main() {
  class MyData : Operation.Data {
    override fun marshaller(): ResponseFieldMarshaller = TODO()
  }

  class MyVariables : Operation.Variables()

  val apolloClient: ApolloClient? = null
  val myQuery: Query<MyData, Any, MyVariables>? = null

  apolloClient!!
    .query(myQuery!!)
    .toBuilder()
    .responseFetcher(ApolloResponseFetchers.NETWORK_ONLY)
    .build()

  val cachedData = apolloClient
    .apolloStore
    .read(myQuery)
    .execute()

  val data: MyData? = null
  apolloClient
    .apolloStore
    .writeAndPublish(myQuery, data!!)
    .execute()
}
