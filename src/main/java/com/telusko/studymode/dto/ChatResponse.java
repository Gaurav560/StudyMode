package com.telusko.studymode.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatResponse {
    private String answer;
    private int turnCount;
     private int messageCount;
}
