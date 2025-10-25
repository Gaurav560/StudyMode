package com.telusko.studymode.controller;

import com.telusko.studymode.dto.ChatRequest;
import com.telusko.studymode.dto.ChatResponse;
import com.telusko.studymode.service.TutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tutor")
public class TutorController {

    @Autowired
    private TutorService tutorService;

    @PostMapping("/ask")
    public ChatResponse ask(@RequestBody ChatRequest request) {
        String answer = tutorService.generateAnswer(
                request.getQuestion(),
                request.getUserId(),
                request.getUserName()
        );

        int turnCount = tutorService.getStudentTurnCount(request.getUserId());
        int messageCount = tutorService.getConversationLength(request.getUserId());

        return new ChatResponse(answer, turnCount, messageCount);
    }

    @DeleteMapping("/clear/{userId}")
    public String clearConversation(@PathVariable String userId) {
        tutorService.clearConversation(userId);
        return "Conversation cleared for user: " + userId;
    }

    @GetMapping("/health")
    public String health() {
        return "Tutor Service is running!";
    }
}