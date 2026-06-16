package com.xpressbees.chatbot.repository;

import com.xpressbees.chatbot.entity.ApiConfig;
import com.xpressbees.chatbot.entity.ApiHeader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiHeaderRepository extends JpaRepository<ApiHeader, Long> {

    void deleteAllByApiConfig(ApiConfig apiConfig);
}
