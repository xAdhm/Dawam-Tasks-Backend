package com.adhm.dawam.controller;

import com.adhm.dawam.dto.TaskRequest;
import com.adhm.dawam.dto.TaskResponse;
import com.adhm.dawam.entity.RecurrenceRule;
import com.adhm.dawam.entity.Task;
import com.adhm.dawam.repository.SectionRepository;
import com.adhm.dawam.repository.TaskRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sections/{sectionId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskRepository taskRepository;
    private final SectionRepository sectionRepository;

    @GetMapping
    public ResponseEntity<List<TaskResponse>> listTasks(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID sectionId) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return sectionRepository.findById(sectionId)
                .filter(section -> section.getUserId().equals(userId))
                .map(section -> {
                    List<TaskResponse> tasks = taskRepository.findBySectionId(sectionId)
                            .stream()
                            .map(TaskResponse::from)
                            .toList();
                    return ResponseEntity.ok(tasks);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID sectionId,
                                                   @Valid @RequestBody TaskRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return sectionRepository.findById(sectionId)
                .filter(section -> section.getUserId().equals(userId))
                .map(section -> {
                    Task task = new Task();
                    task.setSection(section);
                    task.setTitle(request.getTitle());
                    task.setType(request.getType());
                    task.setCompleted(false);

                    applyTypeSpecificFields(task, request);

                    Task saved = taskRepository.save(task);
                    return ResponseEntity.ok(TaskResponse.from(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<TaskResponse> updateTask(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID sectionId,
                                                   @PathVariable UUID taskId,
                                                   @Valid @RequestBody TaskRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return taskRepository.findById(taskId)
                .filter(task -> task.getSection().getId().equals(sectionId))
                .filter(task -> task.getSection().getUserId().equals(userId))
                .map(task -> {
                    task.setTitle(request.getTitle());
                    task.setType(request.getType());

                    applyTypeSpecificFields(task, request);

                    Task saved = taskRepository.save(task);
                    return ResponseEntity.ok(TaskResponse.from(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID sectionId,
                                           @PathVariable UUID taskId) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return taskRepository.findById(taskId)
                .filter(task -> task.getSection().getId().equals(sectionId))
                .filter(task -> task.getSection().getUserId().equals(userId))
                .map(task -> {
                    taskRepository.delete(task);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void applyTypeSpecificFields(Task task, TaskRequest request) {
        if (request.getType() == Task.TaskType.RECURRING) {
            task.setDueDate(null);

            RecurrenceRule rule = task.getRecurrenceRule();
            if (rule == null) {
                rule = new RecurrenceRule();
                rule.setTask(task);
                task.setRecurrenceRule(rule);
            }
            rule.setFrequency(request.getFrequency());
            rule.setDaysOfWeek(request.getDaysOfWeek());

        } else {
            task.setDueDate(request.getDueDate());
            task.setRecurrenceRule(null);
        }
    }
}