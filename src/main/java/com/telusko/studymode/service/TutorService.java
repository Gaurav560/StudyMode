package com.telusko.studymode.service;

import com.telusko.studymode.config.PromptTemplate;
import com.telusko.studymode.dto.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TutorService {

    private final ChatClient tutorChatClient;
    private final ChatMemory tutorChatMemory;
    private final PromptTemplate systemPromptTemplate;

    private static final Logger log = LoggerFactory.getLogger(TutorService.class);

    // userId → list of their conversations
    private final Map<String, List<Conversation>> userConversations = new HashMap<>();

    // userId → current active conversationId
    private final Map<String, String> activeConversationMap = new HashMap<>();

    // conversationId -> turn count
    private final Map<String, Integer> conversationTurnCounts = new HashMap<>();

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

    /**
     * Create a new conversation
     */
    public Conversation startNewConversation(String userId, String title) {
        Conversation convo = new Conversation(userId, title);
        userConversations.computeIfAbsent(userId, k -> new ArrayList<>()).add(convo);
        activeConversationMap.put(userId, convo.getConversationId());
        conversationTurnCounts.put(convo.getConversationId(), 0);
        log.info("New conversation started: {} for user {}", convo.getConversationId(), userId);
        return convo;
    }

    /**
     * Switch active conversation for the user
     */
    public void switchConversation(String userId, String conversationId) {
        // ensure conversation exists for user (optional)
        List<Conversation> convos = userConversations.get(userId);
        if (convos != null) {
            boolean found = convos.stream().anyMatch(c -> c.getConversationId().equals(conversationId));
            if (!found) {
                log.warn("switchConversation: conversation {} not found for user {}", conversationId, userId);
            }
        }
        activeConversationMap.put(userId, conversationId);
        conversationTurnCounts.putIfAbsent(conversationId, 0);
        log.info("Switched to conversation {} for user {}", conversationId, userId);
    }

    /**
     * Generate answer for a specific conversation (explicit conversationId)
     */
    public String generateAnswer(String question, String userId, String userName, String conversationId) {
        // If a conversationId is provided, use it and set active; otherwise fallback to existing active or create new
        String convoId = conversationId;
        if (convoId == null || convoId.isEmpty()) {
            convoId = activeConversationMap.get(userId);
            if (convoId == null) {
                convoId = startNewConversation(userId, "Untitled Chat").getConversationId();
            }
        } else {
            // ensure this conversation is active for user
            activeConversationMap.put(userId, convoId);
            conversationTurnCounts.putIfAbsent(convoId, 0);
        }

        log.info("[{}] User: {}, Q: {}", convoId, userName, question);

        try {
            Map<String, Object> vars = Map.of("userName", userName);
            String systemPrompt = systemPromptTemplate.format(vars);

            String finalConversationId = convoId;
            String response = tutorChatClient.prompt()
                    .system(systemPrompt)
                    .user(question)
                    .options(ChatOptions.builder().temperature(0.7).build())
                    .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, finalConversationId))
                    .call()
                    .content();

            // increment turn count for this conversation
            conversationTurnCounts.merge(convoId, 1, Integer::sum);

            return response;
        } catch (Exception e) {
            log.error("Error generating response", e);
            return "I encountered a technical issue. Could you rephrase that?";
        }
    }

    /**
     * Get all conversations for a user
     */
    public List<Conversation> getUserConversations(String userId) {
        return userConversations.getOrDefault(userId, Collections.emptyList());
    }

    /**
     * Delete one conversation
     */
    public void clearConversation(String userId, String conversationId) {
        tutorChatMemory.clear(conversationId);
        List<Conversation> convos = userConversations.get(userId);
        if (convos != null) convos.removeIf(c -> c.getConversationId().equals(conversationId));
        activeConversationMap.values().removeIf(id -> id.equals(conversationId));
        conversationTurnCounts.remove(conversationId);
        log.info("Cleared conversation {} for user {}", conversationId, userId);
    }

    /**
     * Clear all conversations for a user
     */
    public void clearAllConversations(String userId) {
        List<Conversation> convos = userConversations.remove(userId);
        if (convos != null) convos.forEach(c -> {
            tutorChatMemory.clear(c.getConversationId());
            conversationTurnCounts.remove(c.getConversationId());
        });
        activeConversationMap.remove(userId);
        log.info("Cleared ALL conversations for user {}", userId);
    }

    /**
     * Return number of messages stored for a conversation (safe)
     */
    public int getConversationLength(String userId, String conversationId) {
        if (conversationId == null) return 0;
        try {
            List<?> mem = tutorChatMemory.get(conversationId);
            return mem != null ? mem.size() : 0;
        } catch (Exception e) {
            log.warn("getConversationLength: unable to read memory for {}", conversationId, e);
            return 0;
        }
    }

    /**
     * Get turn count for a conversation
     */
    public int getStudentTurnCount(String userId, String conversationId) {
        if (conversationId == null) return 0;
        return conversationTurnCounts.getOrDefault(conversationId, 0);
    }
}
