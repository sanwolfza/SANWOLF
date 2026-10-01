package com.example.ui

import android.os.SystemClock

/**
 * Deduplicates rapid double-keystroke artifacts commonly caused by
 * Android emulator WebRTC/ADB input bridges forwarding both KeyEvent
 * and InputConnection events, or firing redundant key-down events.
 */
class TextInputDeduplicator {
    private var lastEmittedText: String? = null
    private var lastChangeTimestamp = 0L
    private var lastInsertedChar: Char? = null
    private var lastWasDeletion = false

    companion object {
        private const val DEDUPLICATE_WINDOW_MS = 280L
    }

    fun processChange(
        externalText: String,
        incomingText: String,
        currentTimeMs: Long = SystemClock.uptimeMillis()
    ): String {
        val timeDelta = currentTimeMs - lastChangeTimestamp

        // Use the last emitted text if available and recent to avoid stale recomposition state
        val oldText = if (lastEmittedText != null && timeDelta < 800L) {
            lastEmittedText!!
        } else {
            externalText
        }

        val oldLen = oldText.length
        val newLen = incomingText.length

        val result: String

        // Case 1: Insertion
        if (newLen > oldLen) {
            val addedCount = newLen - oldLen

            // Subcase 1A: Single event where 2 identical characters were inserted at once (e.g. "a" -> "aa")
            if (addedCount == 2) {
                var insertIndex = 0
                while (insertIndex < oldLen && insertIndex < newLen && oldText[insertIndex] == incomingText[insertIndex]) {
                    insertIndex++
                }
                val char1 = incomingText.getOrNull(insertIndex)
                val char2 = incomingText.getOrNull(insertIndex + 1)
                if (char1 != null && char1 == char2) {
                    lastChangeTimestamp = currentTimeMs
                    lastInsertedChar = char1
                    lastWasDeletion = false
                    val collapsed = incomingText.removeRange(insertIndex, insertIndex + 1)
                    lastEmittedText = collapsed
                    return collapsed
                }
            }

            // Subcase 1B: Rapid second call within window inserting the exact same character at the same position
            if (addedCount == 1 && timeDelta < DEDUPLICATE_WINDOW_MS && !lastWasDeletion) {
                var insertIndex = 0
                while (insertIndex < oldLen && insertIndex < newLen && oldText[insertIndex] == incomingText[insertIndex]) {
                    insertIndex++
                }
                val addedChar = incomingText.getOrNull(insertIndex)
                if (addedChar != null && addedChar == lastInsertedChar) {
                    // Drop rapid duplicate of the identical character
                    return oldText
                }
            }

            // Normal single character or paste insertion
            var insertIndex = 0
            while (insertIndex < oldLen && insertIndex < newLen && oldText[insertIndex] == incomingText[insertIndex]) {
                insertIndex++
            }
            lastInsertedChar = incomingText.getOrNull(insertIndex)
            lastWasDeletion = false
            lastChangeTimestamp = currentTimeMs
            result = incomingText
        } else if (newLen < oldLen) {
            // Case 2: Deletion (Backspace)
            val deletedCount = oldLen - newLen

            // Subcase 2A: Single call deleting 2 characters at once without a newline
            if (deletedCount == 2 && !oldText.contains('\n')) {
                var diffIndex = 0
                while (diffIndex < newLen && oldText[diffIndex] == incomingText[diffIndex]) {
                    diffIndex++
                }
                lastChangeTimestamp = currentTimeMs
                lastWasDeletion = true
                lastInsertedChar = null
                // If deleted at the tail end, delete only the single last character
                val singleDelete = if (diffIndex == newLen) {
                    oldText.substring(0, oldLen - 1)
                } else {
                    oldText.removeRange(diffIndex, diffIndex + 1)
                }
                lastEmittedText = singleDelete
                return singleDelete
            }

            // Subcase 2B: Rapid second backspace call within window
            if (deletedCount == 1 && timeDelta < DEDUPLICATE_WINDOW_MS && lastWasDeletion) {
                return oldText
            }

            lastWasDeletion = true
            lastInsertedChar = null
            lastChangeTimestamp = currentTimeMs
            result = incomingText
        } else {
            // Case 3: No length change
            result = incomingText
        }

        lastEmittedText = result
        return result
    }

    fun reset() {
        lastEmittedText = null
        lastChangeTimestamp = 0L
        lastInsertedChar = null
        lastWasDeletion = false
    }
}
