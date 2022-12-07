package com.example

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Query

suspend fun main() {
  val apolloClient: ApolloClient? = null
  val myQuery: Query<*, *, *>? = null
  val flow = apolloClient!!.query(myQuery!!)
    .watch()
}
