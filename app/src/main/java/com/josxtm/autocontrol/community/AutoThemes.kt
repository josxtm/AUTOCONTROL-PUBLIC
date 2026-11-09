package com.josxtm.autocontrol.community

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

data class AutoTheme(val name: String, val accent: Color, val background: Color, val surface: Color, val dark: Boolean = false)
val autoThemes = listOf(
    AutoTheme("T1 Claro Profesional", Color(0xFF487B96), Color(0xFFF4F7FA), Color.White),
    AutoTheme("T2 Azul Corporativo", Color(0xFF366C9C), Color(0xFFE7F0F8), Color.White),
    AutoTheme("T3 Verde Técnico", Color(0xFF47795A), Color(0xFFF1F6EC), Color.White),
    AutoTheme("T4 Gris Industrial", Color(0xFFA92E35), Color(0xFFE5E7E9), Color(0xFFF8F8F7)),
    AutoTheme("T5 Arena / Trabajo de Campo", Color(0xFF9A7343), Color(0xFFF8F2E8), Color(0xFFFFFCF7)),
    AutoTheme("T6 Noche Suave", Color(0xFF90B9C9), Color(0xFF202B38), Color(0xFF2A3949), true),
    AutoTheme("T7 Rojo Operativo", Color(0xFFB44B4D), Color(0xFFFFF0EF), Color.White),
    AutoTheme("T8 Púrpura Técnico", Color(0xFF8069AB), Color(0xFFF3F0FA), Color.White),
    AutoTheme("T9 Turquesa Moderno", Color(0xFF348C94), Color(0xFFECF8F8), Color.White),
    AutoTheme("T10 Inspiración macOS", Color(0xFF688CB8), Color(0xFFECF1FA), Color(0xFFF8FAFE))
)
fun autoColorScheme(themeIndex: Int): ColorScheme {
    val t = autoThemes.getOrElse(themeIndex) { autoThemes[3] }
    return if (t.dark) darkColorScheme(primary=t.accent, background=t.background, surface=t.surface)
    else lightColorScheme(primary=t.accent, background=t.background, surface=t.surface,
        onPrimary=Color.White)
}
