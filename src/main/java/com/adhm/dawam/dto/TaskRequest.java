package com.adhm.dawam.dto;

import com.adhm.dawam.entity.RecurrenceRule;
import com.adhm.dawam.entity.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class TaskRequest {

    @NotBlank
    private String title;

    @NotNull
    private Task.TaskType type;

    private LocalDate dueDate;

    private RecurrenceRule.Frequency frequency;

    private List<RecurrenceRule.DayOfWeek> daysOfWeek;
}