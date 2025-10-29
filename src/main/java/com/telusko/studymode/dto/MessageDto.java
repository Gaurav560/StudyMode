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
public class MessageDto {
    private String role; // "user" or "assistant"
    private String content;
    private Instant timestamp;
}