package com.telusko.studymode.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String question;
    private String userId;
    private String userName;
    private String conversationId;  // new field (optional if backend uses active map)
}
