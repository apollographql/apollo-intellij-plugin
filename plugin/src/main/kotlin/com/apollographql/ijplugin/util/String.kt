package com.apollographql.ijplugin.util

fun String.quoted(): String {
  return if (this.startsWith('"') && this.endsWith('"')) this else "\"$this\""
}

fun String.unquoted(): String {
  return if (this.startsWith('"') && this.endsWith('"')) this.substring(1, this.length - 1) else this
}
