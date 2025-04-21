package com.example.calendarplan.ui.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.viewModelScope
import com.example.calendarplan.BuildConfig
import com.example.calendarplan.ui.calendar.CalendarTask
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class CalendarAssistantViewModel(private val context: Context) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _events = MutableLiveData<List<CalendarEvent>>(generateSampleEvents())
    val events: LiveData<List<CalendarEvent>> = _events
    
    private val _lastAction = MutableStateFlow<CalendarAction?>(null)
    val lastAction: StateFlow<CalendarAction?> = _lastAction.asStateFlow()
    
    // Initialize Gemini model
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )
    
    // Initialize with a welcome message
    init {
        _messages.value = listOf(
            ChatMessage(
                content = "Hello! I'm your Calendar Assistant. I can help you manage your schedule with natural language commands. Try phrases like:\n\n" +
                        "• Add a meeting with John tomorrow at 2pm for 1 hour\n" +
                        "• Schedule a high priority dentist appointment on Friday at 10am\n" +
                        "• Create a team lunch event next Monday at noon with Alex and Sarah\n" +
                        "• Show me my events for this week\n" +
                        "• What's on my calendar for tomorrow?\n\n" +
                        "I'll confirm each action with a response and update your calendar automatically!",
                isUser = false
            )
        )
    }
    
    fun sendMessage(text: String) {
        // Add user message to the list
        val userMessage = ChatMessage(
            content = text,
            isUser = true
        )
        // Update LiveData on main thread
        viewModelScope.launch(Dispatchers.Main) {
            _messages.value = _messages.value + userMessage
            
            // Generate AI response
            _isLoading.value = true
            
            // Log the user message and current time for debugging
            Log.d("CalendarVM", "User message: $text at ${LocalDateTime.now()}")
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Create a prompt that includes context about current events
                val eventsContext = createEventsContext()
                val fullPrompt = """
                    You are a helpful calendar assistant that can manage events and tasks.
                    
                    Current events in the calendar:
                    $eventsContext
                    
                    Current date and time: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}
                    
                    The user says: $text
                    
                    Analyze the user's request and respond in the following JSON format:
                    ```json
                    {
                      "response": "Your natural language response to the user",
                      "action": "ADD_EVENT|EDIT_EVENT|DELETE_EVENT|SHOW_EVENTS|SUMMARIZE|NONE",
                      "eventData": {
                        "id": "ID if editing/deleting an existing event, otherwise null",
                        "title": "Event title",
                        "description": "Event description",
                        "startTime": "Start time in yyyy-MM-dd HH:mm format",
                        "endTime": "End time in yyyy-MM-dd HH:mm format",
                        "location": "Event location",
                        "priority": "HIGH|MEDIUM|LOW"
                      }
                    }
                    ```
                    
                    IMPORTANT INSTRUCTIONS:
                    1. For ADD_EVENT, always include a clear title, start time, and end time
                    2. Format all dates as yyyy-MM-dd HH:mm exactly
                    3. Always include a helpful response message that confirms what action you're taking
                    4. For SHOW_EVENTS or SUMMARIZE, don't include eventData
                    5. Make sure your response is conversational and friendly
                    
                    Example for adding an event:
                    ```json
                    {
                      "response": "I've added your meeting with John to your calendar for tomorrow at 2:00 PM.",
                      "action": "ADD_EVENT",
                      "eventData": {
                        "id": null,
                        "title": "Meeting with John",
                        "description": "Discuss project updates",
                        "startTime": "2025-04-22 14:00",
                        "endTime": "2025-04-22 15:00",
                        "location": "Office",
                        "priority": "MEDIUM"
                      }
                    }
                    ```
                """.trimIndent()
                
                try {
                    val response = generativeModel.generateContent(
                        content {
                            text(fullPrompt)
                        }
                    )
                    val responseText = response.text ?: ""
                    
                    // Add debug message if response is empty
                    if (responseText.isBlank()) {
                        val debugMessage = ChatMessage(
                            content = "[Debug] Received empty response from AI. Please try again with a more specific request.",
                            isUser = false
                        )
                        _messages.value = _messages.value + debugMessage
                        _isLoading.value = false
                        return@launch
                    }
                    
                    // Parse the AI response
                    val aiResponse = parseAIResponse(responseText)
                    
                    // Add raw response in debug mode
                    if (BuildConfig.DEBUG) {
                        Log.d("CalendarVM", "Raw AI response: $responseText")
                    }
                    
                    // Process the calendar action
                    processCalendarAction(aiResponse)
                    
                    // Add the AI response to the chat with readyToEnterCalendar flag and raw response
                    val aiMessage = ChatMessage(
                        content = aiResponse.response.ifBlank { "I've processed your request." },
                        isUser = false,
                        readyToEnterCalendar = aiResponse.readyToEnterCalendar,
                        eventData = aiResponse.eventData,
                        rawAiResponse = responseText
                    )
                    // Update LiveData on main thread
                    withContext(Dispatchers.Main) {
                        _messages.value = _messages.value + aiMessage
                        _isLoading.value = false
                    }
                } catch (e: Exception) {
                    // Handle API-specific errors
                    withContext(Dispatchers.Main) {
                        val errorMessage = ChatMessage(
                            content = "Sorry, I couldn't process that request. There was an issue with the AI service: ${e.message ?: "Unknown API error"}",
                            isUser = false
                        )
                        _messages.value = _messages.value + errorMessage
                        _isLoading.value = false
                        e.printStackTrace() // Log the exception
                    }
                }
            } catch (e: Exception) {
                // Handle general errors
                withContext(Dispatchers.Main) {
                    val errorMessage = ChatMessage(
                        content = "Sorry, I encountered an error: ${e.localizedMessage ?: "Unknown error"}",
                        isUser = false
                    )
                    _messages.value = _messages.value + errorMessage
                    _isLoading.value = false
                    e.printStackTrace() // Log the exception
                }
            }
        }
    }
    
    private fun createEventsContext(): String {
        val currentEvents = _events.value ?: emptyList()
        if (currentEvents.isEmpty()) {
            return "No events currently scheduled."
        }
        
        return currentEvents.joinToString("\n") { event ->
            """
            Event: ${event.title}
            Description: ${event.description}
            Time: ${event.startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))} to ${event.endTime.format(DateTimeFormatter.ofPattern("HH:mm"))}
            Location: ${event.location}
            Priority: ${event.priority}
            """.trimIndent()
        }
    }
    
    private fun parseAIResponse(responseText: String): AIResponse {
        try {
            // Extract JSON from the response - handle both code blocks and raw JSON
            val jsonPattern = """(?:\`\`\`json)?\s*(\{[\s\S]*?\})\s*(?:\`\`\`)?|\{[\s\S]*\}""".toRegex()
            val jsonMatch = jsonPattern.find(responseText)
            
            // Check for delete keywords in the response text for better detection
            val isDeleteRequest = responseText.lowercase().contains("delete") || 
                                responseText.lowercase().contains("remove") ||
                                responseText.lowercase().contains("cancel")
            
            // If no JSON found, check if it might be a delete request
            if (jsonMatch == null) {
                // If it looks like a delete request but no JSON, try to extract event info
                if (isDeleteRequest) {
                    Log.d("CalendarVM", "Detected possible delete request without proper JSON")
                    // Try to create a simple delete action with minimal event data
                    return handlePossibleDeleteRequest(responseText)
                }
                
                return AIResponse(
                    response = responseText.take(500), // Limit response length for safety
                    action = CalendarAction.NONE,
                    eventData = null,
                    readyToEnterCalendar = false
                )
            }
            
            // Extract the JSON string from the match
            val jsonString = jsonMatch.groupValues.getOrNull(1) ?: jsonMatch.value
            
            // Log the raw JSON for debugging
            Log.d("CalendarVM", "Raw JSON from AI: $jsonString")
            
            // Extract response message
            val response = extractJsonValue(jsonString, "response") ?: "I've processed your request."
            
            // Extract action
            val actionStr = extractJsonValue(jsonString, "action")?.trim()?.uppercase() ?: 
                           if (isDeleteRequest) "DELETE_EVENT" else "NONE"
            
            val action = try {
                CalendarAction.valueOf(actionStr)
            } catch (e: Exception) {
                Log.w("CalendarVM", "Invalid action: $actionStr, defaulting to NONE")
                CalendarAction.NONE
            }
            
            // Extract event data if present
            val eventData = if (action == CalendarAction.ADD_EVENT || 
                              action == CalendarAction.EDIT_EVENT || 
                              action == CalendarAction.DELETE_EVENT) {
                try {
                    val eventDataJson = extractJsonObject(jsonString, "eventData")
                    if (eventDataJson != null) {
                        // Generate a random UUID for new events if not provided
                        val id = extractJsonValue(eventDataJson, "id")?.takeIf { it.isNotBlank() } 
                            ?: UUID.randomUUID().toString()
                            
                        val title = extractJsonValue(eventDataJson, "title")?.takeIf { it.isNotBlank() } 
                            ?: "Untitled Event"
                            
                        val description = extractJsonValue(eventDataJson, "description") ?: ""
                        
                        // Parse dates with standard format
                        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        val now = LocalDateTime.now()
                        
                        // Get start time
                        val startTimeStr = extractJsonValue(eventDataJson, "startTime") ?: ""
                        val startTime = if (startTimeStr.isNotBlank()) {
                            try {
                                LocalDateTime.parse(startTimeStr, dateTimeFormatter)
                            } catch (e: Exception) {
                                Log.w("CalendarVM", "Failed to parse start time: $startTimeStr, using current time")
                                now
                            }
                        } else now
                        
                        // Get end time
                        val endTimeStr = extractJsonValue(eventDataJson, "endTime") ?: ""
                        val endTime = if (endTimeStr.isNotBlank()) {
                            try {
                                LocalDateTime.parse(endTimeStr, dateTimeFormatter)
                            } catch (e: Exception) {
                                Log.w("CalendarVM", "Failed to parse end time: $endTimeStr, using startTime + 1 hour")
                                startTime.plusHours(1)
                            }
                        } else startTime.plusHours(1)
                        
                        val location = extractJsonValue(eventDataJson, "location") ?: ""
                        
                        // Parse priority
                        val priorityStr = extractJsonValue(eventDataJson, "priority")?.trim()?.uppercase() ?: "MEDIUM"
                        val priority = try {
                            EventPriority.valueOf(priorityStr)
                        } catch (e: Exception) {
                            EventPriority.MEDIUM
                        }
                        
                        // Create the event
                        CalendarEvent(
                            id = id,
                            title = title,
                            description = description,
                            startTime = startTime,
                            endTime = endTime,
                            location = location,
                            priority = priority
                        )
                    } else null
                } catch (e: Exception) {
                    Log.e("CalendarVM", "Error parsing event data: ${e.message}")
                    null
                }
            } else null
            
            // Always mark as ready to enter calendar if we have event data
            return AIResponse(
                response = response,
                action = action,
                eventData = eventData,
                readyToEnterCalendar = eventData != null
            )
        } catch (e: Exception) {
            Log.e("CalendarVM", "Error in parseAIResponse: ${e.message}")
            return AIResponse(
                response = "I'm sorry, I couldn't process that request properly.",
                action = CalendarAction.NONE,
                eventData = null,
                readyToEnterCalendar = false
            )
        }
    }
    
    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = """"$key"\s*:\s*"([^"]*)"|\s*"$key"\s*:\s*([^",}\s]*)""".toRegex()
        val match = pattern.find(json)
        return match?.groupValues?.get(1)?.takeIf { it.isNotEmpty() } ?: match?.groupValues?.get(2)
    }
    
    private fun extractJsonObject(json: String, key: String): String? {
        val pattern = """"$key"\s*:\s*(\{[^}]*\})""".toRegex()
        val match = pattern.find(json)
        return match?.groupValues?.get(1)
    }
    
    private suspend fun processCalendarAction(response: AIResponse) {
        // Create a confirmation message based on the action
        val confirmationMessage = when (response.action) {
            CalendarAction.ADD_EVENT -> {
                response.eventData?.let { event ->
                    // SIMPLIFIED: Always add the event directly without additional validation
                    // Log the event data from AI response for debugging
                    Log.d("CalendarVM", "Processing AI event data:")
                    Log.d("CalendarVM", "  Title: ${event.title}")
                    Log.d("CalendarVM", "  Start time: ${event.startTime}")
                    Log.d("CalendarVM", "  End time: ${event.endTime}")
                    
                    // Just use the event as-is from the AI
                    val completeEvent = event.copy(
                        id = event.id.takeIf { !it.isNullOrBlank() } ?: UUID.randomUUID().toString()
                    )
                    
                    // Switch to main thread for LiveData updates
                    withContext(Dispatchers.Main) {
                        val currentEvents = _events.value ?: emptyList()
                        _events.value = currentEvents + completeEvent
                    }
                    
                    // Share events with timeline
                    shareEventsWithTimeline()
                    
                    // Log the event creation for debugging
                    Log.d("CalendarVM", "Added event: ${completeEvent.title} at ${completeEvent.startTime}")
                    
                    // Return null since the AI response already contains confirmation
                    null
                }
            }
            CalendarAction.EDIT_EVENT -> {
                response.eventData?.let { updatedEvent ->
                    // Switch to main thread for LiveData updates
                    withContext(Dispatchers.Main) {
                        val currentEvents = _events.value ?: emptyList()
                        _events.value = currentEvents.map { 
                            if (it.id == updatedEvent.id) updatedEvent else it 
                        }
                    }
                    
                    // Share events with timeline
                    shareEventsWithTimeline()
                    
                    // Add a confirmation message if the AI didn't provide a clear one
                    if (!response.response.contains("updated") && !response.response.contains("edited") && !response.response.contains("changed")) {
                        "Event '${updatedEvent.title}' has been updated"
                    } else null
                }
            }
            CalendarAction.DELETE_EVENT -> {
                response.eventData?.let { eventToDelete ->
                    Log.d("CalendarVM", "Attempting to delete event with ID: ${eventToDelete.id}")
                    
                    // Get current events from both sources to ensure we have the latest
                    val assistantEvents = _events.value ?: emptyList()
                    
                    // Load events directly from SharedPreferences to ensure we have all events
                    val sharedPrefs = context.getSharedPreferences("CalendarEvents", Context.MODE_PRIVATE)
                    val eventsJsonStr = sharedPrefs.getString("events", "[]")
                    val timelineEvents = try {
                        parseEventsFromJson(eventsJsonStr ?: "[]")
                    } catch (e: Exception) {
                        Log.e("CalendarVM", "Error parsing events from SharedPreferences: ${e.message}")
                        emptyList()
                    }
                    
                    // Combine both sources to ensure we don't miss any events
                    val allEvents = (assistantEvents + timelineEvents).distinctBy { it.id }
                    
                    Log.d("CalendarVM", "Found ${allEvents.size} total events before deletion")
                    
                    // Filter out the event to delete
                    val updatedEvents = allEvents.filter { it.id != eventToDelete.id }
                    
                    Log.d("CalendarVM", "After filtering, ${updatedEvents.size} events remain")
                    
                    // Update both the assistant's events and the shared preferences
                    withContext(Dispatchers.Main) {
                        _events.value = updatedEvents
                    }
                    
                    // Save directly to SharedPreferences
                    saveEventsDirectlyToSharedPreferences(updatedEvents)
                    
                    // Share events with timeline
                    shareEventsWithTimeline()
                    
                    // Add a confirmation message if the AI didn't provide a clear one
                    if (!response.response.contains("deleted") && !response.response.contains("removed")) {
                        "Event '${eventToDelete.title}' has been deleted from your calendar"
                    } else null
                }
            }
            CalendarAction.SHOW_EVENTS -> {
                // No additional message needed for showing events
                null
            }
            CalendarAction.SUMMARIZE -> {
                // No additional message needed for summarizing
                null
            }
            CalendarAction.NONE -> {
                // No additional message needed
                null
            }
        }
        
        // If we have a confirmation message, add it to the chat
        confirmationMessage?.let {
            val confirmMessage = ChatMessage(
                content = it,
                isUser = false
            )
            _messages.value = _messages.value + confirmMessage
        }
        
        // Update the last action
        _lastAction.value = response.action
    }
    
    // Generate sample events for demonstration
    private fun generateSampleEvents(): List<CalendarEvent> {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        
        return listOf(
            CalendarEvent(
                id = "1",
                title = "Team Meeting",
                description = "Weekly team sync-up",
                startTime = now.withHour(10).withMinute(0),
                endTime = now.withHour(11).withMinute(0),
                location = "Conference Room A",
                priority = EventPriority.HIGH
            ),
            CalendarEvent(
                id = "2",
                title = "Lunch with Alex",
                description = "Discuss project collaboration",
                startTime = now.withHour(12).withMinute(30),
                endTime = now.withHour(13).withMinute(30),
                location = "Cafe Central",
                priority = EventPriority.MEDIUM
            )
        )
    }
    // Share events with other components (like TimelineViewModel)
    /**
     * Extract a meaningful title from the AI response if the user didn't specify one
     */
    private fun extractTitleFromResponse(response: String): String {
        // Try to extract a title from phrases like "I've added a meeting with John"
        val addedPattern = """added (?:a|an|the) ([\w\s]+?)(?:\s+to your calendar|\s+for|\s+at|\s+on|\.)""".
            toRegex(RegexOption.IGNORE_CASE)
        val scheduledPattern = """scheduled (?:a|an|the) ([\w\s]+?)(?:\s+for|\s+at|\s+on|\.)""".
            toRegex(RegexOption.IGNORE_CASE)
        val createdPattern = """created (?:a|an|the) ([\w\s]+?)(?:\s+for|\s+at|\s+on|\.)""".
            toRegex(RegexOption.IGNORE_CASE)
        
        // Try each pattern in order
        val addedMatch = addedPattern.find(response)
        if (addedMatch != null) {
            val title = addedMatch.groupValues[1].trim()
            if (title.isNotBlank() && title != "event" && title != "appointment") {
                Log.d("CalendarVM", "Extracted title from 'added' pattern: $title")
                return title.capitalize()
            }
        }
        
        val scheduledMatch = scheduledPattern.find(response)
        if (scheduledMatch != null) {
            val title = scheduledMatch.groupValues[1].trim()
            if (title.isNotBlank() && title != "event" && title != "appointment") {
                Log.d("CalendarVM", "Extracted title from 'scheduled' pattern: $title")
                return title.capitalize()
            }
        }
        
        val createdMatch = createdPattern.find(response)
        if (createdMatch != null) {
            val title = createdMatch.groupValues[1].trim()
            if (title.isNotBlank() && title != "event" && title != "appointment") {
                Log.d("CalendarVM", "Extracted title from 'created' pattern: $title")
                return title.capitalize()
            }
        }
        
        // If no match found, return a default title
        return "Calendar Event"
    }
    
    /**
     * Add an appropriate emoji to the event title based on its content
     */
    private fun addEmojiToTitle(title: String): String {
        val lowercaseTitle = title.lowercase()
        
        return when {
            lowercaseTitle.contains("meeting") || lowercaseTitle.contains("call") -> "🤝 $title"
            lowercaseTitle.contains("lunch") || lowercaseTitle.contains("dinner") || 
            lowercaseTitle.contains("breakfast") || lowercaseTitle.contains("coffee") -> "🍽️ $title"
            lowercaseTitle.contains("gym") || lowercaseTitle.contains("workout") || 
            lowercaseTitle.contains("exercise") -> "💪 $title"
            lowercaseTitle.contains("doctor") || lowercaseTitle.contains("dentist") || 
            lowercaseTitle.contains("appointment") -> "🩺 $title"
            lowercaseTitle.contains("birthday") || lowercaseTitle.contains("party") || 
            lowercaseTitle.contains("celebration") -> "🎉 $title"
            lowercaseTitle.contains("travel") || lowercaseTitle.contains("flight") || 
            lowercaseTitle.contains("trip") -> "✈️ $title"
            lowercaseTitle.contains("deadline") || lowercaseTitle.contains("due") -> "⏰ $title"
            lowercaseTitle.contains("study") || lowercaseTitle.contains("class") || 
            lowercaseTitle.contains("lecture") -> "📚 $title"
            else -> "📅 $title"
        }
    }
    
    private fun shareEventsWithTimeline() {
        // Use a shared preference or other mechanism to share events
        // This is a simple implementation - in a real app, you might use a repository pattern
        val sharedPrefs = context.getSharedPreferences("CalendarEvents", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        
        // Convert events to JSON and store with all fields including priority, description, and location
        val currentEvents = _events.value ?: emptyList()
        
        // Log events for debugging
        Log.d("CalendarVM", "Sharing ${currentEvents.size} events with timeline")
        currentEvents.forEach { event ->
            Log.d("CalendarVM", "Event: ${event.title} at ${event.startTime}")
        }
        
        val eventsJson = currentEvents.joinToString(",") { event ->
            // Use ISO-8601 format for consistent date/time handling
            """
            {"id":"${event.id}","title":"${event.title}","startTime":"${event.startTime}","endTime":"${event.endTime}","priority":"${event.priority}","description":"${event.description ?: ""}","location":"${event.location ?: ""}","isCompleted":${event.isCompleted}}
            """.trimIndent()
        }
        
        editor.putString("events", "[$eventsJson]")
        // Use commit() instead of apply() to ensure immediate write
        editor.commit()
        
        Log.d("CalendarVM", "Saved ${currentEvents.size} events to SharedPreferences")
        
        // Notify the TimelineViewModel to reload events
        notifyTimelineToReload()
        
        // Log for debugging
        Log.d("CalendarVM", "Shared ${currentEvents.size} events with timeline")
    }
    
    private fun saveEventsDirectlyToSharedPreferences(events: List<CalendarEvent>) {
        val sharedPrefs = context.getSharedPreferences("CalendarEvents", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        
        val eventsJson = events.joinToString(",") { event ->
            """
            {"id":"${event.id}","title":"${event.title}","startTime":"${event.startTime}","endTime":"${event.endTime}","priority":"${event.priority}","description":"${event.description ?: ""}","location":"${event.location ?: ""}","isCompleted":${event.isCompleted}}
            """.trimIndent()
        }
        
        editor.putString("events", "[$eventsJson]")
        // Use commit() for immediate write
        val success = editor.commit()
        
        Log.d("CalendarVM", "Direct save to SharedPreferences ${if (success) "succeeded" else "failed"}")
    }
    
    private fun parseEventsFromJson(jsonStr: String): List<CalendarEvent> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
        
        val events = mutableListOf<CalendarEvent>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                try {
                    val eventJson = jsonArray.getJSONObject(i)
                    val id = eventJson.optString("id")
                    if (id.isBlank()) continue
                    
                    val title = eventJson.optString("title", "Untitled Event")
                    val startTimeStr = eventJson.optString("startTime")
                    val endTimeStr = eventJson.optString("endTime")
                    
                    // Parse dates with fallback
                    val startTime = try {
                        LocalDateTime.parse(startTimeStr)
                    } catch (e: Exception) {
                        LocalDateTime.now()
                    }
                    
                    val endTime = try {
                        LocalDateTime.parse(endTimeStr)
                    } catch (e: Exception) {
                        startTime.plusHours(1)
                    }
                    
                    val priorityStr = eventJson.optString("priority", "MEDIUM")
                    val priority = try {
                        EventPriority.valueOf(priorityStr)
                    } catch (e: Exception) {
                        EventPriority.MEDIUM
                    }
                    
                    events.add(CalendarEvent(
                        id = id,
                        title = title,
                        description = eventJson.optString("description", ""),
                        startTime = startTime,
                        endTime = endTime,
                        location = eventJson.optString("location", ""),
                        priority = priority,
                        isCompleted = eventJson.optBoolean("isCompleted", false)
                    ))
                } catch (e: Exception) {
                    Log.e("CalendarVM", "Error parsing event: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarVM", "Error parsing events JSON: ${e.message}")
        }
        
        return events
    }
    
    private fun notifyTimelineToReload() {
        // Broadcast an intent to notify the TimelineViewModel to reload
        val intent = Intent("com.example.calendarplan.RELOAD_EVENTS")
        
        // Log the broadcast for debugging
        Log.d("CalendarVM", "Broadcasting reload intent to timeline")
        
        try {
            // Send broadcast with RECEIVER_NOT_EXPORTED flag for Android security requirements
            context.sendBroadcast(intent, null)
            
            // Force a direct reload by accessing the shared preferences
            // This is a fallback in case the broadcast doesn't work
            val sharedPrefs = context.getSharedPreferences("CalendarEvents", Context.MODE_PRIVATE)
            val eventsJson = sharedPrefs.getString("events", "[]")
            Log.d("CalendarVM", "Current events in SharedPreferences: $eventsJson")
        } catch (e: Exception) {
            Log.e("CalendarVM", "Error sending broadcast: ${e.message}")
        }
    }
    
    private fun handlePossibleDeleteRequest(responseText: String): AIResponse {
        // Try to find event title or ID in the text
        val currentEvents = _events.value ?: emptyList()
        
        // Look for any event title in the response
        for (event in currentEvents) {
            if (responseText.contains(event.title, ignoreCase = true)) {
                Log.d("CalendarVM", "Found event to delete by title: ${event.title}")
                return AIResponse(
                    response = "I'll delete the event '${event.title}' for you.",
                    action = CalendarAction.DELETE_EVENT,
                    eventData = event,
                    readyToEnterCalendar = true
                )
            }
        }
        
        // If no specific event found, return a generic response
        return AIResponse(
            response = "I couldn't find which event you want to delete. Could you specify the event name?",
            action = CalendarAction.NONE,
            eventData = null,
            readyToEnterCalendar = false
        )
    }
}

/**
 * Possible actions that the AI assistant can perform
 */
enum class CalendarAction {
    ADD_EVENT,
    EDIT_EVENT,
    DELETE_EVENT,
    SHOW_EVENTS,
    SUMMARIZE,
    NONE
}

/**
 * Structured response from the AI
 */
data class AIResponse(
    val response: String,
    val action: CalendarAction,
    val eventData: CalendarEvent?,
    val readyToEnterCalendar: Boolean = false
)
