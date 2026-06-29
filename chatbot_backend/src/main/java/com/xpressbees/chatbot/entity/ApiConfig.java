package com.xpressbees.chatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "api_config")
@EntityListeners(ApiConfigEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "url", nullable = false, length = 1024)
    private String url;

    @Column(name = "method", nullable = false, length = 10)
    private String method;

    @Column(name = "timeout_ms", nullable = false)
    private Integer timeoutMs = 5000;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 1;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "client_id", length = 255)
    private String clientId;

    @OneToMany(mappedBy = "apiConfig", cascade = CascadeType.ALL, orphanRemoval = true , fetch = FetchType.EAGER)
    private List<ApiHeader> headers = new ArrayList<>();

    @OneToOne(mappedBy = "apiConfig", cascade = CascadeType.ALL, orphanRemoval = true , fetch = FetchType.EAGER)
    private ApiPayload payload;

    @OneToMany(mappedBy = "apiConfig", cascade = CascadeType.ALL, orphanRemoval = true , fetch = FetchType.EAGER)
    private List<ApiResponseMapping> responseMappings = new ArrayList<>();

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
