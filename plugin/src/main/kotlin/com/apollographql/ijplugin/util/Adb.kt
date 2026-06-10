package com.apollographql.ijplugin.util

import com.android.adblib.ConnectedDevice
import com.android.adblib.ShellCommandOutput
import com.android.adblib.shell
import kotlinx.coroutines.runBlocking

suspend fun ConnectedDevice.executeShellCommand(command: String): ShellCommandOutput {
  logd("Executing adb shell command: '$command'")
  val output = shell.executeAsText(command)
  logd("adb shell command result: exitCode=${output.exitCode}, stdout=${output.stdout}, stderr=${output.stderr}")
  return output
}

fun ConnectedDevice.executeShellCommandCatching(command: String): Result<ShellCommandOutput> = runCatching {
  runBlocking { executeShellCommand(command) }
}

val ShellCommandOutput.isError: Boolean get() = exitCode != 0
