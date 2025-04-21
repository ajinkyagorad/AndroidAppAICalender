package com.example.calendarplan.ui.timeline

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendarplan.ui.assistant.CalendarEvent
import com.example.calendarplan.ui.assistant.EventPriority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelChildren
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TimelineViewModel(private val context: Context) : ViewModel() {
    // Broadcast receiver for event reload notifications
    private val eventReloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.calendarplan.RELOAD_EVENTS") {
                Log.d("TimelineVM", "Received broadcast to reload events")
                loadEventsFromSharedPreferences()
            }
        }
    }
    
    private val _events = MutableLiveData<List<CalendarEvent>>(emptyList())
    val events: LiveData<List<CalendarEvent>> = _events
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _filterMode = MutableStateFlow(FilterMode.ALL)
    val filterMode: StateFlow<FilterMode> = _filterMode.asStateFlow()
    
    init {
        // Load events from shared preferences on initialization
        loadEventsFromSharedPreferences()
        
        // Register broadcast receiver with proper flags for Android security requirements
        val filter = IntentFilter("com.example.calendarplan.RELOAD_EVENTS")
        context.registerReceiver(eventReloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        
        Log.d("TimelineVM", "Registered broadcast receiver for event reloads")
        
        // Automatically refresh events periodically
        startAutoRefresh()
    }
    
    override fun onCleared() {
        super.onCleared()
        // Unregister broadcast receiver
        try {
            context.unregisterReceiver(eventReloadReceiver)
            Log.d("TimelineVM", "Unregistered broadcast receiver")
        } catch (e: Exception) {
            Log.e("TimelineVM", "Error unregistering receiver: ${e.message}")
        }
        
        // Cancel any ongoing jobs when ViewModel is cleared
        viewModelScope.coroutineContext.cancelChildren()
    }
    
    fun setFilterMode(mode: FilterMode) {
        viewModelScope.launch {
            _filterMode.value = mode
        }
    }
    
    fun setEvents(eventList: List<CalendarEvent>) {
        _events.value = eventList
    }
    
    /**
     * Starts automatic periodic refresh of events
     * This helps keep the timeline in sync with events created in the AI Assistant
     */
    private fun startAutoRefresh() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000) // 5 seconds initial delay
            while (true) {
                try {
                    loadEventsFromSharedPreferences()
                    Log.d("TimelineVM", "Auto-refreshed events")
                } catch (e: Exception) {
                    Log.e("TimelineVM", "Error during auto-refresh: ${e.message}")
                }
                kotlinx.coroutines.delay(10000) // Refresh every 10 seconds
            }
        }
    }
    
    fun addEvent(event: CalendarEvent) {
        try {
            Log.d("TimelineVM", "Adding event: ${event.title} at ${event.startTime}")
            val currentList = _events.value ?: emptyList()
            
            // Check if event with same ID already exists
            val existingEvent = currentList.find { it.id == event.id }
            if (existingEvent != null) {
                Log.d("TimelineVM", "Event with ID ${event.id} already exists, updating instead")
                updateEvent(event)
                return
            }
            
            // Update the LiveData
            _events.value = currentList + event
            
            // Save to SharedPreferences
            saveEventsToSharedPreferences(_events.value ?: emptyList())
            
            Log.d("TimelineVM", "Successfully added event: ${event.title}")
        } catch (e: Exception) {
            Log.e("TimelineVM", "Error adding event: ${e.message}")
        }
    }
    
    fun removeEvent(eventId: String) {
        try {
            Log.d("TimelineVM", "Removing event with ID: $eventId")
            val currentList = _events.value ?: emptyList()
            
            // Check if event exists
            val eventExists = currentList.any { it.id == eventId }
            if (!eventExists) {
                Log.w("TimelineVM", "Event with ID $eventId not found, nothing to remove")
                return
            }
            
            // Update the LiveData
            val updatedList = currentList.filter { it.id != eventId }
            _events.value = updatedList
            
            // Save to SharedPreferences
            saveEventsToSharedPreferences(updatedList)
            
            Log.d("TimelineVM", "Successfully removed event with ID: $eventId")
        } catch (e: Exception) {
            Log.e("TimelineVM", "Error removing event: ${e.message}")
        }
    }
    
    fun updateEvent(updatedEvent: CalendarEvent) {
        try {
            Log.d("TimelineVM", "Updating event: ${updatedEvent.title}")
            val currentList = _events.value ?: emptyList()
            
            // Check if event exists
            val eventExists = currentList.any { it.id == updatedEvent.id }
            if (!eventExists) {
                Log.w("TimelineVM", "Event with ID ${updatedEvent.id} not found, adding instead")
                addEvent(updatedEvent)
                return
            }
            
            // Update the LiveData
            val updatedList = currentList.map { 
                if (it.id == updatedEvent.id) updatedEvent else it 
            }
            _events.value = updatedList
            
            // Save to SharedPreferences
            saveEventsToSharedPreferences(updatedList)
            
            Log.d("TimelineVM", "Successfully updated event: ${updatedEvent.title}")
        } catch (e: Exception) {
            Log.e("TimelineVM", "Error updating event: ${e.message}")
        }
    }
    
    // Load events shared by CalendarAssistantViewModel
    fun loadEventsFromSharedPreferences() {
        Log.d("TimelineVM", "Loading events from SharedPreferences - explicit call")
        _isLoading.value = true
        try {
            val sharedPrefs = context.getSharedPreferences("CalendarEvents", Context.MODE_PRIVATE)
            val eventsJsonStr = sharedPrefs.getString("events", "[]")
            
            Log.d("TimelineVM", "Loading events from SharedPreferences: $eventsJsonStr")
            
            if (eventsJsonStr.isNullOrBlank() || eventsJsonStr == "[]") {
                Log.d("TimelineVM", "No events found in shared preferences")
                _events.value = emptyList()
                _isLoading.value = false
                return
            }
            
            try {
                val jsonArray = JSONArray(eventsJsonStr)
                Log.d("TimelineVM", "Found ${jsonArray.length()} events in SharedPreferences")
                val eventsList = mutableListOf<CalendarEvent>()
                
                for (i in 0 until jsonArray.length()) {
                    try {
                        val eventJson = jsonArray.getJSONObject(i)
                        Log.d("TimelineVM", "Processing event ${i+1}/${jsonArray.length()}: ${eventJson}")
                        
                        // First check if we have a valid ID
                        val id = eventJson.optString("id", "")
                        if (id.isBlank()) {
                            Log.w("TimelineVM", "Skipping event with blank ID")
                            continue
                        }
                        
                        // Parse dates with fallback - support both formats
                        val startTimeStr = eventJson.optString("startTime", "")
                        val endTimeStr = eventJson.optString("endTime", "")
                        
                        Log.d("TimelineVM", "Event time strings - Start: $startTimeStr, End: $endTimeStr")
                        
                        // More robust parsing for start time - try multiple formats
                        val startTime = if (startTimeStr.isNotBlank()) {
                            try {
                                // First try ISO-8601 format (2025-04-21T10:00:00)
                                try {
                                    val parsedTime = LocalDateTime.parse(startTimeStr)
                                    Log.d("TimelineVM", "Successfully parsed ISO-8601 start time: $parsedTime")
                                    parsedTime
                                } catch (e: Exception) {
                                    try {
                                        // Then try custom format (yyyy-MM-dd HH:mm)
                                        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                        val parsedTime = LocalDateTime.parse(startTimeStr, dateTimeFormatter)
                                        Log.d("TimelineVM", "Successfully parsed custom format start time: $parsedTime")
                                        parsedTime
                                    } catch (e2: Exception) {
                                        // Try one more format that might be used
                                        try {
                                            val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                                            val parsedTime = LocalDateTime.parse(startTimeStr, dateTimeFormatter)
                                            Log.d("TimelineVM", "Successfully parsed alternative format start time: $parsedTime")
                                            parsedTime
                                        } catch (e3: Exception) {
                                            Log.w("TimelineVM", "Error parsing start time: $startTimeStr, using current time")
                                            LocalDateTime.now()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("TimelineVM", "Error parsing start time: $startTimeStr, using current time")
                                LocalDateTime.now()
                            }
                        } else {
                            Log.w("TimelineVM", "Empty start time string, using current time")
                            LocalDateTime.now()
                        }
                        
                        // More robust parsing for end time - try multiple formats
                        val endTime = if (endTimeStr.isNotBlank()) {
                            try {
                                // First try ISO-8601 format (2025-04-21T11:00:00)
                                try {
                                    val parsedTime = LocalDateTime.parse(endTimeStr)
                                    Log.d("TimelineVM", "Successfully parsed ISO-8601 end time: $parsedTime")
                                    parsedTime
                                } catch (e: Exception) {
                                    try {
                                        // Then try custom format (yyyy-MM-dd HH:mm)
                                        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                        val parsedTime = LocalDateTime.parse(endTimeStr, dateTimeFormatter)
                                        Log.d("TimelineVM", "Successfully parsed custom format end time: $parsedTime")
                                        parsedTime
                                    } catch (e2: Exception) {
                                        // Try one more format that might be used
                                        try {
                                            val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                                            val parsedTime = LocalDateTime.parse(endTimeStr, dateTimeFormatter)
                                            Log.d("TimelineVM", "Successfully parsed alternative format end time: $parsedTime")
                                            parsedTime
                                        } catch (e3: Exception) {
                                            Log.w("TimelineVM", "Error parsing end time: $endTimeStr, using startTime + 1 hour")
                                            startTime.plusHours(1)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("TimelineVM", "Error parsing end time: $endTimeStr, using startTime + 1 hour")
                                startTime.plusHours(1)
                            }
                        } else {
                            Log.w("TimelineVM", "Empty end time string, using startTime + 1 hour")
                            startTime.plusHours(1)
                        }
                        
                        // Parse priority from string to enum
                        val priorityStr = eventJson.optString("priority", EventPriority.MEDIUM.name)
                        val priority = try {
                            EventPriority.valueOf(priorityStr)
                        } catch (e: Exception) {
                            Log.w("TimelineVM", "Invalid priority: $priorityStr, defaulting to MEDIUM")
                            EventPriority.MEDIUM
                        }
                        
                        // Get title with fallback
                        val title = eventJson.optString("title", "")
                        if (title.isBlank()) {
                            Log.w("TimelineVM", "Event has blank title, using 'Untitled Event'")
                        }
                        
                        val event = CalendarEvent(
                            id = id,
                            title = if (title.isBlank()) "Untitled Event" else title,
                            description = eventJson.optString("description", ""),
                            startTime = startTime,
                            endTime = endTime,
                            location = eventJson.optString("location", ""),
                            priority = priority,
                            isCompleted = eventJson.optBoolean("isCompleted", false)
                        )
                        
                        Log.d("TimelineVM", "Created event: ${event.title} at ${event.startTime}")
                        eventsList.add(event)
                    } catch (e: Exception) {
                        Log.e("TimelineVM", "Error parsing event JSON: ${e.message}")
                        e.printStackTrace()
                    }
                }
                
                if (eventsList.isNotEmpty()) {
                    Log.d("TimelineVM", "Loaded ${eventsList.size} events from shared preferences")
                    _events.postValue(eventsList)
                    
                    // Log each event for debugging
                    eventsList.forEach { event ->
                        Log.d("TimelineVM", "Loaded event: ${event.title} at ${event.startTime}")
                    }
                } else {
                    Log.w("TimelineVM", "No valid events were parsed from JSON")
                }
            } catch (e: Exception) {
                Log.e("TimelineVM", "Error processing JSON array: ${e.message}")
                e.printStackTrace()
            }
            
            _isLoading.value = false
        } catch (e: Exception) {
            Log.e("TimelineVM", "Error loading events from shared preferences: ${e.message}")
            _isLoading.value = false
        }
    }
    
    fun getFilteredEvents(): List<CalendarEvent> {
        val allEvents = _events.value ?: emptyList()
        val now = LocalDateTime.now()
        
        return when (_filterMode.value) {
            FilterMode.ALL -> allEvents
            FilterMode.TODAY -> allEvents.filter { 
                it.startTime.toLocalDate() == now.toLocalDate()
            }
            FilterMode.UPCOMING -> allEvents.filter { 
                it.startTime.isAfter(now)
            }
            FilterMode.PAST -> allEvents.filter { 
                it.endTime.isBefore(now)
            }
        }
    }
    
    /**
     * Delete an event from the timeline
     */
    fun deleteEvent(eventId: String) {
        try {
            // Get current events
            val currentEvents = _events.value ?: emptyList()
            val eventToDelete = currentEvents.find { it.id == eventId }
            
            if (eventToDelete == null) {
                Log.e("TimelineVM", "Cannot delete event: Event with ID $eventId not found")
                return
            }
            
            // Create updated list without the deleted event
            val updatedEvents = currentEvents.filter { it.id != eventId }
            
            // Update the LiveData on the main thread
            _events.postValue(updatedEvents)
            
            // Save to shared preferences immediately
            saveEventsToSharedPreferences(updatedEvents)
            
            // Log detailed information for debugging
            Log.d("TimelineVM", "Deleted event: '${eventToDelete.title}' with ID: $eventId")
            Log.d("TimelineVM", "Events before: ${currentEvents.size}, Events after: ${updatedEvents.size}")
            
            // Force a refresh from SharedPreferences to ensure consistency
            viewModelScope.launch {
                // Small delay to ensure SharedPreferences has been updated
                kotlinx.coroutines.delay(100)
                loadEventsFromSharedPreferences()
                
                // Verify the event was actually deleted
                val eventsAfterRefresh = _events.value ?: emptyList()
                val stillExists = eventsAfterRefresh.any { it.id == eventId }
                if (stillExists) {
                    Log.e("TimelineVM", "ERROR: Event with ID $eventId still exists after deletion!")
                    // Try one more time with direct modification
                    _events.postValue(eventsAfterRefresh.filter { it.id != eventId })
                    saveEventsToSharedPreferences(eventsAfterRefresh.filter { it.id != eventId })
                } else {
                    Log.d("TimelineVM", "Verified event with ID $eventId was successfully deleted")
                }
            }
        } catch (e: Exception) {
            Log.e("TimelineVM", "Error deleting event: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Mark an event as completed
     */
    fun markEventAsCompleted(eventId: String) {
        val currentEvents = _events.value ?: return
        val updatedEvents = currentEvents.map { event ->
            if (event.id == eventId) {
                // Create a copy with completed status
                event.copy(isCompleted = true)
            } else {
                event
            }
        }
        _events.value = updatedEvents
        saveEventsToSharedPreferences(updatedEvents)
        Log.d("TimelineVM", "Marked event as completed: $eventId")
    }
    
    /**
     * Save events to shared preferences
     */
    private fun saveEventsToSharedPreferences(events: List<CalendarEvent>) {
        try {
            Log.d("TimelineVM", "Saving ${events.size} events to SharedPreferences")
            val jsonArray = JSONArray()
            
            // Use a consistent date format for saving
            val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            
            events.forEach { event ->
                try {
                    // Ensure we have a valid ID
                    if (event.id.isBlank()) {
                        Log.w("TimelineVM", "Skipping event with blank ID: ${event.title}")
                        return@forEach
                    }
                    
                    // Format dates consistently
                    val startTimeStr = event.startTime.format(dateTimeFormatter)
                    val endTimeStr = event.endTime.format(dateTimeFormatter)
                    
                    // Create JSON object with all required fields
                    val eventJson = JSONObject().apply {
                        put("id", event.id)
                        put("title", event.title)
                        put("description", event.description ?: "")
                        put("startTime", startTimeStr)
                        put("endTime", endTimeStr)
                        put("location", event.location ?: "")
                        put("priority", event.priority.name)
                        put("isCompleted", event.isCompleted)
                    }
                    
                    jsonArray.put(eventJson)
                    Log.d("TimelineVM", "Added event to JSON: ${event.title} at $startTimeStr")
                } catch (e: Exception) {
                    Log.e("TimelineVM", "Error adding event to JSON: ${e.message}")
                }
            }
            
            // Use commit() instead of apply() to ensure immediate write
            val sharedPrefs = context.getSharedPreferences("CalendarEvents", Context.MODE_PRIVATE)
            val success = sharedPrefs.edit().putString("events", jsonArray.toString()).commit()
            
            if (success) {
                Log.d("TimelineVM", "Successfully saved ${events.size} events to SharedPreferences")
            } else {
                Log.e("TimelineVM", "Failed to save events to SharedPreferences")
            }
        } catch (e: Exception) {
            Log.e("TimelineVM", "Error saving events to SharedPreferences: ${e.message}")
            e.printStackTrace()
        }
    }
}

enum class FilterMode {
    ALL,
    TODAY,
    UPCOMING,
    PAST
}
