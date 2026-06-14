package com.eitan.productivime;

import com.eitan.productivime.BangForYourBuck.BangForYourBuck;
import com.eitan.productivime.BangForYourBuck.ScheduleResult;
import com.eitan.productivime.BangForYourBuck.Activities.*;
import org.springframework.stereotype.Service;

/**
 * Owns the single BangForYourBuck instance for the current day.
 *
 * Because the day's start/end times are required at construction time,
 * the instance is created lazily via initDay() rather than at startup.
 * Call initDay() first — all other methods will throw if the day hasn't
 * been initialised yet.
 */
@Service
public class BangForYourBuckService {

    private BangForYourBuck day;

    // ------------------------------------------------------------------
    // Day lifecycle
    // ------------------------------------------------------------------

    /**
     * Initialises (or resets) the day.
     *
     * @param startTime 24-hr time string, e.g. "8:00"
     * @param endTime   24-hr time string, e.g. "18:00"
     */
    public void initDay(String startTime, String endTime) {
        this.day = new BangForYourBuck(startTime, endTime);
    }

    public boolean isDayInitialised() {
        return day != null;
    }

    // ------------------------------------------------------------------
    // Adding activities
    // ------------------------------------------------------------------

    public void addSetActivity(String name, String startTime, String endTime) {
        requireDay();
        day.addActivity(new SetActivity(name, startTime, endTime), false);
    }

    public void addFlexActivity(String name, String duration, int value) {
        requireDay();
        day.addActivity(new FlexibleActivity(name, duration, value), false);
    }

    public void addFlexActivityDoToday(String name, String duration, int value) {
        requireDay();
        FlexibleActivity activity = new FlexibleActivity(name, duration, value);
        activity.doToday();
        day.addActivity(activity, false);
    }

    public void addDoTodayFlexActivity(String name, String startBy, String endBy, String duration) {
        requireDay();
        day.addActivity(new DoTodayFlexActivity(name, startBy, endBy, duration), false);
    }

    // ------------------------------------------------------------------
    // Schedule generation
    // ------------------------------------------------------------------

    public ScheduleResult generateSchedule() {
        requireDay();
        return day.generateSchedule();
    }

    // ------------------------------------------------------------------
    // Completion tracking
    // ------------------------------------------------------------------

    public void completeActivity(String name) {
        requireDay();
        day.completeActivity(name);
    }

    public boolean isActivityCompleted(String name) {
        requireDay();
        return day.isActivityCompleted(name);
    }

    // ------------------------------------------------------------------
    // Effectiveness
    // ------------------------------------------------------------------

    /**
     * Returns day effectiveness as a percentage string, e.g. "73.5%"
     */
    public String getDayEffectivenessPercent() {
        requireDay();
        double effectiveness = day.getDayEffectiveness();
        return String.format("%.1f%%", effectiveness * 100);
    }

    public double getDayEffectivenessRaw() {
        requireDay();
        return day.getDayEffectiveness();
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private void requireDay() {
        if (day == null) {
            throw new IllegalStateException("Day has not been initialised. POST to /day/start first.");
        }
    }

}
