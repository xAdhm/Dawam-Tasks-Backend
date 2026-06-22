package com.adhm.dawam.repository;

import com.adhm.dawam.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findBySectionId(UUID sectionId);
}