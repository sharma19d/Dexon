package com.charon.dexon.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.charon.dexon.data.model.PrivacyGrade

object RiskColors {
    val Emerald = Color(0xFF10B981)
    val Teal = Color(0xFF14B8A6)
    val Amber = Color(0xFFF59E0B)
    val Crimson = Color(0xFFDC2626)

    fun forGrade(grade: PrivacyGrade): Color = when (grade) {
        PrivacyGrade.A -> Emerald
        PrivacyGrade.B -> Teal
        PrivacyGrade.C -> Amber
        PrivacyGrade.D, PrivacyGrade.F -> Crimson
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF3355CC),
    secondary = Color(0xFF586379),
    tertiary = Color(0xFF14B8A6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    secondary = Color(0xFFC0C6DC),
    tertiary = Color(0xFF5EEAD4)
)

@Composable
fun DexonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
