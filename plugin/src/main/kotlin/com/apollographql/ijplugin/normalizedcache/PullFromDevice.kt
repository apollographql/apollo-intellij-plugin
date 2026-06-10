package com.apollographql.ijplugin.normalizedcache

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceSelector
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.serialNumber
import com.android.adblib.syncRecv
import com.android.tools.idea.adblib.AdbLibApplicationService
import com.apollographql.ijplugin.util.executeShellCommand
import com.apollographql.ijplugin.util.executeShellCommandCatching
import com.apollographql.ijplugin.util.isError
import com.apollographql.ijplugin.util.logd
import com.apollographql.ijplugin.util.logw
import java.io.File
import java.nio.file.Paths

fun getConnectedDevices(): List<ConnectedDevice> {
  return AdbLibApplicationService.instance.session
      .connectedDevicesTracker
      .connectedDevices.value
      .sortedBy { it.serialNumber }
}

fun ConnectedDevice.getDebuggablePackageList(): Result<List<String>> {
  val commandResult = executeShellCommandCatching(
      // List all packages, and try run-as on them - if it succeeds, the package is debuggable
      "for p in \$(pm list packages -3 | cut -d : -f 2); do (run-as \$p true >/dev/null 2>&1 && echo \$p); done; true"
  )
  if (commandResult.isFailure) {
    val e = commandResult.exceptionOrNull()!!
    logw(e, "Could not list debuggable packages")
    return Result.failure(e)
  }
  val result = commandResult.getOrThrow()
  if (result.isError) {
    val message = "Could not list debuggable packages: ${result.stderr}"
    logw(message)
    return Result.failure(Exception(message))
  }
  return Result.success(result.stdout.lines().filterNot { it.isEmpty() }.sorted())
}

fun ConnectedDevice.getDatabaseList(packageName: String, databasesDir: String): Result<List<String>> {
  val commandResult = executeShellCommandCatching("run-as $packageName ls -1 $databasesDir")
  if (commandResult.isFailure) {
    val e = commandResult.exceptionOrNull()!!
    logw(e, "Could not list databases")
    return Result.failure(e)
  }
  val result = commandResult.getOrThrow()
  if (result.isError) {
    if (result.stderr.contains("No such file or directory") || result.stdout.contains("No such file or directory")) {
      return Result.success(emptyList())
    }
    val message = "Could not list databases: ${result.stderr}"
    logw(message)
    return Result.failure(Exception(message))
  }
  return Result.success(result.stdout.lines().filter { it.isDatabaseFileName() }.sorted())
}

fun ConnectedDevice.getDatabaseList(packageName: String, databasesDirs: List<String>): Result<List<Pair<String, String>>> {
  val allDatabases = mutableListOf<Pair<String, String>>()
  var atLeastOneSuccess = false
  for (databasesDir in databasesDirs) {
    val result = getDatabaseList(packageName, databasesDir)
    if (result.isFailure) {
      continue
    }
    atLeastOneSuccess = true
    allDatabases.addAll(result.getOrThrow().map { databasesDir to it })
  }
  return if (atLeastOneSuccess) {
    Result.success(allDatabases)
  } else {
    Result.failure(Exception("Could not list databases from any of the provided directories"))
  }
}

suspend fun pullFile(device: ConnectedDevice, appPackageName: String, remoteDirName: String, remoteFileName: String): Result<File> {
  val remoteFilePath = "$remoteDirName/$remoteFileName"
  val localFile = File.createTempFile(remoteFileName.substringBeforeLast(".") + "-tmp", ".db")
  logd("Pulling $remoteFilePath to ${localFile.absolutePath}")
  val intermediateRemoteFilePath = "/data/local/tmp/${localFile.name}"
  return runCatching {
    var commandResult = device.executeShellCommand("touch $intermediateRemoteFilePath")
    if (commandResult.isError) {
      throw Exception("'touch' command failed")
    }
    commandResult = device.executeShellCommand("run-as $appPackageName sh -c 'cp $remoteFilePath $intermediateRemoteFilePath'")
    if (commandResult.isError) {
      throw Exception("'copy' command failed")
    }
    try {
      val adbLibSession = AdbLibApplicationService.instance.session
      val fileChannel = adbLibSession.channelFactory.createFile(Paths.get(localFile.absolutePath))
      fileChannel.use {
        adbLibSession.deviceServices.syncRecv(DeviceSelector.fromSerialNumber(device.serialNumber), intermediateRemoteFilePath, fileChannel)
      }
    } finally {
      commandResult = device.executeShellCommand("rm $intermediateRemoteFilePath")
      if (commandResult.isError) {
        logw("'rm' command failed")
      }
    }
    localFile
  }
}

// See https://www.sqlite.org/tempfiles.html
fun String.isDatabaseFileName() = isNotEmpty() && !endsWith("-journal") && !endsWith("-wal") && !endsWith("-shm")
