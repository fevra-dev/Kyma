package com.sonicvault.app.domain.model

/**
 * Data-over-sound protocol options.
 * Binary choice: Audible (hearable, 2–6 kHz) or Ultrasonic (silent, 18–20 kHz).
 * Rams: as little design as possible — two modes match the user's mental model.
 */
enum class Protocol(val label: String, val description: String) {
    AUDIBLE("Audible", "2–6 kHz hearable tones"),
    ULTRASONIC("Ultrasonic", "18–20 kHz silent transfer")
}
