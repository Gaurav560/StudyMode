package com.telusko.studymode.service;


import com.telusko.studymode.config.PromptTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TutorService {

    private final ChatClient tutorChatClient;
    private final ChatMemory tutorChatMemory;
    private final PromptTemplate systemPromptTemplate;

    private static final Logger log = LoggerFactory.getLogger(TutorService.class);

    // Simple student tracking
    private final Map<String, StudentContext> studentContexts = new ConcurrentHashMap<>();

    @Autowired
    public TutorService(
            @Qualifier("tutorChatClient") ChatClient tutorChatClient,
            @Qualifier("tutorChatMemory") ChatMemory tutorChatMemory,
            @Qualifier("tutorSystemPrompt") PromptTemplate systemPromptTemplate
    ) {
        this.tutorChatClient = tutorChatClient;
        this.tutorChatMemory = tutorChatMemory;
        this.systemPromptTemplate = systemPromptTemplate;
    }

    public String generateAnswer(String question, String userId, String userName) {
        log.info("📚 TUTOR - User: {}, Q: {}", userName, question);

        try {
            // 1. Get or create student context
            StudentContext context = studentContexts.computeIfAbsent(
                    userId,
                    k -> new StudentContext(userId, userName)
            );

            // 2. Generate conversation ID
            String conversationId = generateConversationId(userId);

            // 3. Build system prompt
            Map<String, Object> promptVars = new HashMap<>();
            promptVars.put("userName", userName);
            String systemPrompt = systemPromptTemplate.format(promptVars);

            // 4. Generate response
            String response = tutorChatClient
                    .prompt()
                    .system(systemPrompt)
                    .user(question)
                    .options(ChatOptions.builder()
                            .temperature(0.7)
                            .build())
                    .advisors(advisorSpec -> advisorSpec
                            .param(ChatMemory.CONVERSATION_ID, conversationId)
                    )
                    .call()
                    .content();

            // 5. Update tracking
            context.incrementTurnCount();

            log.info("✅ Response generated | Turn: {}", context.getTurnCount());

            return response;

        } catch (Exception e) {
            log.error("❌ Error generating response", e);
            return "I encountered a technical issue. Could you rephrase your question?";
        }
    }

    public void clearConversation(String userId) {
        String conversationId = generateConversationId(userId);
        tutorChatMemory.clear(conversationId);
        studentContexts.remove(userId);
        log.info("🗑️ Cleared conversation for user: {}", userId);
    }

    public int getConversationLength(String userId) {
        String conversationId = generateConversationId(userId);
        return tutorChatMemory.get(conversationId).size();
    }

    public int getStudentTurnCount(String userId) {
        StudentContext context = studentContexts.get(userId);
        return context != null ? context.getTurnCount() : 0;
    }

    private String generateConversationId(String userId) {
        return UUID.nameUUIDFromBytes(userId.getBytes()).toString();
    }

    // Simple context tracking
    private static class StudentContext {
        private final String userId;
        private final String userName;
        private int turnCount = 0;

        public StudentContext(String userId, String userName) {
            this.userId = userId;
            this.userName = userName;
        }

        public void incrementTurnCount() {
            this.turnCount++;
        }

        public int getTurnCount() {
            return turnCount;
        }
    }
}