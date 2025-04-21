package com.example.calendarplan.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendarplan.ui.assistant.CalendarEvent
import com.example.calendarplan.ui.calendar.CalendarColors
import java.time.format.DateTimeFormatter

/**
 * An event card with action buttons for completing and deleting events
 */
@Composable
fun SwipeableEventCard(
    event: CalendarEvent,
    eventColor: Color,
    onDelete: () -> Unit,
    onComplete: () -> Unit
) {
    // The event card content
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (event.isCompleted) Color(0xFFEEEEEE) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colored indicator for event type/priority
                Box(
                    modifier = Modifier
                        .size(width = 8.dp, height = 80.dp)
                        .background(
                            if (event.isCompleted) Color.Gray else eventColor,
                            RoundedCornerShape(4.dp)
                        )
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Time column with glossy styling
                Column(
                    modifier = Modifier.width(80.dp)
                ) {
                    Text(
                        text = event.startTime.format(DateTimeFormatter.ofPattern("h:mm a")),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (event.isCompleted) Color.Gray else Color.DarkGray
                    )
                    if (event.endTime != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "to ${event.endTime.format(DateTimeFormatter.ofPattern("h:mm a"))}",
                            fontSize = 12.sp,
                            color = if (event.isCompleted) Color.Gray else Color.Gray
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Event details with emoji if available
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = event.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (event.isCompleted) Color.Gray else Color.DarkGray,
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (event.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = CalendarColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Add event description or location if available
                    event.description?.let {
                        if (it.isNotBlank()) {
                            Text(
                                text = it,
                                fontSize = 14.sp,
                                color = if (event.isCompleted) Color.Gray else Color.Gray,
                                maxLines = 2
                            )
                        }
                    }
                    
                    event.location?.let {
                        if (it.isNotBlank() && it != "To be determined") {
                            Text(
                                text = "📍 $it",
                                fontSize = 14.sp,
                                color = if (event.isCompleted) Color.Gray else Color.Gray,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            
            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                // Complete button
                if (!event.isCompleted) {
                    TextButton(
                        onClick = onComplete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF4CAF50) // Green
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Complete",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Complete")
                    }
                }
                
                // Delete button
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFF44336) // Red
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}
