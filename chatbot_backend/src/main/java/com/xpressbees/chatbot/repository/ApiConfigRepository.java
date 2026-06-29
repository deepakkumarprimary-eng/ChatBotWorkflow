package com.xpressbees.chatbot.repository;

import com.xpressbees.chatbot.entity.ApiConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiConfigRepository extends JpaRepository<ApiConfig, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Optional<ApiConfig> findByName(String name);
}
