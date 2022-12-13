package com.example

import com.apollographql.apollo3.api.EnumValue

enum class UserInterfaceStyle(
  override val rawValue: String,
) : EnumValue {
  LIGHT("Light"),

  DARK("Dark"),

  CORNFLOWERBLUE("CornflowerBlue"),


  /**
   * Auto generated constant for unknown enum values
   */
  UNKNOWN__("UNKNOWN__");

  companion object {
    fun safeValueOf(rawValue: String): UserInterfaceStyle =
      values().find { it.rawValue == rawValue } ?: UNKNOWN__
  }
}


fun main() {
  val enumValue1 = UserInterfaceStyle.Light
  val enumValue2 = UserInterfaceStyle.Dark
  val enumValue3 = UserInterfaceStyle.CornflowerBlue
}
