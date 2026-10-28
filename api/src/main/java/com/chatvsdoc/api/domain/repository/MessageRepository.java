package com.chatvsdoc.api.domain.repository;

import com.chatvsdoc.api.domain.entity.Message;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {}
