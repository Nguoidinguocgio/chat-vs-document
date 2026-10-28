package com.chatvsdoc.api.domain.repository;

import com.chatvsdoc.api.domain.entity.Conversation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {}
