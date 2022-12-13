package com.example

import com.apollographql.apollo.api.EnumValue

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
  val enumValue1 = UserInterfaceStyle.LIGHT
  val enumValue2 = UserInterfaceStyle.DARK
  val enumValue3 = UserInterfaceStyle.CORNFLOWERBLUE
}
