package com.example.calendarplan.ui.assistant

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.calendarplan.R
import com.example.calendarplan.ui.calendar.CalendarColors
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarAssistantFragment : Fragment() {
    
    private lateinit var viewModel: CalendarAssistantViewModel
    
    // Speech recognition launcher
    private val speechRecognizer = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            if (spokenText.isNotEmpty()) {
                viewModel.sendMessage(spokenText)
            }
        }
    }
    
    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, start speech recognition
            startSpeechRecognitionIntent()
        } else {
            // Permission denied, show a message
            Toast.makeText(
                requireContext(),
                "Microphone permission is required for speech input",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Create the ViewModel with our custom factory
        val factory = CalendarAssistantViewModelFactory(requireContext())
        viewModel = ViewModelProvider(requireActivity(), factory).get(CalendarAssistantViewModel::class.java)
        
        return ComposeView(requireContext()).apply {
            // Dispose of the Composition when the view's LifecycleOwner is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            
            setContent {
                MaterialTheme {
                    CalendarAssistantScreen(
                        viewModel = viewModel,
                        onSpeechInputRequested = { startSpeechRecognition() },
                        onNavigateToTimeline = {
                            findNavController().navigate(R.id.nav_timeline)
                        },
                        fragment = this@CalendarAssistantFragment
                    )
                }
            }
        }
    }
    
    private fun startSpeechRecognition() {
        // Check for microphone permission
        when (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, start speech recognition
                startSpeechRecognitionIntent()
            }
            else -> {
                // Request the permission
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    private fun startSpeechRecognitionIntent() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to your Calendar Assistant")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1) // Only get top result
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000) // Min 3 seconds
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500) // 1.5s silence = complete
        }
        
        try {
            speechRecognizer.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                requireContext(),
                "Speech recognition not supported on this device",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Error starting speech recognition: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            e.printStackTrace() // Log the exception for debugging
        }
    }
}

@Composable
fun CalendarAssistantScreen(
    viewModel: CalendarAssistantViewModel,
    onSpeechInputRequested: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    fragment: Fragment? = null
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val events by viewModel.events.observeAsState(emptyList())
    val lastAction by viewModel.lastAction.collectAsState()
    
    var userInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalendarColors.Background)
            .padding(16.dp)
    ) {
        // Header with title and navigation button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Calendar Assistant",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            // Button to navigate to Timeline
            Button(
                onClick = onNavigateToTimeline,
                colors = ButtonDefaults.buttonColors(containerColor = CalendarColors.Primary),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Timeline View",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Timeline", color = Color.White)
                }
            }
        }
        // Chat messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ChatMessageItem(message = message)
            }
            
            // Show loading indicator when waiting for AI response
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Custom loading indicator instead of CircularProgressIndicator
                        // to avoid version compatibility issues
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(CalendarColors.Primary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(CalendarColors.Primary.copy(alpha = 0.7f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(CalendarColors.Primary.copy(alpha = 0.4f), CircleShape)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Timeline view of events (animated in when showing events)
        AnimatedVisibility(
            visible = lastAction == CalendarAction.SHOW_EVENTS || lastAction == CalendarAction.SUMMARIZE,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            EventTimelineView(events = events)
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Navigation to Wheel Calendar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    // Navigate to wheel calendar view
                    fragment?.let {
                        val navController = androidx.navigation.Navigation.findNavController(it.requireActivity(), R.id.nav_host_fragment_content_main)
                        navController.navigate(R.id.nav_calendar)
                    }
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = CalendarColors.Primary
                )
            ) {
                Text("View Wheel Calendar")
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Calendar",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        
        // Input field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text input field
            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                ),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (userInput.isNotEmpty()) {
                        viewModel.sendMessage(userInput)
                        userInput = ""
                    }
                })
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Voice input button
            IconButton(
                onClick = onSpeechInputRequested,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CalendarColors.Primary)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardVoice,
                    contentDescription = "Voice Input",
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Send button (with a different icon to avoid overlap issues)
            IconButton(
                onClick = {
                    if (userInput.isNotEmpty()) {
                        viewModel.sendMessage(userInput)
                        userInput = ""
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (userInput.isNotEmpty()) CalendarColors.Primary else Color.Gray)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward, // Changed from Send to ArrowForward
                    contentDescription = "Send Message",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isUser) CalendarColors.Primary else Color.White
    val textColor = if (message.isUser) Color.White else Color.Black
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = RoundedCornerShape(
                topStart = if (message.isUser) 12.dp else 0.dp,
                topEnd = if (message.isUser) 0.dp else 12.dp,
                bottomStart = 12.dp,
                bottomEnd = 12.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = backgroundColor
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Show event ready indicator if applicable
                if (!message.isUser && message.readyToEnterCalendar && message.eventData != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .background(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Event Ready",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Event ready to be added to calendar",
                            color = Color(0xFF2E7D32),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Text(
                    text = message.content,
                    color = textColor
                )
                
                // Show event details if available and ready to enter calendar
                if (!message.isUser && message.readyToEnterCalendar && message.eventData != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Event card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF5F5F5),
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = message.eventData.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.DarkGray
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Time",
                                    tint = CalendarColors.Primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${message.eventData.startTime.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))} - ${message.eventData.endTime.format(DateTimeFormatter.ofPattern("h:mm a"))}",
                                    fontSize = 14.sp,
                                    color = Color.DarkGray
                                )
                            }
                            
                            if (message.eventData.location.isNotBlank() && message.eventData.location != "To be determined") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = "Location",
                                        tint = CalendarColors.Primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = message.eventData.location,
                                        fontSize = 14.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Check if the message contains event information
                if (!message.isUser && (message.content.contains("added") || 
                                       message.content.contains("scheduled") || 
                                       message.content.contains("created"))) {
                    Spacer(modifier = Modifier.height(8.dp))
                    EventCardPreview(message.content)
                }
                
                // Show timestamp
                Text(
                    text = message.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
                    fontSize = 10.sp,
                    color = if (message.isUser) Color.White.copy(alpha = 0.7f) else Color.Gray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun EventCardPreview(messageContent: String) {
    // Extract event details from the message
    val eventTitle = extractEventTitle(messageContent)
    val eventTime = extractEventTime(messageContent)
    
    if (eventTitle.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = CalendarColors.Secondary.copy(alpha = 0.2f)
            )
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Event",
                    tint = CalendarColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = eventTitle,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    if (eventTime.isNotEmpty()) {
                        Text(
                            text = eventTime,
                            fontSize = 12.sp,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }
    }
}

// Helper functions to extract event details from message
private fun extractEventTitle(message: String): String {
    val patterns = listOf(
        "Event '(.+?)' added",
        "added (.+?) to your calendar",
        "scheduled (.+?) for",
        "created (.+?) on"
    )
    
    for (pattern in patterns) {
        val regex = pattern.toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(message)
        if (match != null) {
            return match.groupValues[1]
        }
    }
    return ""
}

private fun extractEventTime(message: String): String {
    val patterns = listOf(
        "for (.+?(AM|PM))",
        "for (.+?\\d{1,2}:\\d{2})",
        "on (.+?\\d{1,2}(st|nd|rd|th))",
        "at (\\d{1,2}:\\d{2})"
    )
    
    for (pattern in patterns) {
        val regex = pattern.toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(message)
        if (match != null) {
            return match.groupValues[1]
        }
    }
    return ""
}

@Composable
fun EventTimelineView(events: List<CalendarEvent>) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.calendar_animation))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Upcoming Events",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                
                // Calendar animation
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (events.isEmpty()) {
                Text(
                    text = "No events scheduled",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            } else {
                events.sortedBy { it.startTime }.forEach { event ->
                    EventItem(event = event)
                }
            }
        }
    }
}

@Composable
fun EventItem(event: CalendarEvent) {
    val priorityColor = when (event.priority) {
        EventPriority.HIGH -> CalendarColors.HighPriority
        EventPriority.MEDIUM -> CalendarColors.MediumPriority
        EventPriority.LOW -> CalendarColors.LowPriority
    }
    
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 500,
            easing = FastOutSlowInEasing
        ),
        label = "Event Animation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority indicator
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(priorityColor, CircleShape)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Event details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = event.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                Text(
                    text = event.startTime.format(DateTimeFormatter.ofPattern("MMM d, HH:mm")) + 
                            " - " + 
                            event.endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                
                if (event.location.isNotEmpty()) {
                    Text(
                        text = "📍 ${event.location}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                
                if (event.description.isNotEmpty()) {
                    Text(
                        text = event.description,
                        fontSize = 14.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
