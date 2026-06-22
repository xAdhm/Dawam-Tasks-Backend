package com.adhm.dawam.dto;

import com.adhm.dawam.entity.RecurrenceRule;
import com.adhm.dawam.entity.Task;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class TaskResponse {

    private UUID id;
    private UUID sectionId;
    private String title;
    private Task.TaskType type;
    private LocalDate dueDate;
    private boolean completed;
    private RecurrenceRule.Frequency frequency;
    private List<RecurrenceRule.DayOfWeek> daysOfWeek;
    private boolean doneToday;
    private boolean dueTodayFlag;

    public static TaskResponse from(Task task) {
        TaskResponse response = new TaskResponse();
        response.id = task.getId();
        response.sectionId = task.getSection().getId();
        response.title = task.getTitle();
        response.type = task.getType();
        response.dueDate = task.getDueDate();
        response.completed = task.isCompleted();

        if (task.getRecurrenceRule() != null) {
            response.frequency = task.getRecurrenceRule().getFrequency();
            response.daysOfWeek = task.getRecurrenceRule().getDaysOfWeek();
        }

        return response;
    }
}