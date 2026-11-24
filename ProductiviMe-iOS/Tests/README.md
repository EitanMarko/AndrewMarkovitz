# Regression checks

From the project root, run:

```sh
bash Tests/run-tests.sh
```

The runner uses Xcode's Swift compiler and a temporary build directory. It tests the same engine and view-model files used by the app, with an isolated in-memory preferences store and a controllable calendar/clock. It does not change the app's saved activities.

Coverage includes persistence round trips, alphabetical and custom ordering, daily selection and completion state, recurrence and daylight saving time, future occurrence editing, per-date hours, migration from the original daily state, month grid layouts, permanent deletion, fixed-time conflicts, required activity packing, progress history and percentage calculations, and differential checks against exhaustive schedule optimization.

For an iOS build:

```sh
xcodebuild -project ProductiviMe.xcodeproj -scheme ProductiviMe \
  -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build
```

Manual UI checks:

1. Launch on Home, switch to Schedule, and add a one-time activity. Confirm its schedule entry and saved Activities row.
2. Mark a flexible activity required. Confirm the priority control and rating disappear.
3. Choose Repeat and select individual weekdays, every day, or a preset. Reopen the editor to verify the selection.
4. Open Home → Activities, then use Edit to drag rows, relaunch, then restore alphabetical order from Sort.
5. Complete or skip an activity, relaunch, and verify today's state persists. Verify recurring activities start fresh on their next day.
6. Delete an activity from Activities and verify it disappears from the schedule and remains deleted after relaunch.
7. Create conflicting fixed times or more required work than the day can hold. Confirm the conflict is visible and editing or skipping an activity regenerates the schedule.

8. In Calendar, move between months and jump to a distant date. Verify Today returns the calendar to the current month. Verify day numbers, weekday headings, activity dots, and the selected-day preview.
9. Open a future date. Add an activity, choose saved activities, edit an occurrence with “Only this day,” and change Day Hours. Verify today and neighboring dates remain unchanged after relaunch.
10. Edit a future occurrence with “Saved activity.” Verify other occurrences without their own edits update. Long-press an edited event and choose Use Saved Activity to remove its date-specific edit.
11. Create a recurring activity on a future date. Verify it starts on or after that date and does not appear earlier.

12. Fresh-launch the app and verify Home is selected, with Home, Schedule, and Progress in the bottom tab bar. Set a name, return with the native back arrow, and verify the welcome text after relaunch.
13. Open Home → Day Hours, change the wheels without saving, switch to Progress, and return to Home. Verify the Day Hours page and draft values are retained. Save, go back, and verify the current-activity card uses today's hours.
14. Open Home → Activities, switch tabs, and return. Verify the activity bank remains open with a back arrow to Home and the bottom tabs remain visible.
15. Open Calendar from Schedule’s top-left button, open a future day, move to another day, switch to Home and back to Schedule. Verify the same day page remains open; tap the Calendar button and verify the calendar month is retained. Verify day schedules have no navigation back arrow. Repeat with Calendar opened from Home.
16. Check Home before day start, during an activity, at an activity boundary, during free time, and after day end. The clock updates live; current activity respects date-specific edits and shows conflicts explicitly.

17. Open Progress and switch between 7, 30, and 90 days. Mark and unmark scheduled activities in Schedule and confirm today's percentage updates. Tap or drag across the chart to inspect counts. Use Find a Day to choose a specific date, including one outside the graph range; verify its saved percentage or No recorded data appears and the chart range stays unchanged.
18. Verify empty days, conflicts, and missing history appear as gaps rather than 0%. A recorded day with scheduled activities and no completions must show 0%. Verify future plans never enter the graph.
19. After a day rolls over, delete or edit a library activity and confirm the prior day's result remains. Restart and confirm history survives. Deliberately editing a past day's schedule updates that day's result; later library changes do not.

20. Verify Calendar is absent from the tab bar. Open it from Home's Calendar card and from Schedule's top-left button. Verify native back navigation returns to the page that opened it.
21. Leave Schedule inside Calendar or on a different day, switch to Home, and tap View Today's Schedule. Verify the Schedule tab becomes selected and shows today's root schedule. A normal tab-bar switch must instead retain the previous page and selected day.

22. Navigate Schedule to an earlier or later day and scroll down. Tap Today in the toolbar. Verify the current date and its schedule appear and the view returns to the top. Check the same action from a day opened through Calendar.
23. Confirm Daily breakdown is absent. Open Find a Day, pick a date, then Cancel and verify the previous selection stays. Repeat with Show Progress and verify the selected day's counts. Verify future dates cannot be selected.

Progress records days observed by the app and imports available dated state from older saves. Days without recorded state remain gaps. Period completion is total completed activities divided by total scheduled activities across valid recorded days, not an unweighted average of daily percentages.

The scheduler exactly optimizes selections of up to 14 flexible activities. Larger selections use exact packing for required activities followed by per-gap optimization of optional activities; optional priority totals are not guaranteed globally optimal in that fallback.

24. Open Calendar: verify it starts at the current month, only today is highlighted, and no day agenda appears below it. Scroll vertically in both directions across year boundaries and beyond two years; months should extend without jumping. No month-arrow controls should appear.
25. Open a future day's schedule, then tap Calendar: verify the same calendar scroll location is retained, the visited day has no selection highlight, and no activity preview appears. Repeat from Home and Schedule. Use Go to Date for a distant year and confirm it scrolls to that month; tapping a day still opens its schedule.

26. On a fresh Calendar open, verify the current month is at the top. On narrow and wide devices and after rotation, verify all seven columns fit without horizontal dragging. Scroll several months away and tap Today; verify the current month returns to the top. Repeat after Go to Date, and confirm returning from a day schedule preserves the browsed month.

27. Fling Calendar quickly in either direction, then tap Today while it is still decelerating. Verify momentum stops and a quick animated return to the current month begins immediately, without a second tap or waiting for deceleration. Repeat while already in the current month and after jumping to a distant year.

28. Open Home → Goals. Create a percentage target with optional name/details/incentive. Try past, current, and future start dates, Week/Month/Year presets, and custom days/weeks/months/years. Confirm inclusive end-date preview and invalid duration rejection. Cancel an edit and verify no changes. Open an older untargeted goal and configure tracking without losing its title or incentive.
29. Tap a goal to open its progress page. Complete an activity on Schedule and return: verify the average and chart update. Check the required average includes today as an adjustable remaining day; verify impossible goals show encouragement and can be edited. Missing historical days should be explicitly reported, never silently treated as 0%.
30. Verify ended successful goals show Reward earned, ended unsuccessful goals show supportive wording, and no-data goals do not claim success. Relaunch and check settings and incentive persist. Switch tabs from goal details and return Home: the same details page should remain open.

31. In a goal editor, choose All activities or Selected activities. For Selected, choose Exercise alone, then a group of Piano/Math/Run; saving an empty selection must be disabled. Reopen saved goals and confirm selections persist. Scope labels, chart, completion counts, and required rate must match only the chosen IDs.
32. Verify a daily activity has one planned occurrence per included day and weekly activities only their scheduled weekdays. Skip or explicitly include a future occurrence and confirm the remaining estimate updates. Renaming keeps the selection; deletion preserves past results and labels the selection deleted; recreating the same name does not retarget it. All-activity goals automatically include newly created activities.
33. Verify combined rate uses completed occurrences divided by scheduled occurrences, rather than averaging daily percentages. Days with no matching scheduled activity are excluded. Remaining projections must be described as plans, including all of today, and whole required completions round up.

34. In the goal editor, choose Include → Selected activities: verify a separate Select Activities page opens. Search by partial name, select multiple results across different queries, clear search, and confirm all selections remain. Tap Done or Back and verify only selected activities appear in the editor. Reopen Choose Activities to deselect items; reselecting Selected activities must not clear the draft. Cancel the goal editor and verify edits were not persisted. Check empty-bank and no-search-results states.
