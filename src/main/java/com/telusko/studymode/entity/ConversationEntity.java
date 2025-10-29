package com.telusko.studymode.entity;



import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationEntity {

    @Id
    @Column(name = "conversation_id", length = 255)
    private String conversationId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}