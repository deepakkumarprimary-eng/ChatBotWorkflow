package com.xpressbees.chatbot.repository;

import com.xpressbees.chatbot.entity.ApiConfig;
import com.xpressbees.chatbot.entity.ApiResponseMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiResponseMappingRepository extends JpaRepository<ApiResponseMapping, Long> {

    void deleteAllByApiConfig(ApiConfig apiConfig);
}
