/*
 * Copyright (C) 2026 Arishawke
 * GPL v3.
 */
package com.arishawke.asala.calendar.ui.eventedit.naturallanguage

import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Locale

class VocabularyTest {
    // the seam: only English exists today, so every locale resolves to it. this
    // pins that an unsupported locale falls back to English rather than breaking
    // or returning an empty vocabulary.
    @Test fun `forLocale falls back to English for every locale`() {
        assertSame(Vocabulary.English, Vocabulary.forLocale(Locale.US))
        assertSame(Vocabulary.English, Vocabulary.forLocale(Locale.FRENCH))
        assertSame(Vocabulary.English, Vocabulary.forLocale(Locale.JAPANESE))
    }
}
