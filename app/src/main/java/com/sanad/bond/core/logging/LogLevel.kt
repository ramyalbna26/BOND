package com.sanad.bond.core.logging

enum class LogLevel(val priority: Int, val letter: Char) {
    VERBOSE(2, 'V'),
    DEBUG(3, 'D'),
    INFO(4, 'I'),
    WARN(5, 'W'),
    ERROR(6, 'E'),
    ASSERT(7, 'A');

    companion object {
        fun fromLetter(letter: Char): LogLevel? = entries.firstOrNull { it.letter == letter }
        fun fromPriority(p: Int): LogLevel = entries.lastOrNull { it.priority <= p } ?: VERBOSE
    }
}