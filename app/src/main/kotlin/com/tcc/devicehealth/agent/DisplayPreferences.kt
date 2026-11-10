package com.tcc.devicehealth.agent

enum class ThemeMode {
    System,
    Light,
    Dark,
}

enum class AppLanguage(val languageTag: String?) {
    System(null),
    Portuguese("pt-BR"),
    English("en"),
}

data class DisplayPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val language: AppLanguage = AppLanguage.System,
)

internal fun <T : Enum<T>> enumValueOrDefault(values: Array<T>, name: String?, default: T): T =
    values.firstOrNull { it.name == name } ?: default
