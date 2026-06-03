package com.arishawke.asala.calendar.ui.widget

import com.arishawke.asala.calendar.ui.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaWidgetThemeTest {
    @Test
    fun `system mode follows the system dark flag`() {
        assertEquals(ResolvedTheme.Dark, AgendaWidgetTheme.resolve(ThemeMode.System, systemInDark = true))
        assertEquals(ResolvedTheme.Light, AgendaWidgetTheme.resolve(ThemeMode.System, systemInDark = false))
    }

    @Test
    fun `explicit modes ignore the system dark flag`() {
        assertEquals(ResolvedTheme.Light, AgendaWidgetTheme.resolve(ThemeMode.Light, systemInDark = true))
        assertEquals(ResolvedTheme.Dark, AgendaWidgetTheme.resolve(ThemeMode.Dark, systemInDark = false))
    }

    @Test
    fun `amoled is its own resolved theme, distinct from dark`() {
        assertEquals(ResolvedTheme.Amoled, AgendaWidgetTheme.resolve(ThemeMode.Amoled, systemInDark = false))
        assertEquals(ResolvedTheme.Amoled, AgendaWidgetTheme.resolve(ThemeMode.Amoled, systemInDark = true))
    }
}
