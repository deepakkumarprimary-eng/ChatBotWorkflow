package com.xpressbees.chatbot.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "api_header")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_id", nullable = false)
    private ApiConfig apiConfig;

    @Column(name = "header_name", nullable = false, length = 255)
    private String headerName;

    @Column(name = "header_value", nullable = false, length = 1024)
    private String headerValue;
}
