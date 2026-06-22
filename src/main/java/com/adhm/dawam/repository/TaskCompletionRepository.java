package com.adhm.dawam.repository;

import com.adhm.dawam.entity.TaskCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TaskCompletionRepository extends JpaRepository<TaskCompletion, UUID> {

    Optional<TaskCompletion> findByTaskIdAndCompletedDate(UUID taskId, LocalDate completedDate);

    void deleteByTaskIdAndCompletedDate(UUID taskId, LocalDate completedDate);
}