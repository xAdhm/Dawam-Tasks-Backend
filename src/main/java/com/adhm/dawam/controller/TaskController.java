package com.adhm.dawam.controller;

import com.adhm.dawam.dto.TaskRequest;
import com.adhm.dawam.dto.TaskResponse;
import com.adhm.dawam.entity.RecurrenceRule;
import com.adhm.dawam.entity.Task;
import com.adhm.dawam.entity.TaskCompletion;
import com.adhm.dawam.repository.SectionRepository;
import com.adhm.dawam.repository.TaskCompletionRepository;
import com.adhm.dawam.repository.TaskRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sections/{sectionId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskRepository taskRepository;
    private final SectionRepository sectionRepository;
    private final TaskCompletionRepository taskCompletionRepository;

    @GetMapping
    public ResponseEntity<List<TaskResponse>> listTasks(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID sectionId) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return sectionRepository.findById(sectionId)
                .filter(section -> section.getUserId().equals(userId))
                .map(section -> {
                    List<TaskResponse> tasks = taskRepository.findBySectionId(sectionId)
                            .stream()
                            .map(this::toResponseWithDoneToday)
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
                    return ResponseEntity.ok(toResponseWithDoneToday(saved));
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
                    return ResponseEntity.ok(toResponseWithDoneToday(saved));
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

    @Transactional
    @PutMapping("/{taskId}/toggle")
    public ResponseEntity<TaskResponse> toggleCompletion(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID sectionId,
                                                         @PathVariable UUID taskId) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return taskRepository.findById(taskId)
                .filter(task -> task.getSection().getId().equals(sectionId))
                .filter(task -> task.getSection().getUserId().equals(userId))
                .map(task -> {
                    if (task.getType() == Task.TaskType.ONE_TIME) {
                        task.setCompleted(!task.isCompleted());
                        taskRepository.save(task);
                    } else {
                        LocalDate today = LocalDate.now();
                        var existing = taskCompletionRepository.findByTaskIdAndCompletedDate(taskId, today);

                        if (existing.isPresent()) {
                            taskCompletionRepository.deleteByTaskIdAndCompletedDate(taskId, today);
                        } else {
                            TaskCompletion completion = new TaskCompletion();
                            completion.setTask(task);
                            completion.setCompletedDate(today);
                            taskCompletionRepository.save(completion);
                        }
                    }

                    return ResponseEntity.ok(toResponseWithDoneToday(task));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private TaskResponse toResponseWithDoneToday(Task task) {
        TaskResponse response = TaskResponse.from(task);

        if (task.getType() == Task.TaskType.ONE_TIME) {
            response.setDoneToday(task.isCompleted());
        } else {
            LocalDate today = LocalDate.now();
            boolean doneToday = taskCompletionRepository
                    .findByTaskIdAndCompletedDate(task.getId(), today)
                    .isPresent();
            response.setDoneToday(doneToday);

            if (task.getRecurrenceRule() != null) {
                response.setDueTodayFlag(task.getRecurrenceRule().isDueOn(today));
            }
        }

        return response;
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