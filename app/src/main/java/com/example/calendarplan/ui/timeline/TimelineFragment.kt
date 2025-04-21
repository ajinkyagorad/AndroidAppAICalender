package com.example.calendarplan.ui.timeline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.calendarplan.R
import com.example.calendarplan.ui.assistant.CalendarAssistantViewModel
import com.example.calendarplan.ui.assistant.CalendarAssistantViewModelFactory
import com.example.calendarplan.ui.assistant.CalendarEvent
import com.example.calendarplan.ui.assistant.EventPriority
import com.example.calendarplan.ui.calendar.CalendarColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TimelineFragment : Fragment() {
    
    private lateinit var calendarViewModel: CalendarAssistantViewModel
    private lateinit var timelineViewModel: TimelineViewModel
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Share the CalendarAssistantViewModel with the CalendarAssistantFragment
        val calendarFactory = CalendarAssistantViewModelFactory(requireContext())
        calendarViewModel = ViewModelProvider(requireActivity(), calendarFactory).get(CalendarAssistantViewModel::class.java)
        
        // Create the TimelineViewModel with our custom factory
        val factory = TimelineViewModelFactory(requireContext())
        timelineViewModel = ViewModelProvider(this, factory).get(TimelineViewModel::class.java)
        
        return ComposeView(requireContext()).apply {
            // Dispose of the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            setContent {
                MaterialTheme {
                    TimelineScreen(
                        calendarViewModel = calendarViewModel,
                        timelineViewModel = timelineViewModel,
                        onNavigateToAssistant = {
                            findNavController().navigate(R.id.nav_assistant)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineScreen(
    calendarViewModel: CalendarAssistantViewModel,
    timelineViewModel: TimelineViewModel,
    onNavigateToAssistant: () -> Unit
) {
    // State for selected date (for day navigation)
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    
    // Get events from both ViewModels
    val calendarEvents by calendarViewModel.events.observeAsState(emptyList())
    val timelineEvents by timelineViewModel.events.observeAsState(emptyList())
    
    // Combine events from both sources, prioritizing calendar events
    val combinedEvents = remember(calendarEvents, timelineEvents) {
        val result = calendarEvents.toMutableList()
        // Add timeline events that aren't already in calendar events
        timelineEvents.forEach { timelineEvent ->
            if (result.none { it.id == timelineEvent.id }) {
                result.add(timelineEvent)
            }
        }
        result
    }
    
    // Group events by date
    val eventsByDate = combinedEvents.groupBy { it.startTime.toLocalDate() }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp)
        ) {
            
            // Add a header with refresh button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Timeline",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Button to navigate to AI Assistant
                Button(
                    onClick = onNavigateToAssistant,
                    colors = ButtonDefaults.buttonColors(containerColor = CalendarColors.Primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "AI Assistant",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Assistant", color = Color.White)
                    }
                }
                
                // Refresh button
                FloatingActionButton(
                    onClick = { 
                        timelineViewModel.loadEventsFromSharedPreferences() 
                    },
                    modifier = Modifier.size(40.dp),
                    containerColor = CalendarColors.Primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Events",
                        tint = Color.White
                    )
                }
            }
            
            // Day navigation controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous day button
                IconButton(
                    onClick = { 
                        selectedDate = selectedDate.minusDays(1)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Previous Day",
                        tint = CalendarColors.Primary
                    )
                }
                
                // Current date display
                Text(
                    text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                
                // Next day button
                IconButton(
                    onClick = { 
                        selectedDate = selectedDate.plusDays(1)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Next Day",
                        tint = CalendarColors.Primary
                    )
                }
            }
            
            // Filter events for the selected date
            val eventsForSelectedDate = combinedEvents.filter { 
                it.startTime.toLocalDate() == selectedDate 
            }
            
            if (eventsForSelectedDate.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Add a subtle animation for empty state
                        val infiniteTransition = rememberInfiniteTransition()
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.9f,
                            targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = "No Events",
                            tint = Color.Gray.copy(alpha = 0.7f),
                            modifier = Modifier
                                .size(80.dp)
                                .scale(scale)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "No events scheduled",
                            color = Color.Gray,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Text(
                            text = "Use the AI Assistant to add events",
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Only show the selected date
                    if (eventsByDate.containsKey(selectedDate)) {
                        item {
                            // Animated date header with glossy effect
                            val isToday = selectedDate.isEqual(LocalDate.now())
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isToday) CalendarColors.Primary else CalendarColors.Primary.copy(alpha = 0.7f),
                                shadowElevation = 2.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 16.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isToday) {
                                            // Pulsating star animation for today
                                            val infiniteTransition = rememberInfiniteTransition()
                                            val alpha by infiniteTransition.animateFloat(
                                                initialValue = 0.7f,
                                                targetValue = 1f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(1000),
                                                    repeatMode = RepeatMode.Reverse
                                                )
                                            )
                                            
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Today",
                                                tint = Color.White.copy(alpha = alpha),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        
                                        Text(
                                            text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Animated event cards
                        itemsIndexed(eventsByDate[selectedDate] ?: emptyList()) { index, event ->
                            // Event card with staggered animation
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(animationSpec = tween(durationMillis = 300, delayMillis = index * 100)) +
                                        slideInVertically(animationSpec = tween(durationMillis = 300, delayMillis = index * 100)) { it / 2 }
                            ) {
                                // Determine event color based on priority or AI-detected mood
                                val eventColor = when(event.priority) {
                                    EventPriority.HIGH -> Color(0xFFE57373) // Red for high priority
                                    EventPriority.MEDIUM -> Color(0xFF64B5F6) // Blue for medium priority
                                    EventPriority.LOW -> Color(0xFF81C784) // Green for low priority
                                    else -> Color(0xFF9575CD) // Purple for default
                                }
                                
                                // Add swipe-to-dismiss functionality
                                SwipeableEventCard(
                                    event = event,
                                    eventColor = eventColor,
                                    onDelete = { timelineViewModel.deleteEvent(event.id) },
                                    onComplete = { timelineViewModel.markEventAsCompleted(event.id) }
                                )
                            }
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
        
        // Add floating action button to quickly add events
        FloatingActionButton(
            onClick = onNavigateToAssistant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp),
            containerColor = CalendarColors.Primary
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "Add Event with AI",
                tint = Color.White
            )
        }
    }
}
