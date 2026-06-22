package com.adhm.dawam.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recurrence_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecurrenceRule {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recurrence_rule_days", joinColumns = @JoinColumn(name = "recurrence_rule_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private List<DayOfWeek> daysOfWeek;

    public enum Frequency {
        DAILY,
        SPECIFIC_DAYS
    }

    public enum DayOfWeek {
        MON, TUE, WED, THU, FRI, SAT, SUN
    }

    public boolean isDueOn(java.time.LocalDate date) {
        if (frequency == Frequency.DAILY) {
            return true;
        }

        if (frequency == Frequency.SPECIFIC_DAYS) {
            DayOfWeek today = mapJavaDayOfWeek(date.getDayOfWeek());
            return daysOfWeek != null && daysOfWeek.contains(today);
        }

        return false;
    }

    private DayOfWeek mapJavaDayOfWeek(java.time.DayOfWeek javaDay) {
        return switch (javaDay) {
            case MONDAY -> DayOfWeek.MON;
            case TUESDAY -> DayOfWeek.TUE;
            case WEDNESDAY -> DayOfWeek.WED;
            case THURSDAY -> DayOfWeek.THU;
            case FRIDAY -> DayOfWeek.FRI;
            case SATURDAY -> DayOfWeek.SAT;
            case SUNDAY -> DayOfWeek.SUN;
        };
    }
}