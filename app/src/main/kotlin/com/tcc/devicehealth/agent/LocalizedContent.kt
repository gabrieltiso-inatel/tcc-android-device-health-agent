package com.tcc.devicehealth.agent

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val languageTag = language.languageTag
    val configuration = remember(baseConfiguration, languageTag) {
        if (languageTag == null) {
            baseConfiguration
        } else {
            Configuration(baseConfiguration).apply { setLocale(Locale.forLanguageTag(languageTag)) }
        }
    }
    val localizedContext = remember(baseContext, configuration) {
        if (languageTag == null) baseContext else baseContext.createConfigurationContext(configuration)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        content = content,
    )
}
