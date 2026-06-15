package com.xpressbees.chatbot.repository;

import com.xpressbees.chatbot.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
}
