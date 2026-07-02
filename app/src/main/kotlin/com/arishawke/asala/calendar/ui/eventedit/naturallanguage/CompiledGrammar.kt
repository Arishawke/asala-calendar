/*
 * Copyright (C) 2026 Arishawke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.arishawke.asala.calendar.ui.eventedit.naturallanguage

import java.util.concurrent.ConcurrentHashMap

// every vocab-derived regex from EventTextParser and DateGrammar, compiled once per
// Vocabulary instead of once per keystroke. field names and match-group numbering
// mirror the extractX/DateGrammar function that consumes them; see forVocabulary
// for the per-vocabulary cache (today there is only Vocabulary.English).
internal class CompiledGrammar(vocab: Vocabulary) {
    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)

    // EventTextParser.extractDuration
    val duration: Regex

    // EventTextParser.extractTimeRange
    val timeRange: Regex
    val timeRangeFromTo: Regex

    // EventTextParser.extractSingleTime
    val singleTimeNamed: Regex
    val singleTimeMeridiem: Regex
    val singleTimeBareAt: Regex

    // EventTextParser.extractTimeOfDay
    val tonight: Regex
    val timeOfDayLed: Regex

    // EventTextParser.extractLocation
    val location: Regex
    val locationTrim: Regex

    // DateGrammar.relative
    val relativeToday: Regex
    val relativeTomorrow: Regex
    val relativeIn: Regex

    // DateGrammar.nextThisWeekday
    val nextThisWeekday: Regex

    // DateGrammar.bareWeekday
    val everyQualifier: Regex
    val bareWeekday: Regex

    // DateGrammar.monthNameDate
    val monthDayYear: Regex
    val dayMonthYear: Regex

    // DateGrammar.ordinalDate
    val ordinalDate: Regex

    init {
        val units = alt(vocab.hourUnits + vocab.minuteUnits)
        duration = Regex("\\b(?:${alt(vocab.forConnector)})\\s+(\\d+(?:\\.\\d+)?)\\s*($units)\\b", ignoreCase)

        val mer = alt(vocab.meridiemAm + vocab.meridiemPm)
        val fromTo = alt(vocab.fromConnector + vocab.toConnector)
        timeRange = Regex(
            "(?:\\b(?:${alt(vocab.fromConnector)})\\s+)?\\b(\\d{1,2})(?::(\\d{2}))?\\s*($mer)?\\s*" +
                "(?:-|${alt(vocab.toConnector)})\\s*(\\d{1,2})(?::(\\d{2}))?\\s*($mer)?\\b",
            ignoreCase,
        )
        timeRangeFromTo = Regex("\\b($fromTo)\\b", ignoreCase)

        val at = alt(vocab.atConnector)
        val named = alt(vocab.noon + vocab.midnight)
        singleTimeNamed = Regex("(?:\\b(?:$at)\\s+)?\\b($named)\\b", ignoreCase)
        singleTimeMeridiem = Regex("(?:\\b(?:$at)\\s+)?\\b(\\d{1,2})(?::(\\d{2}))?\\s*($mer)\\b", ignoreCase)
        singleTimeBareAt = Regex("\\b(?:$at)\\s+(\\d{1,2})\\b", ignoreCase)

        tonight = Regex("\\b(${alt(vocab.tonight)})\\b", ignoreCase)
        val lead = alt(
            vocab.thisQualifier + vocab.theArticle + vocab.today + vocab.tomorrow + vocab.weekdays.keys,
        )
        val words = alt(vocab.timeOfDay.keys - vocab.tonight.toSet())
        timeOfDayLed = Regex("\\b($lead)\\s+($words)\\b", ignoreCase)

        location = Regex("\\b(?:${alt(vocab.atConnector)})\\s+(.+)$", ignoreCase)
        locationTrim = Regex("\\s+(${alt(vocab.locationTrimConnectors)})$", ignoreCase)

        relativeToday = Regex("\\b(?:${alt(vocab.today)})\\b", ignoreCase)
        relativeTomorrow = Regex("\\b(?:${alt(vocab.tomorrow)})\\b", ignoreCase)
        relativeIn = Regex(
            "\\b(?:${alt(vocab.inConnector)})\\s+(\\d+)\\s+(${alt(vocab.dayUnits + vocab.weekUnits)})\\b",
            ignoreCase,
        )

        val qual = alt(vocab.nextQualifier + vocab.thisQualifier)
        nextThisWeekday = Regex("\\b($qual)\\s+(${alt(vocab.weekdays.keys)})\\b", ignoreCase)

        everyQualifier = Regex("\\b(?:${alt(vocab.everyQualifier)})\\b", ignoreCase)
        val bare = alt(vocab.weekdays.keys - vocab.bareHomographs)
        bareWeekday = Regex("\\b($bare)\\b", ignoreCase)

        val mon = alt(vocab.months.keys)
        val ord = alt(vocab.ordinalSuffixes)
        monthDayYear = Regex("\\b($mon)\\.?\\s+(\\d{1,2})(?:$ord)?(?:,?\\s+(\\d{4}))?\\b", ignoreCase)
        dayMonthYear = Regex("\\b(\\d{1,2})(?:$ord)?\\s+($mon)\\.?(?:,?\\s+(\\d{4}))?\\b", ignoreCase)

        val the = alt(vocab.theArticle)
        ordinalDate = Regex("\\b(?:(?:$the)\\s+)?(\\d{1,2})(?:$ord)\\b", ignoreCase)
    }

    companion object {
        private val cache = ConcurrentHashMap<Vocabulary, CompiledGrammar>()

        fun forVocabulary(vocab: Vocabulary): CompiledGrammar = cache.computeIfAbsent(vocab) { CompiledGrammar(it) }
    }
}
