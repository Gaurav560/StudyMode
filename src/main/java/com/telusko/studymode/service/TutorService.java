package com.telusko.studymode.service;

import com.telusko.studymode.config.PromptTemplate;
import com.telusko.studymode.dto.ChatResponse;
import com.telusko.studymode.dto.ConversationDto;
import com.telusko.studymode.dto.MessageDto;
import com.telusko.studymode.entity.ConversationEntity;
import com.telusko.studymode.repo.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TutorService {

    private static final Logger log = LoggerFactory.getLogger(TutorService.class);

    private final ChatClient tutorChatClient;
    private final ChatMemory tutorChatMemory;
    private final PromptTemplate systemPromptTemplate;
    private final ConversationRepository conversationRepository;

    public TutorService(
            @Qualifier("tutorChatClient") ChatClient tutorChatClient,
            @Qualifier("tutorChatMemory") ChatMemory tutorChatMemory,
            @Qualifier("tutorSystemPrompt") PromptTemplate systemPromptTemplate,
            ConversationRepository conversationRepository
    ) {
        this.tutorChatClient = tutorChatClient;
        this.tutorChatMemory = tutorChatMemory;
        this.systemPromptTemplate = systemPromptTemplate;
        this.conversationRepository = conversationRepository;
    }

    /**
     * Create a new conversation - persisted to database
     */
    @Transactional
    public ConversationDto createConversation(String userId, String title) {
        log.info("Creating new conversation for user: {}", userId);

        ConversationEntity entity = ConversationEntity.builder()
                .conversationId(UUID.randomUUID().toString())
                .userId(userId)
                .title(title != null && !title.trim().isEmpty() ? title : "New Chat")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ConversationEntity saved = conversationRepository.save(entity);

        log.info("Created conversation: {} for user: {}", saved.getConversationId(), userId);

        return mapToDto(saved);
    }

    /**
     * Generate AI response with context-aware prompt enhancement
     */
    @Transactional
    public ChatResponse chat(String userId, String conversationId, String question, String userName) {
        log.info("Chat request - User: {}, Conversation: {}, Question: {}", userId, conversationId, question);

        // 1. Verify user owns this conversation
        ConversationEntity conversation = conversationRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> {
                    log.error("Conversation {} not found or access denied for user {}", conversationId, userId);
                    return new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation not found or access denied");
                });

        // 2. Update conversation timestamp
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        // 3. Get conversation history for context injection
        List<Message> conversationHistory = tutorChatMemory.get(conversationId);

        // 4. Build context-aware system prompt
        String enhancedSystemPrompt = buildContextAwarePrompt(userName, conversationHistory, question);

        try {
            // 5. Call AI with enhanced system prompt
            String response = tutorChatClient.prompt()
                    .system(enhancedSystemPrompt)
                    .user(question)
                    .options(ChatOptions.builder().temperature(0.7).build())
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();

            log.info("AI response generated for conversation: {}", conversationId);

            return ChatResponse.builder()
                    .answer(response)
                    .conversationId(conversationId)
                    .build();

        } catch (Exception e) {
            log.error("Error generating AI response for conversation: {}", conversationId, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate response: " + e.getMessage()
            );
        }
    }

    /**
     * Build universal context-aware prompt that works for ANY conversation pattern
     * This method doesn't hardcode scenarios - it provides general interpretation guidance
     */
    private String buildContextAwarePrompt(String userName, List<Message> history, String currentQuestion) {
        StringBuilder prompt = new StringBuilder();

        // Base system prompt
        prompt.append(systemPromptTemplate.format(Map.of("userName", userName)));
        prompt.append("\n\n");

        // Add explicit conversation history if exists
        if (!history.isEmpty()) {
            prompt.append("========================================\n");
            prompt.append("📝 CONVERSATION HISTORY (CRITICAL - READ THIS FIRST):\n");
            prompt.append("========================================\n\n");

            // Show last 10 messages (to keep prompt size manageable)
            int startIndex = Math.max(0, history.size() - 10);
            List<Message> recentHistory = history.subList(startIndex, history.size());

            for (Message msg : recentHistory) {
                if (msg instanceof UserMessage) {
                    prompt.append("USER: ").append(msg.getText()).append("\n");
                } else if (msg instanceof AssistantMessage) {
                    prompt.append("YOU: ").append(msg.getText()).append("\n");
                }
            }

            prompt.append("\n👉 USER'S CURRENT MESSAGE: \"").append(currentQuestion).append("\"\n");
            prompt.append("\n========================================\n");

            // Universal interpretation guidance
            String lastAssistantMessage = getLastAssistantMessage(history);

            prompt.append("\n⚠️ UNIVERSAL INTERPRETATION RULES:\n");
            prompt.append("========================================\n\n");

            if (lastAssistantMessage != null) {
                // Check if your last message was a question
                boolean askedQuestion = lastAssistantMessage.contains("?");

                if (askedQuestion) {
                    prompt.append("✅ YOUR LAST MESSAGE ASKED A QUESTION:\n");
                    prompt.append("   \"").append(truncateMessage(lastAssistantMessage)).append("\"\n\n");
                    prompt.append("🎯 THEREFORE: The user's current message \"").append(currentQuestion).append("\" is ANSWERING your question.\n\n");
                    prompt.append("📌 INTERPRETATION GUIDELINES:\n");
                    prompt.append("   • Short answers like 'yes', 'no', 'ok', 'done' are answers to YOUR question\n");
                    prompt.append("   • Do NOT interpret them as 'I want to stop' or 'I'm not interested'\n");
                    prompt.append("   • Analyze what question you asked, then interpret the answer in that context\n");
                    prompt.append("   • If the answer doesn't make sense, ask for clarification politely\n\n");
                } else {
                    prompt.append("✅ YOUR LAST MESSAGE WAS A STATEMENT (not a question)\n");
                    prompt.append("   \"").append(truncateMessage(lastAssistantMessage)).append("\"\n\n");
                    prompt.append("🎯 THEREFORE: The user is either:\n");
                    prompt.append("   • Responding to your statement\n");
                    prompt.append("   • Asking a follow-up question\n");
                    prompt.append("   • Requesting clarification\n");
                    prompt.append("   • Expressing understanding ('ok', 'got it', etc.)\n\n");
                }
            }

            prompt.append("❌ PHRASES THAT MEAN USER WANTS TO STOP (very rare):\n");
            prompt.append("   • 'stop', 'quit', 'cancel'\n");
            prompt.append("   • 'I don't want to', 'I'm not interested'\n");
            prompt.append("   • 'maybe later', 'not now'\n\n");

            prompt.append("✅ COMMON CONTINUATION PATTERNS:\n");
            prompt.append("   • 'yes/yeah/yup/sure/ok' after a question = affirmative\n");
            prompt.append("   • 'no/nope/nah' after a question = negative\n");
            prompt.append("   • 'done/finished/installed' = completed the task\n");
            prompt.append("   • 'next/continue' = ready to proceed\n");
            prompt.append("   • Short responses are usually answers to your question\n\n");

            prompt.append("🧠 SMART INTERPRETATION:\n");
            prompt.append("   1. Review what YOU asked/said last\n");
            prompt.append("   2. Interpret user's response in THAT context\n");
            prompt.append("   3. If ambiguous, assume they want to continue (most common)\n");
            prompt.append("   4. Continue the natural flow of the conversation\n");
            prompt.append("   5. Only assume they want to stop if they explicitly say so\n\n");

            prompt.append("========================================\n\n");
        }

        return prompt.toString();
    }

    /**
     * Get the last assistant message from conversation history
     */
    private String getLastAssistantMessage(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Message msg = history.get(i);
            if (msg instanceof AssistantMessage) {
                return msg.getText();
            }
        }
        return null;
    }

    /**
     * Truncate long messages for readability in prompt
     */
    private String truncateMessage(String message) {
        if (message.length() <= 150) {
            return message;
        }
        return message.substring(0, 147) + "...";
    }

    /**
     * Get all conversations for a user - from database
     */
    public List<ConversationDto> getUserConversations(String userId) {
        log.info("Fetching conversations for user: {}", userId);

        List<ConversationEntity> entities = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);

        return entities.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get conversation history (messages) - from Spring AI memory
     */
    public List<MessageDto> getConversationHistory(String userId, String conversationId) {
        log.info("Fetching history for conversation: {} (user: {})", conversationId, userId);

        // Verify ownership
        conversationRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> {
                    log.error("Conversation {} not found or access denied for user {}", conversationId, userId);
                    return new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation not found or access denied");
                });

        try {
            // Get messages from Spring AI chat memory (stored in PostgreSQL)
            List<Message> messages = tutorChatMemory.get(conversationId);

            return messages.stream()
                    .map(this::mapMessageToDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error fetching conversation history: {}", conversationId, e);
            return List.of();
        }
    }

    /**
     * Delete a conversation and its messages
     */
    @Transactional
    public void deleteConversation(String userId, String conversationId) {
        log.info("Deleting conversation: {} for user: {}", conversationId, userId);

        // Verify ownership
        conversationRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> {
                    log.error("Conversation {} not found or access denied for user {}", conversationId, userId);
                    return new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation not found or access denied");
                });

        // Delete messages from Spring AI memory
        tutorChatMemory.clear(conversationId);

        // Delete conversation metadata
        conversationRepository.deleteByConversationIdAndUserId(conversationId, userId);

        log.info("Deleted conversation: {} for user: {}", conversationId, userId);
    }

    /**
     * Delete all conversations for a user
     */
    @Transactional
    public void deleteAllConversations(String userId) {
        log.info("Deleting all conversations for user: {}", userId);

        List<ConversationEntity> conversations = conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);

        // Clear all chat memories
        conversations.forEach(conv -> {
            try {
                tutorChatMemory.clear(conv.getConversationId());
            } catch (Exception e) {
                log.warn("Failed to clear memory for conversation: {}", conv.getConversationId(), e);
            }
        });

        // Delete all conversation records
        conversationRepository.deleteByUserId(userId);

        log.info("Deleted all {} conversations for user: {}", conversations.size(), userId);
    }

    // ==================== Helper Methods ====================

    private ConversationDto mapToDto(ConversationEntity entity) {
        return ConversationDto.builder()
                .conversationId(entity.getConversationId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private MessageDto mapMessageToDto(Message message) {
        String role;
        if (message instanceof UserMessage) {
            role = "user";
        } else if (message instanceof AssistantMessage) {
            role = "assistant";
        } else {
            role = "system";
        }

        return MessageDto.builder()
                .role(role)
                .content(message.getText())
                .timestamp(Instant.now())
                .build();
    }
}