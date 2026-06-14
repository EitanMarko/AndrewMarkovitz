package com.eitan.productivime;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for ProductiviMe.
 *
 * Typical flow:
 *   1. POST /day/start              — define the day's hours
 *   2. POST /activities/set         — add fixed-time activities
 *      POST /activities/flexible    — add flexible activities
 *      POST /activities/dotoday     — add must-do flexible activities
 *   3. POST /activities/{name}/complete — mark activities done as the day progresses
 *   4. GET  /day/effectiveness      — see how effective the day was
 */
@RestController
public class BangForYourBuckController {

    private final BangForYourBuckService service;

    public BangForYourBuckController(BangForYourBuckService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------
    // Request bodies (static inner classes — no separate files needed)
    // ------------------------------------------------------------------

    record StartDayRequest(String startTime, String endTime) {}

    record SetActivityRequest(String name, String startTime, String endTime) {}

    record FlexActivityRequest(String name, String duration, int value, boolean doToday) {}

    record DoTodayFlexActivityRequest(String name, String startBy, String endBy, String duration) {}

    record EffectivenessResponse(String percentage, double raw) {}

    // ------------------------------------------------------------------
    // Day lifecycle
    // ------------------------------------------------------------------

    /**
     * Initialise (or reset) the day.
     *
     * POST /day/start
     * Body: { "startTime": "8:00", "endTime": "18:00" }
     */
    @PostMapping("/day/start")
    public ResponseEntity<String> startDay(@RequestBody StartDayRequest request) {
        service.initDay(request.startTime(), request.endTime());
        return ResponseEntity.ok(
            "Day started: " + request.startTime() + " → " + request.endTime()
        );
    }

    // ------------------------------------------------------------------
    // Adding activities
    // ------------------------------------------------------------------

    /**
     * Add a fixed-time activity.
     *
     * POST /activities/set
     * Body: { "name": "Team Standup", "startTime": "9:00", "endTime": "9:30" }
     */
    @PostMapping("/activities/set")
    public ResponseEntity<String> addSetActivity(@RequestBody SetActivityRequest request) {
        service.addSetActivity(request.name(), request.startTime(), request.endTime());
        return ResponseEntity.ok("Set activity added: " + request.name());
    }

    /**
     * Add a flexible activity.
     * Set "doToday" to true to mark it as a must-complete for the day.
     *
     * POST /activities/flexible
     * Body: { "name": "Read", "duration": "0:30", "value": 7, "doToday": false }
     */
    @PostMapping("/activities/flexible")
    public ResponseEntity<String> addFlexActivity(@RequestBody FlexActivityRequest request) {
        if (request.doToday()) {
            service.addFlexActivityDoToday(request.name(), request.duration(), request.value());
            return ResponseEntity.ok("Do-today flex activity added: " + request.name());
        }
        service.addFlexActivity(request.name(), request.duration(), request.value());
        return ResponseEntity.ok("Flexible activity added: " + request.name());
    }

    /**
     * Add a do-today flexible activity with a specific time window it must occur within.
     *
     * POST /activities/dotoday
     * Body: { "name": "Call Mom", "startBy": "10:00", "endBy": "13:00", "duration": "0:20" }
     */
    @PostMapping("/activities/dotoday")
    public ResponseEntity<String> addDoTodayActivity(@RequestBody DoTodayFlexActivityRequest request) {
        service.addDoTodayFlexActivity(
            request.name(), request.startBy(), request.endBy(), request.duration()
        );
        return ResponseEntity.ok("Do-today activity added: " + request.name());
    }

    // ------------------------------------------------------------------
    // Completion tracking
    // ------------------------------------------------------------------

    /**
     * Mark an activity as completed.
     *
     * POST /activities/{name}/complete
     */
    @PostMapping("/activities/{name}/complete")
    public ResponseEntity<String> completeActivity(@PathVariable String name) {
        service.completeActivity(name);
        return ResponseEntity.ok("✓ \"" + name + "\" marked as complete.");
    }

    /**
     * Check whether an activity has been completed.
     *
     * GET /activities/{name}/status
     */
    @GetMapping("/activities/{name}/status")
    public ResponseEntity<String> getActivityStatus(@PathVariable String name) {
        boolean completed = service.isActivityCompleted(name);
        String status = completed ? "✓ Complete" : "✗ Not yet complete";
        return ResponseEntity.ok("\"" + name + "\": " + status);
    }

    // ------------------------------------------------------------------
    // Effectiveness
    // ------------------------------------------------------------------

    /**
     * Get the day's effectiveness score.
     *
     * GET /day/effectiveness
     * Response: { "percentage": "73.5%", "raw": 0.735 }
     */
    @GetMapping("/day/effectiveness")
    public ResponseEntity<EffectivenessResponse> getDayEffectiveness() {
        return ResponseEntity.ok(new EffectivenessResponse(
            service.getDayEffectivenessPercent(),
            service.getDayEffectivenessRaw()
        ));
    }

    // ------------------------------------------------------------------
    // Error handling
    // ------------------------------------------------------------------

    /**
     * Converts IllegalArgumentException (bad input) and
     * IllegalStateException (day not initialised) into readable 400 responses
     * rather than a raw 500.
     */
    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    public ResponseEntity<String> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body("Error: " + ex.getMessage());
    }

}
