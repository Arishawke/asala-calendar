package com.arishawke.asala.calendar.ui.widget

import com.arishawke.asala.calendar.ui.settings.ThemeMode
import com.arishawke.asala.calendar.ui.settings.WidgetThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `follow-app resolves through the app theme mode`() {
        assertEquals(
            ResolvedTheme.Dark,
            AgendaWidgetTheme.resolveWidget(WidgetThemeMode.FollowApp, ThemeMode.System, systemInDark = true),
        )
        assertEquals(
            ResolvedTheme.Light,
            AgendaWidgetTheme.resolveWidget(WidgetThemeMode.FollowApp, ThemeMode.System, systemInDark = false),
        )
        assertEquals(
            ResolvedTheme.Amoled,
            AgendaWidgetTheme.resolveWidget(WidgetThemeMode.FollowApp, ThemeMode.Amoled, systemInDark = false),
        )
    }

    @Test
    fun `explicit widget mode ignores the app theme mode`() {
        assertEquals(
            ResolvedTheme.Light,
            AgendaWidgetTheme.resolveWidget(WidgetThemeMode.Light, ThemeMode.Dark, systemInDark = true),
        )
        assertEquals(
            ResolvedTheme.Amoled,
            AgendaWidgetTheme.resolveWidget(WidgetThemeMode.Amoled, ThemeMode.Light, systemInDark = false),
        )
    }

    @Test
    fun `widget system mode follows the system flag, not the app mode`() {
        assertEquals(
            ResolvedTheme.Dark,
            AgendaWidgetTheme.resolveWidget(WidgetThemeMode.System, ThemeMode.Light, systemInDark = true),
        )
    }

    @Test
    fun `opaque background uses full alpha`() {
        assertEquals(1f, AgendaWidgetTheme.backgroundAlpha(translucent = false), 0f)
    }

    @Test
    fun `translucent background is see-through but stays legible`() {
        val alpha = AgendaWidgetTheme.backgroundAlpha(translucent = true)
        assertTrue("expected < 1.0 so wallpaper shows through", alpha < 1f)
        assertTrue("expected >= 0.5 so text stays legible", alpha >= 0.5f)
    }
}
