package com.telusko.studymode.controller;

import com.telusko.studymode.dto.ChatRequest;
import com.telusko.studymode.dto.ChatResponse;
import com.telusko.studymode.dto.Conversation;
import com.telusko.studymode.service.TutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tutor")
public class TutorController {

    @Autowired
    private TutorService tutorService;

    // Start a new conversation
    @PostMapping("/start/{userId}")
    public Conversation startNewChat(@PathVariable String userId, @RequestParam String title) {
        return tutorService.startNewConversation(userId, title);
    }

    // Switch to another conversation
    @PostMapping("/switch/{userId}/{conversationId}")
    public String switchChat(@PathVariable String userId, @PathVariable String conversationId) {
        tutorService.switchConversation(userId, conversationId);
        return "Switched to conversation: " + conversationId;
    }

    // List chats for a user
    @GetMapping("/list/{userId}")
    public List<Conversation> listChats(@PathVariable String userId) {
        return tutorService.getUserConversations(userId);
    }

    // Ask a question inside a specific conversation
    @PostMapping("/ask/{userId}/{conversationId}")
    public ChatResponse askQuestion(
            @PathVariable String userId,
            @PathVariable String conversationId,
            @RequestBody ChatRequest request
    ) {
        // prefer the path conversationId; fallback to request.getConversationId() if present
        String convoIdToUse = (conversationId != null && !conversationId.isEmpty())
                ? conversationId
                : request.getConversationId();

        String answer = tutorService.generateAnswer(
                request.getQuestion(),
                userId,
                request.getUserName(),
                convoIdToUse
        );

        int turnCount = tutorService.getStudentTurnCount(userId, convoIdToUse);
        int messageCount = tutorService.getConversationLength(userId, convoIdToUse);

        return new ChatResponse(answer, turnCount, messageCount, convoIdToUse);
    }

    // Delete a single conversation
    @DeleteMapping("/clear/{userId}/{conversationId}")
    public String deleteChat(@PathVariable String userId, @PathVariable String conversationId) {
        tutorService.clearConversation(userId, conversationId);
        return "Deleted conversation " + conversationId;
    }

    // Delete all conversations for a user
    @DeleteMapping("/clearAll/{userId}")
    public String deleteAllChats(@PathVariable String userId) {
        tutorService.clearAllConversations(userId);
        return "Deleted all chats for user " + userId;
    }

    @GetMapping("/health")
    public String health() {
        return "Tutor Service is running!";
    }
}
