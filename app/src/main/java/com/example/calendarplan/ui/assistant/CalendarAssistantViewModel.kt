package com.example.calendarplan.ui.assistant

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
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
        _messages.value = _messages.value + userMessage
        
        // Generate AI response
        _isLoading.value = true
        
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
                    
                    // Add the AI response to the chat with readyToEnterCalendar flag
                    val aiMessage = ChatMessage(
                        content = aiResponse.response.ifBlank { "I've processed your request." },
                        isUser = false,
                        readyToEnterCalendar = aiResponse.readyToEnterCalendar,
                        eventData = aiResponse.eventData
                    )
                    _messages.value = _messages.value + aiMessage
                    _isLoading.value = false
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
            
            // If no JSON is found, return the raw text
            val jsonString = jsonMatch?.groupValues?.getOrNull(1) ?: jsonMatch?.value ?: return AIResponse(
                response = responseText.take(500), // Limit response length for safety
                action = CalendarAction.NONE,
                eventData = null,
                readyToEnterCalendar = false
            )
            
            // Extract response message with fallback to original text
            val response = extractJsonValue(jsonString, "response") ?: responseText.replace(jsonString, "").trim()
            
            // If response is still empty, use a default message
            val finalResponse = if (response.isBlank()) {
                "I've processed your request."
            } else {
                response
            }
            
            // Extract action with better error handling
            val actionStr = extractJsonValue(jsonString, "action")?.trim()?.uppercase() ?: "NONE"
            val action = try {
                // Handle empty action string
                if (actionStr.isBlank()) {
                    Log.w("CalendarVM", "Empty action string, defaulting to NONE")
                    CalendarAction.NONE
                } else {
                    CalendarAction.valueOf(actionStr)
                }
            } catch (e: Exception) {
                Log.w("CalendarVM", "Invalid action: $actionStr, defaulting to NONE")
                CalendarAction.NONE
            }
            
            // Log the raw AI response for debugging
            Log.d("CalendarVM", "Raw AI response: $jsonString")
            
            // Only parse event data if the action requires it
            val eventData = if (action == CalendarAction.ADD_EVENT || 
                              action == CalendarAction.EDIT_EVENT || 
                              action == CalendarAction.DELETE_EVENT) {
                try {
                    val eventDataJson = extractJsonObject(jsonString, "eventData")
                    if (eventDataJson != null) {
                        // Generate a random UUID for new events
                        val id = extractJsonValue(eventDataJson, "id")?.takeIf { it.isNotBlank() } 
                            ?: UUID.randomUUID().toString()
                            
                        val title = extractJsonValue(eventDataJson, "title")?.takeIf { it.isNotBlank() } 
                            ?: "Untitled Event"
                            
                        val description = extractJsonValue(eventDataJson, "description") ?: ""
                        
                        // Default to current time if parsing fails
                        val now = LocalDateTime.now()
                        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        
                        val startTimeStr = extractJsonValue(eventDataJson, "startTime") ?: ""
                        val startTime = try {
                            if (startTimeStr.isNotBlank()) {
                                LocalDateTime.parse(startTimeStr, dateTimeFormatter)
                            } else {
                                now
                            }
                        } catch (e: Exception) {
                            Log.w("CalendarVM", "Error parsing start time: $startTimeStr, using now")
                            now
                        }
                        
                        val endTimeStr = extractJsonValue(eventDataJson, "endTime") ?: ""
                        val endTime = try {
                            if (endTimeStr.isNotBlank()) {
                                LocalDateTime.parse(endTimeStr, dateTimeFormatter)
                            } else {
                                startTime.plusHours(1)
                            }
                        } catch (e: Exception) {
                            Log.w("CalendarVM", "Error parsing end time: $endTimeStr, using startTime + 1 hour")
                            startTime.plusHours(1)
                        }
                        
                        val location = extractJsonValue(eventDataJson, "location") ?: ""
                        
                        // Parse priority
                        val priorityStr = extractJsonValue(eventDataJson, "priority")?.trim()?.uppercase() ?: "MEDIUM"
                        val priority = try {
                            EventPriority.valueOf(priorityStr)
                        } catch (e: Exception) {
                            Log.w("CalendarVM", "Invalid priority: $priorityStr, defaulting to MEDIUM")
                            EventPriority.MEDIUM
                        }
                        
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
                    e.printStackTrace()
                    null
                }
            } else null
            
            // Determine if the event is ready to be entered in the calendar
            val readyToEnterCalendar = determineIfEventIsReady(action, eventData, finalResponse)
            
            return AIResponse(
                response = finalResponse,
                action = action,
                eventData = eventData,
                readyToEnterCalendar = readyToEnterCalendar
            )
        } catch (e: Exception) {
            Log.e("CalendarVM", "Error in parseAIResponse: ${e.message}")
            e.printStackTrace()
            return AIResponse(
                response = responseText.ifEmpty { "I'm sorry, I couldn't process that request properly." },
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
    
    /**
     * Determines if an event is ready to be entered in the calendar based on the AI response
     * and event data completeness
     */
    private fun determineIfEventIsReady(action: CalendarAction, eventData: CalendarEvent?, response: String): Boolean {
        // If action is not ADD_EVENT or event data is null, it's not ready
        if (action != CalendarAction.ADD_EVENT || eventData == null) {
            return false
        }
        
        // Check if the AI is asking for more information
        val isAskingForInfo = response.contains("what should I call it") || 
                             response.contains("what would you like to call") ||
                             response.contains("how long will it last") ||
                             response.contains("more information") ||
                             response.contains("what time") ||
                             response.contains("when is") ||
                             response.contains("where is")
        
        // If AI is asking questions, the event is not ready
        if (isAskingForInfo) {
            return false
        }
        
        // Check if event has all required fields with meaningful values
        val hasValidTitle = eventData.title.isNotBlank() && eventData.title != "Untitled Event" && eventData.title != "Event" && eventData.title != "To be determined"
        val hasValidTimes = eventData.startTime != null && eventData.endTime != null
        
        return hasValidTitle && hasValidTimes
    }
    
    private fun processCalendarAction(response: AIResponse) {
        // Create a confirmation message based on the action
        val confirmationMessage = when (response.action) {
            CalendarAction.ADD_EVENT -> {
                response.eventData?.let { event ->
                    // Only add the event to the calendar if it's ready
                    if (response.readyToEnterCalendar) {
                        // Ensure event has a valid ID and other required fields
                        val completeEvent = event.copy(
                            id = event.id.takeIf { !it.isNullOrBlank() } ?: UUID.randomUUID().toString(),
                            priority = event.priority ?: EventPriority.MEDIUM,
                            description = event.description ?: "",
                            location = event.location ?: ""
                        )
                        
                        // Force update the UI
                        _lastAction.value = null
                        
                        val currentEvents = _events.value ?: emptyList()
                        _events.value = currentEvents + completeEvent
                        
                        // Share events with timeline
                        shareEventsWithTimeline()
                        
                        // Log the event creation for debugging
                        Log.d("CalendarVM", "Added event: ${completeEvent.title} at ${completeEvent.startTime}")
                        
                        // Add a confirmation message if the AI didn't provide a clear one
                        if (!response.response.contains("added") && !response.response.contains("created")) {
                            "Event '${event.title}' added to your calendar for ${event.startTime.format(DateTimeFormatter.ofPattern("MMM d 'at' h:mm a"))}"
                        } else null
                    } else {
                        // Event is not ready, just return the AI's response
                        Log.d("CalendarVM", "Event not ready to be added to calendar: ${event.title}")
                        null
                    }
                }
            }
            CalendarAction.EDIT_EVENT -> {
                response.eventData?.let { updatedEvent ->
                    val currentEvents = _events.value ?: emptyList()
                    _events.value = currentEvents.map { 
                        if (it.id == updatedEvent.id) updatedEvent else it 
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
                    val currentEvents = _events.value ?: emptyList()
                    _events.value = currentEvents.filter { it.id != eventToDelete.id }
                    
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
            """
            {"id":"${event.id}","title":"${event.title}","startTime":"${event.startTime}","endTime":"${event.endTime}","priority":"${event.priority}","description":"${event.description ?: ""}","location":"${event.location ?: ""}"}
            """.trimIndent()
        }
        
        editor.putString("events", "[$eventsJson]")
        editor.apply()
        
        Log.d("CalendarVM", "Saved ${currentEvents.size} events to SharedPreferences")
        
        // Log for debugging
        Log.d("CalendarVM", "Shared ${currentEvents.size} events with timeline")
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
