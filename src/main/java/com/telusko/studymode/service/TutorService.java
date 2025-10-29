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
     * Generate AI response - fully server-side with Spring AI memory
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

        // 3. Prepare system prompt with user context
        String systemPrompt = systemPromptTemplate.format(Map.of(
                "userName", userName != null ? userName : "Student"
        ));

        try {
            // 4. Call AI with Spring AI memory - it automatically manages conversation history
            String response = tutorChatClient.prompt()
                    .system(systemPrompt)
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
                .timestamp(Instant.now()) // Spring AI doesn't store timestamps, use current time
                .build();
    }
}