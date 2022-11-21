package com.apollographql.ijplugin.migration.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.simple.SimpleLogger

private val logger: Logger = run {
  System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE")
  System.setProperty(SimpleLogger.LOG_FILE_KEY, "System.out")
  System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true")
  System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd HH:mm:ss")
  LoggerFactory.getLogger("Apollo")
}

fun logd() {
  logger.debug(prefix(null))
}

fun logd(message: String?) {
  logger.debug(prefix(message))
}

fun logd(any: Any?) {
  logger.debug(prefix(any?.toString()))
}

fun logd(throwable: Throwable) {
  logger.debug("", throwable)
}

fun logd(throwable: Throwable, message: String) {
  logger.debug(prefix(message), throwable)
}

fun logd(throwable: Throwable, any: Any) {
  logger.debug(prefix(any.toString()), throwable)
}

fun logw(message: String) {
  logger.warn(message)
}

fun logi(any: Any) {
  logger.info(any.toString())
}

fun logi(throwable: Throwable) {
  logger.info("", throwable)
}

fun logi(throwable: Throwable, message: String) {
  logger.info(message, throwable)
}

fun logi(throwable: Throwable, any: Any) {
  logger.info(any.toString(), throwable)
}

fun logw(any: Any) {
  logger.warn(any.toString())
}

fun logw(throwable: Throwable) {
  logger.warn("", throwable)
}

fun logw(throwable: Throwable, message: String) {
  logger.warn(message, throwable)
}

fun logw(throwable: Throwable, any: Any) {
  logger.warn(any.toString(), throwable)
}

private fun getClassAndMethodName(): String {
  val stackTrace = Thread.currentThread().stackTrace
  val className = stackTrace[3].className.substringAfterLast('.')
  return className + " " + stackTrace[3].methodName
}

@Suppress("NOTHING_TO_INLINE")
private inline fun prefix(message: String?): String {
  return if (message == null) {
    getClassAndMethodName()
  } else {
    "${getClassAndMethodName()} - $message"
  }
}
