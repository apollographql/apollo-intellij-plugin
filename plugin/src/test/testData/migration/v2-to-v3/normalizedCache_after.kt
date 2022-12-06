package com.example

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.internal.ResponseFieldMarshaller
import com.apollographql.apollo3.cache.normalized.FetchPolicy

suspend fun main() {
  class MyData : Operation.Data {
    override fun marshaller(): ResponseFieldMarshaller = TODO()
  }

  class MyVariables : Operation.Variables()

  val apolloClient: ApolloClient? = null
  val myQuery: Query<MyData, Any, MyVariables>? = null

  apolloClient!!
    .query(myQuery!!)
    .fetchPolicy(com.apollographql.apollo3.cache.normalized.FetchPolicy.NetworkOnly)

  val cachedData = apolloClient
    .apolloStore
    .readOperation(myQuery)

  val data: MyData? = null
  apolloClient
    .apolloStore
    .writeOperation(myQuery, data!!)
}
