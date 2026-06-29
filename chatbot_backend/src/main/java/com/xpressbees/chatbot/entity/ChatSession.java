package com.xpressbees.chatbot.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "chat_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "current_node_id")
    private String currentNodeId;

    @Column(name = "current_type")
    private String currentType;

    @Column(name = "current_node_type")
    private String currentNodeType;

    @Type(JsonType.class)
    @Column(name = "context", columnDefinition = "jsonb")
    private Map<String, Object> context = new HashMap<>();

    @Column(name = "status", nullable = false)
    private String status;

    @Type(JsonType.class)
    @Column(name = "last_prompt_payload", columnDefinition = "jsonb")
    private Map<String, Object> lastPromptPayload;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
