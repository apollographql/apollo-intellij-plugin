package com.apollographql.ijplugin.migration.util

import java.io.File

fun File.getFilesWithExtension(extensions: Set<String>): List<File> {
  return walk().filter { it.isFile && it.extension in extensions }.toList()
}
