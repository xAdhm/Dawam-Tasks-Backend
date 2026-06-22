package com.adhm.dawam.repository;

import com.adhm.dawam.entity.RecurrenceRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {
}