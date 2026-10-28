package com.chatvsdoc.api.domain.repository;

import com.chatvsdoc.api.domain.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {}
