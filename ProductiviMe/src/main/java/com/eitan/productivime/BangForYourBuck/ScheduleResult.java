package com.eitan.productivime.BangForYourBuck;

import java.util.List;

// JSON response shape for POST /schedule/generate.
// Spring Boot / Jackson serialises this automatically.
public record ScheduleResult(
        List<ScheduledInterval> intervals,
        int totalValue
) {
    public record ScheduledInterval(
            String start,
            String end,
            int durationMinutes,
            List<ScheduledActivity> activities
    ) {}

    public record ScheduledActivity(
            String name,
            int durationMinutes,
            int value
    ) {}
}
