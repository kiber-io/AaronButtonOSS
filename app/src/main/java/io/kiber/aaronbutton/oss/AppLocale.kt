package io.kiber.aaronbutton.oss

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

internal const val CONFIG_PREFS = "button_config"
private const val LANGUAGE_KEY = "language"

internal enum class AppLanguage(val tag: String, val shortName: String) {
    RUSSIAN("ru", "RU"),
    ENGLISH("en", "EN")
}

internal fun appLanguage(context: Context): AppLanguage {
    val tag = context.getSharedPreferences(CONFIG_PREFS, Context.MODE_PRIVATE)
        .getString(LANGUAGE_KEY, AppLanguage.ENGLISH.tag)
    return AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.ENGLISH
}

internal fun setAppLanguage(context: Context, language: AppLanguage) {
    context.getSharedPreferences(CONFIG_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(LANGUAGE_KEY, language.tag)
        .apply()
}

internal fun localizedContext(base: Context): Context {
    val locale = Locale.forLanguageTag(appLanguage(base).tag)
    val configuration = Configuration(base.resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return base.createConfigurationContext(configuration)
}
