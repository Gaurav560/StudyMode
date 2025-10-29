package com.telusko.studymode.dto;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class Conversation {
    private final String conversationId;
    private final String userId;
    private final String title;
    private final LocalDateTime createdAt;

    public Conversation(String userId, String title) {
        this.conversationId = UUID.randomUUID().toString();
        this.userId = userId;
        this.title = title;
        this.createdAt = LocalDateTime.now();
    }
}
