package com.xpressbees.chatbot.repository;

import com.xpressbees.chatbot.entity.ApiConfig;
import com.xpressbees.chatbot.entity.ApiPayload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiPayloadRepository extends JpaRepository<ApiPayload, Long> {

    Optional<ApiPayload> findByApiConfig(ApiConfig apiConfig);

    void deleteByApiConfig(ApiConfig apiConfig);
}
