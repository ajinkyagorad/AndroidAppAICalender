package com.example.calendarplan.ui.timeline

import android.content.Context
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TimelineViewModel(private val context: Context) : ViewModel() {
    
    private val _events = MutableLiveData<List<CalendarEvent>>(emptyList())
    val events: LiveData<List<CalendarEvent>> = _events
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _filterMode = MutableStateFlow(FilterMode.ALL)
    val filterMode: StateFlow<FilterMode> = _filterMode.asStateFlow()
    
    init {
        // Load events from shared preferences on initialization
        loadEventsFromSharedPreferences()
        
        // Automatically refresh events periodically
        startAutoRefresh()
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
            kotlinx.coroutines.delay(30000) // 30 seconds initial delay
            while (true) {
                try {
                    loadEventsFromSharedPreferences()
                    Log.d("TimelineVM", "Auto-refreshed events")
                } catch (e: Exception) {
                    Log.e("TimelineVM", "Error during auto-refresh: ${e.message}")
                }
                kotlinx.coroutines.delay(60000) // Refresh every minute
            }
        }
    }
    
    fun addEvent(event: CalendarEvent) {
        val currentList = _events.value ?: emptyList()
        _events.value = currentList + event
    }
    
    fun removeEvent(eventId: String) {
        val currentList = _events.value ?: emptyList()
        _events.value = currentList.filter { it.id != eventId }
    }
    
    fun updateEvent(updatedEvent: CalendarEvent) {
        val currentList = _events.value ?: emptyList()
        _events.value = currentList.map { 
            if (it.id == updatedEvent.id) updatedEvent else it 
        }
    }
    
    // Load events shared by CalendarAssistantViewModel
    fun loadEventsFromSharedPreferences() {
        _isLoading.value = true
        try {
            val sharedPrefs = context.getSharedPreferences("CalendarEvents", Context.MODE_PRIVATE)
            val eventsJsonStr = sharedPrefs.getString("events", "[]")
            
            if (eventsJsonStr.isNullOrBlank() || eventsJsonStr == "[]") {
                Log.d("TimelineVM", "No events found in shared preferences")
                return
            }
            
            val jsonArray = JSONArray(eventsJsonStr)
            val eventsList = mutableListOf<CalendarEvent>()
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val eventJson = jsonArray.getJSONObject(i)
                    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                    
                    // Parse dates with fallback
                    val startTimeStr = eventJson.optString("startTime", "")
                    val endTimeStr = eventJson.optString("endTime", "")
                    
                    val startTime = if (startTimeStr.isNotBlank()) {
                        try {
                            LocalDateTime.parse(startTimeStr, dateTimeFormatter)
                        } catch (e: Exception) {
                            LocalDateTime.now()
                        }
                    } else {
                        LocalDateTime.now()
                    }
                    
                    val endTime = if (endTimeStr.isNotBlank()) {
                        try {
                            LocalDateTime.parse(endTimeStr, dateTimeFormatter)
                        } catch (e: Exception) {
                            startTime.plusHours(1)
                        }
                    } else {
                        startTime.plusHours(1)
                    }
                    
                    // Parse priority from string to enum
                    val priorityStr = eventJson.optString("priority", EventPriority.MEDIUM.name)
                    val priority = try {
                        EventPriority.valueOf(priorityStr)
                    } catch (e: Exception) {
                        EventPriority.MEDIUM
                    }
                    
                    val event = CalendarEvent(
                        id = eventJson.optString("id", ""),
                        title = eventJson.optString("title", "Untitled Event"),
                        description = eventJson.optString("description", ""),
                        startTime = startTime,
                        endTime = endTime,
                        location = eventJson.optString("location", ""),
                        priority = priority
                    )
                    
                    eventsList.add(event)
                } catch (e: Exception) {
                    Log.e("TimelineVM", "Error parsing event JSON: ${e.message}")
                }
            }
            
            if (eventsList.isNotEmpty()) {
                Log.d("TimelineVM", "Loaded ${eventsList.size} events from shared preferences")
                _events.value = eventsList
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
        val currentEvents = _events.value ?: return
        val updatedEvents = currentEvents.filter { it.id != eventId }
        _events.value = updatedEvents
        saveEventsToSharedPreferences(updatedEvents)
        Log.d("TimelineVM", "Deleted event with ID: $eventId")
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
            val jsonArray = JSONArray()
            events.forEach { event ->
                val eventJson = JSONObject().apply {
                    put("id", event.id)
                    put("title", event.title)
                    put("description", event.description ?: "")
                    put("startTime", event.startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    put("endTime", event.endTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: "")
                    put("location", event.location ?: "")
                    put("priority", event.priority?.name ?: "")
                    put("isCompleted", event.isCompleted)
                }
                jsonArray.put(eventJson)
            }
            
            val sharedPrefs = context.getSharedPreferences("CalendarEvents", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("events", jsonArray.toString()).apply()
            Log.d("TimelineVM", "Saved ${events.size} events to shared preferences")
        } catch (e: Exception) {
            Log.e("TimelineVM", "Error saving events to shared preferences: ${e.message}")
        }
    }
}

enum class FilterMode {
    ALL,
    TODAY,
    UPCOMING,
    PAST
}
