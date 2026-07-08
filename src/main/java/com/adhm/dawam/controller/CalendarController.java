package com.adhm.dawam.controller;

import com.adhm.dawam.dto.TaskResponse;
import com.adhm.dawam.entity.Task;
import com.adhm.dawam.repository.SectionRepository;
import com.adhm.dawam.repository.TaskCompletionRepository;
import com.adhm.dawam.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final SectionRepository sectionRepository;
    private final TaskRepository taskRepository;
    private final TaskCompletionRepository taskCompletionRepository;

    @GetMapping
    public ResponseEntity<Map<String, List<TaskResponse>>> getCalendar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(defaultValue = "UTC") String timezone) {

        UUID userId = UUID.fromString(jwt.getSubject());

        ZoneId userZone;
        try {
            userZone = ZoneId.of(timezone);
        } catch (Exception e) {
            userZone = ZoneId.of("UTC");
        }

        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);

        List<UUID> sectionIds = sectionRepository.findByUserIdOrderByPosition(userId)
                .stream()
                .map(s -> s.getId())
                .toList();

        List<Task> allTasks = sectionIds.stream()
                .flatMap(sectionId -> taskRepository.findBySectionIdOrderByPosition(sectionId).stream())
                .toList();

        Map<String, List<TaskResponse>> calendar = new LinkedHashMap<>();

        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            final LocalDate day = cursor;
            List<TaskResponse> dayTasks = new ArrayList<>();

            for (Task task : allTasks) {
                boolean isDue = false;

                if (task.getType() == Task.TaskType.ONE_TIME) {
                    if (task.getDueDate() != null) {
                        LocalDate taskLocalDate = task.getDueDate()
                                .atZone(ZoneId.of("UTC"))
                                .withZoneSameInstant(userZone)
                                .toLocalDate();
                        isDue = taskLocalDate.equals(day);
                    }
                } else if (task.getType() == Task.TaskType.RECURRING) {
                    if (task.getRecurrenceRule() != null) {
                        isDue = task.getRecurrenceRule().isDueOn(day);
                    }
                }

                if (isDue) {
                    TaskResponse response = TaskResponse.from(task);

                    if (task.getType() == Task.TaskType.ONE_TIME) {
                        response.setDoneToday(task.isCompleted());
                    } else {
                        boolean doneToday = taskCompletionRepository
                                .findByTaskIdAndCompletedDate(task.getId(), day)
                                .isPresent();
                        response.setDoneToday(doneToday);
                        if (task.getRecurrenceRule() != null) {
                            response.setDueTodayFlag(task.getRecurrenceRule().isDueOn(day));
                        }
                    }

                    dayTasks.add(response);
                }
            }

            calendar.put(day.toString(), dayTasks);
            cursor = cursor.plusDays(1);
        }

        return ResponseEntity.ok(calendar);
    }
}