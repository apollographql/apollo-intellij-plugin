package com.apollographql.ijplugin.util

import com.intellij.openapi.diagnostic.Logger

// To see debug logs, in the embedded IntelliJ app, go to Help / Diagnostic Tools / Debug Log Settings and add "Apollo"
// or pass -Didea.log.debug.categories=ApolloKotlin to the VM.
// See https://plugins.jetbrains.com/docs/intellij/ide-infrastructure.html#logging
// and https://plugins.jetbrains.com/docs/intellij/testing-faq.html#how-to-enable-debugtrace-logging
private val logger = Logger.getInstance("Apollo")

fun logd(message: String) {
  logger.debug(message)
}

fun logd(any: Any) {
  logger.debug(any.toString())
}

fun logd(throwable: Throwable) {
  logger.debug(throwable)
}

fun logd(throwable: Throwable, message: String) {
  logger.debug(message, throwable)
}

fun logd(throwable: Throwable, any: Any) {
  logger.debug(any.toString(), throwable)
}

fun logw(message: String) {
  logger.warn(message)
}

fun logw(any: Any) {
  logger.warn(any.toString())
}

fun logw(throwable: Throwable) {
  logger.warn(throwable)
}

fun logw(throwable: Throwable, message: String) {
  logger.warn(message, throwable)
}

fun logw(throwable: Throwable, any: Any) {
  logger.warn(any.toString(), throwable)
}

