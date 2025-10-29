package com.telusko.studymode.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDto {
    private String conversationId;
    private String userId;
    private String title;
    private Instant createdAt;
    private Instant updatedAt;
}