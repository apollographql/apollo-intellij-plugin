package com.apollographql.ijplugin.refactoring.migration.item

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.usageView.UsageInfo

open class MigrationItemUsageInfo : UsageInfo {
  val migrationItem: MigrationItem

  constructor(migrationItem: MigrationItem, reference: PsiReference) : super(reference) {
    this.migrationItem = migrationItem
  }

  constructor(migrationItem: MigrationItem, element: PsiElement) : super(element) {
    this.migrationItem = migrationItem
  }

  constructor(migrationItem: MigrationItem, source: UsageInfo) : super(
    source.element!!,
    source.rangeInElement!!.startOffset,
    source.rangeInElement!!.endOffset
  ) {
    this.migrationItem = migrationItem
  }
}

context(MigrationItem)
fun UsageInfo.toMigrationItemUsageInfo() = MigrationItemUsageInfo(migrationItem = this@MigrationItem, source = this)

context(MigrationItem)
fun Array<UsageInfo>.toMigrationItemUsageInfo(): List<MigrationItemUsageInfo> {
  return map { it.toMigrationItemUsageInfo() }
}

context(MigrationItem)
fun PsiReference.toMigrationItemUsageInfo() = MigrationItemUsageInfo(migrationItem = this@MigrationItem, reference = this)

context(MigrationItem)
fun Collection<PsiReference>.toMigrationItemUsageInfo(): List<MigrationItemUsageInfo> {
  return map { it.toMigrationItemUsageInfo() }
}

context(MigrationItem)
fun PsiElement.toMigrationItemUsageInfo() = MigrationItemUsageInfo(migrationItem = this@MigrationItem, element = this)
