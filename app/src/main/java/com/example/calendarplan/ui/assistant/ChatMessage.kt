package com.example.calendarplan.ui.assistant

import android.graphics.Bitmap
import java.time.LocalDateTime

/**
 * Represents a message in the chat between the user and the AI assistant
 */
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val image: Bitmap? = null,
    val readyToEnterCalendar: Boolean = false,
    val eventData: CalendarEvent? = null
)
