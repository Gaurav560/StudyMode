package com.telusko.studymode.controller;

import com.telusko.studymode.dto.ChatRequest;
import com.telusko.studymode.dto.ChatResponse;
import com.telusko.studymode.dto.ConversationDto;
import com.telusko.studymode.dto.MessageDto;
import com.telusko.studymode.service.TutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tutor")
@CrossOrigin(origins = "*") // Configure properly for production
public class TutorController {

    private static final Logger log = LoggerFactory.getLogger(TutorController.class);
    private final TutorService tutorService;

    public TutorController(TutorService tutorService) {
        this.tutorService = tutorService;
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Tutor Service is running!");
    }

    /**
     * Create a new conversation
     */
    @PostMapping("/start/{userId}")
    public ResponseEntity<ConversationDto> startNewChat(
            @PathVariable String userId,
            @RequestParam String title
    ) {
        log.info("POST /start/{} - title: {}", userId, title);
        ConversationDto conversation = tutorService.createConversation(userId, title);
        return ResponseEntity.ok(conversation);
    }

    /**
     * Send a message in a conversation
     */
    @PostMapping("/ask/{userId}/{conversationId}")
    public ResponseEntity<ChatResponse> askQuestion(
            @PathVariable String userId,
            @PathVariable String conversationId,
            @RequestBody ChatRequest request
    ) {
        log.info("POST /ask/{}/{} - question: {}", userId, conversationId, request.getQuestion());

        ChatResponse response = tutorService.chat(
                userId,
                conversationId,
                request.getQuestion(),
                request.getUserName()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get all conversations for a user
     */
    @GetMapping("/list/{userId}")
    public ResponseEntity<List<ConversationDto>> listChats(@PathVariable String userId) {
        log.info("GET /list/{}", userId);
        List<ConversationDto> conversations = tutorService.getUserConversations(userId);
        return ResponseEntity.ok(conversations);
    }

    /**
     * Get conversation history (messages)
     */
    @GetMapping("/history/{userId}/{conversationId}")
    public ResponseEntity<List<MessageDto>> getHistory(
            @PathVariable String userId,
            @PathVariable String conversationId
    ) {
        log.info("GET /history/{}/{}", userId, conversationId);
        List<MessageDto> messages = tutorService.getConversationHistory(userId, conversationId);
        return ResponseEntity.ok(messages);
    }

    /**
     * Delete a single conversation
     */
    @DeleteMapping("/clear/{userId}/{conversationId}")
    public ResponseEntity<Void> deleteChat(
            @PathVariable String userId,
            @PathVariable String conversationId
    ) {
        log.info("DELETE /clear/{}/{}", userId, conversationId);
        tutorService.deleteConversation(userId, conversationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete all conversations for a user
     */
    @DeleteMapping("/clearAll/{userId}")
    public ResponseEntity<Void> deleteAllChats(@PathVariable String userId) {
        log.info("DELETE /clearAll/{}", userId);
        tutorService.deleteAllConversations(userId);
        return ResponseEntity.noContent().build();
    }
}