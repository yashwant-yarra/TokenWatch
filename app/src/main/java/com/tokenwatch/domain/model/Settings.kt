package com.tokenwatch.domain.model

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** How often usage pages are re-read in the background. 0 means off. */
    val refreshIntervalMinutes: Int = 0,
    val usageAlertsEnabled: Boolean = false,
    /** Notify when a session (rolling or daily) window reaches this percent used. */
    val sessionAlertPercent: Int = 80,
    /** Notify when a weekly window reaches this percent used. */
    val weeklyAlertPercent: Int = 80,
    /** Notify once a session or weekly window has reset, confirmed by re-reading the page. */
    val resetAlertsEnabled: Boolean = false,
)
