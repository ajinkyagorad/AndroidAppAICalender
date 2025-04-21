# CalendarPlan App - Known Issues and Future Improvements

## Current Issues

1. **Event Deletion Issues**
   - Deletion of preexisting cards doesn't work consistently
   - Some events may remain in the system after deletion attempts
   - Need better handling of event identification for deletion

2. **Timeline Update Problems**
   - Timeline view doesn't always update when new events are added
   - Broadcast communication between components is inconsistent
   - Events sometimes disappear after app restart

3. **AI Response Parsing**
   - Gemini AI sometimes returns responses that don't match expected JSON format
   - Error handling for malformed AI responses needs improvement
   - Need better fallback mechanisms when AI doesn't provide structured data

4. **Date/Time Parsing**
   - Multiple date formats cause inconsistencies in event display
   - Time zone handling is incomplete
   - Some date strings from AI can't be properly parsed

5. **UI/UX Issues**
   - Lack of visual feedback when events are created or deleted
   - No loading indicators during AI processing
   - Timeline view needs better organization of events

## Future Improvements

1. **AI Integration Enhancements**
   - Improve prompt engineering for more consistent AI responses
   - Implement a more robust JSON schema for AI communication
   - Add examples in prompts to guide AI toward better structured outputs

2. **Error Recovery System**
   - Add retry mechanisms for failed AI interactions
   - Implement graceful degradation when AI services are unavailable
   - Better error messages for users when operations fail

3. **User Experience Improvements**
   - Add animations for state transitions
   - Implement drag-and-drop for event management
   - Add event categories and color coding

4. **Data Management**
   - Implement proper database storage instead of SharedPreferences
   - Add cloud synchronization capabilities
   - Implement data backup and restore features

5. **Testing and Quality Assurance**
   - Add comprehensive unit tests for AI integration
   - Implement UI tests for critical user flows
   - Add performance monitoring for AI response times

## Priority Items

The following issues should be addressed first:

1. Fix event deletion for all event types
2. Ensure consistent timeline updates when events are modified
3. Improve AI response parsing reliability
4. Enhance date/time handling for all formats
5. Add proper database storage for events

## Technical Debt

1. Replace SharedPreferences with Room database
2. Refactor broadcast communication to use LiveData or StateFlow
3. Implement proper dependency injection
4. Separate AI communication into its own service layer
5. Add comprehensive error logging and analytics
