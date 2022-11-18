package com.apollographql.ijplugin.migration

import com.apollographql.ijplugin.migration.step.MigrationStep
import java.io.File

class MigrationManager(
  val projectRoot: File,
  val dryRun: Boolean = true,
) {
  private val migrationSteps = mutableListOf<MigrationStep>()

  fun addStep(step: MigrationStep) {
    migrationSteps.add(step)
  }

  fun addSteps(vararg steps: MigrationStep) {
    migrationSteps.addAll(steps)
  }

  fun process() {
    for (migrationStep in migrationSteps) {
      migrationStep.process()
    }
  }
}
