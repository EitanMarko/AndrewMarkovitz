import Foundation

private final class MemoryDefaults: UserDefaults {
    private var values: [String: Any] = [:]
    override func data(forKey key: String) -> Data? { values[key] as? Data }
    override func set(_ value: Any?, forKey key: String) { values[key] = value }
}

@main
struct RegressionTests {
    private static var assertions = 0

    static func check(_ condition: Bool, _ message: String) {
        assertions += 1
        precondition(condition, message)
    }

    static func names(_ result: ScheduleResult?) -> Set<String> {
        Set(result?.intervals.flatMap { $0.activities.map(\.name) } ?? [])
    }

    static func expectFailure(_ message: String, _ action: () throws -> Void) {
        do { try action(); fatalError(message) } catch { assertions += 1 }
    }

    static func main() throws {
        try scopedGoals()
        try productivityGoals()
        try goalsLifecycle()
        try libraryLifecycle()
        try requiredActivities()
        try largeSchedule()
        try compareWithBruteForce()
        try recurrenceAcrossDST()
        try futureDayEditing()
        try migrateDailyState()
        calendarGeometry()
        try homeStatusAndProfile()
        try progressHistoryLifecycle()
        print("Passed \(assertions) regression assertions.")
    }

    static func scopedGoals() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        let start = calendar.date(from: DateComponents(year: 2026, month: 11, day: 23))! // Monday
        var now = start
        let defaults = MemoryDefaults()
        let vm = AppViewModel(defaults: defaults, calendar: calendar, now: { now })
        let exercise = ActivityRecord(name: "Exercise", details: .flexible(duration: 20, value: nil, doToday: true), weekdays: Set(1...7))
        let piano = ActivityRecord(name: "Play Piano", details: .flexible(duration: 20, value: nil, doToday: true), weekdays: [2])
        let math = ActivityRecord(name: "Study Math", details: .flexible(duration: 20, value: nil, doToday: true), weekdays: [3])
        let run = ActivityRecord(name: "Go on a Run", details: .flexible(duration: 20, value: nil, doToday: true), weekdays: [4])
        for activity in [exercise, piano, math, run] { check(vm.addActivity(activity), "Create scope fixture") }
        let dailyTarget = ProductivityTarget(percentage: 50, startDate: start, amount: 4, unit: .weeks, activityIDs: [exercise.id], activityNames: [exercise.id: exercise.name])
        let single = PersonalGoal(title: "Exercise goal", target: dailyTarget)
        let groupTarget = ProductivityTarget(percentage: 75, startDate: start, amount: 4, unit: .weeks, activityIDs: [piano.id, math.id, run.id])
        let group = PersonalGoal(title: "Weekly group", target: groupTarget)
        let global = PersonalGoal(title: "Everything", target: ProductivityTarget(percentage: 80, startDate: start, amount: 4, unit: .weeks))
        check(vm.saveGoal(single) && vm.saveGoal(group) && vm.saveGoal(global), "Save three goal scopes")
        check(vm.goalProgress(for: single)?.remainingOccurrences == 28, "Daily goal projects 28 occurrences in four weeks")
        check(vm.goalProgress(for: group)?.remainingOccurrences == 12, "Three weekly activities project twelve occurrences, not 28 days")
        check(vm.goalProgress(for: group)?.requiredCompletions == 9, "75 percent of twelve requires nine completions")
        check(vm.goalProgress(for: global)?.remainingOccurrences == 40, "Global scope includes all activities")
        vm.markComplete(name: "Exercise")
        check(vm.goalProgress(for: single)?.average == 100, "Individual scope sees its completion")
        check(vm.goalProgress(for: group)?.average == 0, "Unrelated completion cannot inflate group")
        check(vm.goalProgress(for: global)?.average == 50, "Global counts both scheduled occurrences")
        let nextMonday = calendar.date(byAdding: .day, value: 7, to: start)!
        vm.toggleSelection(of: piano, on: nextMonday)
        check(vm.goalProgress(for: group)?.remainingOccurrences == 11, "Skipped recurrence removed from forecast")
        let nextFriday = calendar.date(byAdding: .day, value: 4, to: start)!
        vm.toggleSelection(of: math, on: nextFriday)
        check(vm.goalProgress(for: group)?.remainingOccurrences == 12, "Manually included occurrence added to forecast")
        var renamed = exercise; renamed.name = "Morning exercise"
        check(vm.addActivity(renamed), "Rename tracked activity")
        check(vm.goalActivityNames(dailyTarget) == ["Morning exercise"], "Scope uses stable IDs through rename")
        now = calendar.date(byAdding: .day, value: 1, to: start)!; vm.refreshToday()
        vm.deleteActivity(id: exercise.id)
        check(vm.goalProgress(for: single)?.average == 100, "Deletion preserves archived scoped completion")
        check(vm.goalProgress(for: single)?.remainingOccurrences == 0, "Deleted activity has no projected recurrence")
        check(vm.goalActivityNames(dailyTarget) == ["Exercise (deleted)"], "Deleted activity retains stored label")
        let replacement = ActivityRecord(name: "Exercise", details: exercise.details, weekdays: exercise.weekdays)
        check(vm.addActivity(replacement), "Recreate same name")
        check(vm.goalProgress(for: single)?.remainingOccurrences == 0, "Recreated activity does not silently replace selected ID")
        check(vm.goalProgress(for: global)!.remainingOccurrences > vm.goalProgress(for: group)!.remainingOccurrences, "Global scope includes new activity")
        let restored = AppViewModel(defaults: defaults, calendar: calendar, now: { now })
        check(restored.goals[0].target?.activityIDs == [exercise.id], "Scope survives relaunch")
        let empty = PersonalGoal(title: "Empty", target: ProductivityTarget(activityIDs: []))
        check(!vm.saveGoal(empty), "Empty selected scope is rejected")
        let a = UUID(), b = UUID(), c = UUID()
        let records = [DailyProgress(id: "one", date: start, scheduledIDs: [a], completedIDs: [a], hasConflict: false),
                       DailyProgress(id: "two", date: now, scheduledIDs: [a,b,c], completedIDs: [], hasConflict: false)]
        let weighted = GoalProgress(target: global.target!, history: records, now: now, calendar: calendar, remainingOccurrences: 3)!
        check(weighted.average == 25, "Combined rate weights occurrences, not daily percentages")
        let filtered = GoalProgress(target: ProductivityTarget(startDate: start, activityIDs: [b,c]), history: records, now: now, calendar: calendar, remainingOccurrences: 2)!
        check(filtered.scheduledCount == 2 && filtered.average == 0 && filtered.missingPastDays == 0, "Recorded day without selected activities is not a failure or missing history")
        let rounded = GoalProgress(target: ProductivityTarget(percentage: 75, startDate: start), history: [], now: start, calendar: calendar, remainingOccurrences: 3)!
        check(rounded.requiredCompletions == 3 && rounded.requiredAverage == 100, "Fractional required completions round upward")
    }

    static func productivityGoals() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        func day(_ month: Int, _ date: Int, year: Int = 2026) -> Date {
            calendar.date(from: DateComponents(year: year, month: month, day: date))!
        }
        func record(_ date: Date, _ percent: Int, conflict: Bool = false) -> DailyProgress {
            let ids = (0..<100).map { _ in UUID() }
            return DailyProgress(id: UUID().uuidString, date: date, scheduledIDs: Set(ids),
                                 completedIDs: Set(ids.prefix(percent)), hasConflict: conflict)
        }
        let start = day(3, 7)
        let target = ProductivityTarget(percentage: 80, startDate: start, amount: 1, unit: .weeks)
        let history = [record(start, 50), record(day(3, 8), 70), record(day(3, 9), 20)]
        let progress = GoalProgress(target: target, history: history, now: day(3, 9), calendar: calendar, remainingOccurrences: 500)!
        check(progress.totalDays == 7 && progress.remainingDays == 5, "Calendar days survive DST; today remains adjustable")
        check(abs(progress.requiredAverage! - 88) < 0.00001, "80 percent over 7 days after 50 and 70 requires 88 over remaining 5")
        check(abs(progress.average! - 140.0 / 3) < 0.00001, "Current average includes live today")
        let betterToday = GoalProgress(target: target, history: Array(history.prefix(2)) + [record(day(3, 9), 100)], now: day(3, 9), calendar: calendar, remainingOccurrences: 500)!
        check(betterToday.average! > progress.average! && betterToday.requiredAverage == progress.requiredAverage, "Today's completions update average but today is still a whole remaining day")
        let tomorrow = GoalProgress(target: target, history: history, now: day(3, 10), calendar: calendar, remainingOccurrences: 400)!
        check(tomorrow.isImpossible && tomorrow.requiredAverage! == 105, "Detect impossible remaining average")
        let exact = GoalProgress(target: ProductivityTarget(percentage: 50, startDate: start, amount: 2, unit: .days), history: [record(start, 0)], now: day(3, 8), calendar: calendar, remainingOccurrences: 100)!
        check(!exact.isImpossible && exact.requiredAverage == 100, "Exactly 100 remaining is attainable")
        let secured = GoalProgress(target: ProductivityTarget(percentage: 50, startDate: start, amount: 2, unit: .days), history: [record(start, 100)], now: day(3, 8), calendar: calendar, remainingOccurrences: 100)!
        check(secured.requiredAverage == 0, "Required average cannot go negative")
        let future = GoalProgress(target: target, history: history, now: day(3, 1), calendar: calendar, remainingOccurrences: 700)!
        check(!future.hasStarted && future.remainingDays == 7 && future.requiredAverage == 80 && future.average == nil, "Future goal ignores future records")
        let finished = GoalProgress(target: target, history: [record(start, 80)], now: day(3, 14), calendar: calendar)!
        check(finished.hasEnded && finished.isAchieved && finished.requiredAverage == nil, "Goal ends at exclusive boundary with no division by zero")
        check(finished.missingPastDays == 6, "Unrecorded days are explicit and excluded")
        let empty = GoalProgress(target: target, history: [], now: day(3, 14), calendar: calendar)!
        check(!empty.isAchieved && empty.average == nil, "No data does not earn a reward")
        let filtered = GoalProgress(target: target, history: [record(day(3, 6), 100), record(start, 50), record(day(3, 8), 90, conflict: true), record(day(3, 14), 100)], now: day(3, 14), calendar: calendar)!
        check(filtered.average == 50 && filtered.records.count == 1, "Only valid results within period count")
        let leap = ProductivityTarget(startDate: day(2, 1, year: 2024), amount: 1, unit: .months)
        check(GoalProgress(target: leap, history: [], now: leap.startDate, calendar: calendar)!.totalDays == 29, "Month duration accounts for leap year")
        let year = ProductivityTarget(startDate: day(1, 1, year: 2024), amount: 1, unit: .years)
        check(GoalProgress(target: year, history: [], now: year.startDate, calendar: calendar)!.totalDays == 366, "Year duration uses calendar arithmetic")
        var invalid = target
        invalid.amount = 0
        check(invalid.endExclusive(calendar: calendar) == nil, "Reject invalid duration")
        invalid = target; invalid.percentage = 101
        check(invalid.endExclusive(calendar: calendar) == nil, "Reject invalid percentage")
        let defaults = MemoryDefaults()
        var now = start
        let vm = AppViewModel(defaults: defaults, calendar: calendar, now: { now })
        let goal = PersonalGoal(title: "Weekly goal", incentive: "A trip", target: target)
        check(vm.saveGoal(goal), "Save productivity target")
        let activity = ActivityRecord(name: "Work", details: .flexible(duration: 30, value: 5, doToday: false))
        check(vm.addActivity(activity), "Create activity for live goal")
        check(vm.goalProgress(for: goal)?.average == 0, "Goal reads live daily completion")
        vm.markComplete(name: "Work")
        check(vm.goalProgress(for: goal)?.average == 100, "Goal updates after completion")
        now = day(3, 8); vm.refreshToday()
        check(vm.goalProgress(for: goal)?.remainingDays == 6, "Goal rolls forward with day")
        let restored = AppViewModel(defaults: defaults, calendar: calendar, now: { now })
        check(restored.goals.first?.target == target && restored.goals.first?.incentive == "A trip", "Target dates, duration and incentive persist")
        let legacy = PersonalGoal(title: "Old goal")
        let decoded = try JSONDecoder().decode(PersonalGoal.self, from: JSONEncoder().encode(legacy))
        check(decoded.target == nil && decoded.title == "Old goal", "Legacy goal remains available for setup")
    }

    static func goalsLifecycle() throws {
        let defaults = MemoryDefaults()
        let vm = AppViewModel(defaults: defaults)
        check(vm.goals.isEmpty, "New library has no goals")
        check(!vm.saveGoal(PersonalGoal(title: "  \n")), "Reject blank goal")
        var goal = PersonalGoal(title: " Read ten books ", notes: " This year ", incentive: " A bookshop trip ")
        check(vm.saveGoal(goal), "Create goal with incentive")
        check(vm.goals[0].title == "Read ten books" && vm.goals[0].incentive == "A bookshop trip", "Trim goal input")
        check(vm.saveGoal(PersonalGoal(title: "Practice piano")), "Incentive is optional")
        vm.toggleGoalCompletion(id: goal.id)
        let restored = AppViewModel(defaults: defaults)
        check(restored.goals.count == 2 && restored.goals[0].isCompleted, "Goals and completion survive restart")
        check(restored.goals[0].notes == "This year" && restored.goals[0].incentive == "A bookshop trip", "Details and reward persist")
        goal = restored.goals[0]
        goal.incentive = ""
        check(restored.saveGoal(goal), "Remove incentive")
        restored.toggleGoalCompletion(id: goal.id)
        let edited = AppViewModel(defaults: defaults)
        check(edited.goals.count == 2 && edited.goals[0].incentive.isEmpty && !edited.goals[0].isCompleted, "Edits and reopening persist without duplicates")
        edited.deleteGoal(id: goal.id)
        check(AppViewModel(defaults: defaults).goals.count == 1, "Goal deletion persists")
        let key = "ProductiviMe.library.v1"
        var legacy = try JSONSerialization.jsonObject(with: defaults.data(forKey: key)!) as! [String: Any]
        legacy.removeValue(forKey: "goals")
        defaults.set(try JSONSerialization.data(withJSONObject: legacy), forKey: key)
        let migrated = AppViewModel(defaults: defaults)
        check(migrated.goals.isEmpty && migrated.errorMessage == nil, "Existing libraries without goals remain compatible")
    }

    static func libraryLifecycle() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        var date = calendar.date(from: DateComponents(year: 2026, month: 11, day: 23, hour: 9))! // Monday
        let defaults = MemoryDefaults()
        let vm = AppViewModel(defaults: defaults, calendar: calendar, now: { date })
        check(vm.schedule != nil, "A schedule should exist on first launch")
        let once = ActivityRecord(name: "Zebra", details: .flexible(duration: 30, value: 7, doToday: false))
        let daily = ActivityRecord(name: "apple", details: .flexible(duration: 20, value: 9, doToday: true), weekdays: Set(1...7))
        let weekly = ActivityRecord(name: "Monday", details: .set(start: 600, end: 660), weekdays: [2])
        let tuesday = ActivityRecord(name: "Tuesday", details: .flexible(duration: 15, value: 4, doToday: false), weekdays: [3])
        for activity in [once, daily, weekly, tuesday] { check(vm.addActivity(activity), "Create activity") }
        check(vm.orderedCatalog.map(\.name) == ["apple", "Monday", "Tuesday", "Zebra"], "Alphabetical default")
        check(names(vm.schedule) == ["Zebra", "apple", "Monday"], "Only today's occurrences")
        if case .flexible(_, let priority, _) = vm.catalog.first(where: { $0.id == daily.id })!.details {
            check(priority == nil, "Required activity must not store priority")
        }
        vm.markComplete(name: "apple")
        check(vm.isCompleted(name: "apple"), "Mark completion")
        vm.dayStartMinutes = 7 * 60
        check(vm.applyDayHours(), "Change hours")
        check(vm.isCompleted(name: "apple"), "Changing hours preserves completion")
        let timelineBeforeMove = vm.schedule!.intervals.flatMap { $0.activities.map(\.name) }
        vm.moveActivities(from: IndexSet(integer: 3), to: 0)
        check(vm.orderedCatalog.map(\.name) == ["Zebra", "apple", "Monday", "Tuesday"], "Custom ordering")
        let restored = AppViewModel(defaults: defaults, calendar: calendar, now: { date })
        check(restored.orderedCatalog.map(\.id) == vm.orderedCatalog.map(\.id), "Order and identity survive reload")
        check(restored.customOrder, "Custom sort survives reload")
        check(restored.schedule!.intervals.flatMap { $0.activities.map(\.name) } == timelineBeforeMove, "Library reordering leaves scheduling order unchanged")
        check(restored.isCompleted(name: "apple"), "Completion survives reload")
        check(restored.dayStartMinutes == 420, "Day hours persist")
        check(names(restored.schedule) == names(vm.schedule), "Schedule regenerates on reload")
        restored.toggleSelection(of: daily)
        check(!names(restored.schedule).contains("apple"), "Skip occurrence today")
        let skipped = AppViewModel(defaults: defaults, calendar: calendar, now: { date })
        check(!skipped.isSelected(daily), "Skip survives reload")
        date = calendar.date(byAdding: .day, value: 1, to: date)!
        skipped.refreshIfNeeded()
        check(names(skipped.schedule) == ["apple", "Tuesday"], "Rollover resets skips and chooses weekday")
        check(skipped.catalog.count == 4, "One-time activity stays in library")
        check(!skipped.isCompleted(name: "apple"), "Completion is per occurrence")
        skipped.toggleSelection(of: once)
        check(names(skipped.schedule).contains("Zebra"), "Reuse saved one-time activity")
        let reused = AppViewModel(defaults: defaults, calendar: calendar, now: { date })
        check(names(reused.schedule).contains("Zebra"), "Manual inclusion persists")
        reused.deleteActivity(id: daily.id)
        check(!names(reused.schedule).contains("apple"), "Delete removes schedule occurrence")
        let deleted = AppViewModel(defaults: defaults, calendar: calendar, now: { date })
        check(!deleted.catalog.contains(where: { $0.id == daily.id }), "Deletion survives reload")
        date = calendar.date(byAdding: .day, value: 1, to: date)!
        deleted.refreshIfNeeded()
        check(names(deleted.schedule).isEmpty, "Deleted recurrence cannot return")
        check(deleted.catalog.count == 3, "Other saved activities remain")
        deleted.useAlphabeticalOrder()
        check(!deleted.customOrder && deleted.orderedCatalog.first?.name == "Monday", "Restore alphabetical order")
        check(!deleted.addActivity(ActivityRecord(name: " zebra ", details: once.details)), "Reject duplicate without replacing original")
        check(deleted.catalog.contains(where: { $0.id == once.id }), "Duplicate preserves original")
        var edited = deleted.catalog.first(where: { $0.id == once.id })!
        edited.name = "Renamed"
        edited.weekdays = Set(1...7)
        check(deleted.addActivity(edited), "Edit in place")
        check(deleted.catalog.count == 3 && names(deleted.schedule).contains("Renamed"), "Edit keeps identity and updates schedule")
        let conflict = ActivityRecord(name: "Too early", details: .set(start: 0, end: 60))
        check(deleted.addActivity(conflict), "Conflicted activity still saves")
        check(deleted.schedule == nil && deleted.scheduleError != nil, "Conflict is explicit")
        check(deleted.catalog.contains(where: { $0.id == conflict.id }), "Conflict remains in library")
        deleted.toggleSelection(of: conflict)
        check(deleted.schedule != nil, "Skipping conflict restores automatic schedule")
        deleted.dayEndMinutes = 0
        check(!deleted.applyDayHours() && deleted.schedule != nil, "Invalid day hours preserve last valid schedule")
        let corrupt = MemoryDefaults()
        let invalidData = Data("invalid JSON".utf8)
        corrupt.set(invalidData, forKey: "ProductiviMe.library.v1")
        let failed = AppViewModel(defaults: corrupt, now: { date })
        check(failed.errorMessage != nil && !failed.addActivity(once), "Unreadable library is protected")
        check(corrupt.data(forKey: "ProductiviMe.library.v1") == invalidData, "Never overwrite unreadable data")
    }

    static func requiredActivities() throws {
        let engine = try BangForYourBuck(startTime: "8:00", endTime: "9:00")
        try engine.addActivity(FlexibleActivity(name: "Required", durationStr: "0:45", value: nil, mustDo: true))
        try engine.addActivity(FlexibleActivity(name: "High priority", durationStr: "1:00", value: 10))
        let result = try engine.generateSchedule()
        check(names(result) == ["Required"], "Required beats optional even without priority")
        check(result.intervals.first?.activities.first?.value == nil, "No required priority in result")
        check(result.intervals.last?.activities.isEmpty == true, "Unused time is shown")
        try engine.addActivity(FlexibleActivity(name: "Required 2", durationStr: "0:30", value: nil, mustDo: true))
        expectFailure("Required overload must fail") { _ = try engine.generateSchedule() }
        let fragmented = try BangForYourBuck(startTime: "8:00", endTime: "10:00")
        try fragmented.addActivity(SetActivity(name: "Meeting", startTime: "8:30", endTime: "9:30"))
        try fragmented.addActivity(FlexibleActivity(name: "Needs full hour", durationStr: "1:00", value: nil, mustDo: true))
        expectFailure("Total free minutes are insufficient without a contiguous gap") { _ = try fragmented.generateSchedule() }
        let fixed = try BangForYourBuck(startTime: "8:00", endTime: "10:00")
        try fixed.addActivity(SetActivity(name: "First", startTime: "8:00", endTime: "9:00"))
        try fixed.addActivity(SetActivity(name: "Second", startTime: "9:00", endTime: "10:00"))
        check(names(try fixed.generateSchedule()) == ["First", "Second"], "Fully fixed day displays both appointments")
        expectFailure("Containing fixed interval overlaps") {
            try fixed.addActivity(SetActivity(name: "Contains", startTime: "8:00", endTime: "10:00"))
        }
        expectFailure("Zero duration invalid") { _ = try FlexibleActivity(name: "Zero", durationStr: "0:00", value: 5) }
        expectFailure("Optional needs priority") { _ = try FlexibleActivity(name: "No priority", durationStr: "0:10", value: nil) }
    }

    static func largeSchedule() throws {
        let engine = try BangForYourBuck(startTime: "8:00", endTime: "9:30")
        for i in 0..<16 {
            try engine.addActivity(FlexibleActivity(name: "Required \(i)", durationStr: "0:05", value: nil, mustDo: true))
        }
        try engine.addActivity(FlexibleActivity(name: "Optional", durationStr: "0:10", value: 10))
        check(names(try engine.generateSchedule()).count == 17, "Large fallback includes all required activities")
        try engine.addActivity(FlexibleActivity(name: "Overload", durationStr: "0:15", value: nil, mustDo: true))
        expectFailure("Large fallback must report required overload") { _ = try engine.generateSchedule() }

        // Best-fit packing must backtrack: 25,20,10,10 need gaps of 30 and 35.
        let packing = try BangForYourBuck(startTime: "8:00", endTime: "9:10")
        try packing.addActivity(SetActivity(name: "Divider", startTime: "8:30", endTime: "8:35"))
        for (i, duration) in [25, 20, 10, 10].enumerated() {
            try packing.addActivity(FlexibleActivity(name: "Must \(i)", durationStr: TimeParser.toString(duration), value: nil, mustDo: true))
        }
        for i in 0..<12 { try packing.addActivity(FlexibleActivity(name: "Extra \(i)", durationStr: "0:05", value: 1)) }
        check(names(try packing.generateSchedule()) == ["Divider", "Must 0", "Must 1", "Must 2", "Must 3"], "Large fallback packs fragmented requirements")
    }

    static func compareWithBruteForce() throws {
        // Deterministic randomized differential check against exhaustive assignment.
        var seed: UInt64 = 42
        func random(_ upper: Int) -> Int {
            seed = seed &* 6364136223846793005 &+ 1
            return Int((seed >> 32) % UInt64(upper))
        }
        for _ in 0..<100 {
            let cap1 = (random(6) + 1) * 5
            let cap2 = (random(6) + 1) * 5
            let engine = try BangForYourBuck(startTime: "8:00", endTime: TimeParser.toString(480 + cap1 + 5 + cap2))
            try engine.addActivity(SetActivity(name: "Divider", startTime: TimeParser.toString(480 + cap1), endTime: TimeParser.toString(485 + cap1)))
            var items: [(duration: Int, priority: Int, required: Bool)] = []
            for i in 0..<6 {
                let item = (duration: (random(min(5, (cap1 + cap2 + 5) / 5)) + 1) * 5, priority: random(10) + 1, required: random(3) == 0)
                items.append(item)
                try engine.addActivity(FlexibleActivity(name: "Item \(i)", durationStr: TimeParser.toString(item.duration), value: item.priority, mustDo: item.required))
            }
            func optimal(_ i: Int, _ a: Int, _ b: Int) -> Int? {
                if i == items.count { return 0 }
                let item = items[i]
                var scores: [Int] = []
                if !item.required, let rest = optimal(i + 1, a, b) { scores.append(rest) }
                if item.duration <= a, let rest = optimal(i + 1, a - item.duration, b) { scores.append(rest + (item.required ? 0 : item.priority)) }
                if item.duration <= b, let rest = optimal(i + 1, a, b - item.duration) { scores.append(rest + (item.required ? 0 : item.priority)) }
                return scores.max()
            }
            let expected = optimal(0, cap1, cap2)
            do {
                let result = try engine.generateSchedule()
                check(expected == result.totalValue, "Exact schedule matches exhaustive optimum")
                check(result.intervals.reduce(0, { $0 + $1.durationMinutes }) == cap1 + cap2 + 5, "Timeline covers whole day")
                let scheduled = names(result)
                for i in items.indices where items[i].required { check(scheduled.contains("Item \(i)"), "All mandatory items present") }
                for pair in zip(result.intervals, result.intervals.dropFirst()) { check(pair.0.end == pair.1.start, "No gaps or overlaps") }
            } catch { check(expected == nil, "Only infeasible required combinations throw") }
        }
    }

    static func recurrenceAcrossDST() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        let saturday = calendar.date(from: DateComponents(year: 2026, month: 10, day: 31, hour: 12))!
        let sunday = calendar.date(byAdding: .day, value: 1, to: saturday)!
        let monday = calendar.date(byAdding: .day, value: 1, to: sunday)!
        let weekend = ActivityRecord(name: "Weekend", details: .flexible(duration: 5, value: 1, doToday: false), weekdays: [1, 7])
        check(weekend.occurs(on: saturday, calendar: calendar), "Saturday recurrence")
        check(weekend.occurs(on: sunday, calendar: calendar), "Sunday recurrence across DST")
        check(!weekend.occurs(on: monday, calendar: calendar), "No Monday weekend recurrence")
    }
    static func futureDayEditing() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        var now = calendar.date(from: DateComponents(year: 2026, month: 11, day: 23, hour: 9))!
        let originalToday = now
        let future = calendar.date(byAdding: .day, value: 5, to: now)!
        let after = calendar.date(byAdding: .day, value: 1, to: future)!
        let defaults = MemoryDefaults()
        let vm = AppViewModel(defaults: defaults, calendar: calendar, now: { now })
        let daily = ActivityRecord(name: "Read", details: .flexible(duration: 30, value: 5, doToday: false), weekdays: Set(1...7))
        check(vm.addActivity(daily), "Create recurring activity")
        let todayNames = names(vm.schedule)
        let beforePreview = defaults.data(forKey: "ProductiviMe.library.v1")
        check(names(vm.schedule(for: future).schedule) == ["Read"], "Future recurrence preview")
        check(names(vm.schedule) == todayNames, "Preview does not alter today's schedule")
        check(defaults.data(forKey: "ProductiviMe.library.v1") == beforePreview, "Calendar browsing never writes state")
        let once = ActivityRecord(name: "Dentist", details: .set(start: 600, end: 630))
        check(vm.addActivity(once, on: future), "Create future one-time activity")
        check(!names(vm.schedule).contains("Dentist"), "Future creation does not join today")
        check(names(vm.schedule(for: future).schedule).contains("Dentist"), "Future one-time activity joins its date")
        check(!names(vm.schedule(for: after).schedule).contains("Dentist"), "One-time activity does not recur")
        check(vm.catalog.contains { $0.id == once.id }, "Future activity persists in library")
        var override = vm.catalog.first { $0.id == daily.id }!
        override.name = "Read a chapter"
        override.details = .set(start: 660, end: 720)
        check(vm.updateOccurrence(override, on: future), "Edit a future occurrence")
        check(names(vm.schedule) == ["Read"], "Occurrence edit leaves today unchanged")
        check(names(vm.schedule(for: future).schedule) == ["Read a chapter", "Dentist"], "Only future occurrence renamed")
        check(names(vm.schedule(for: after).schedule) == ["Read"], "Other recurrence remains unchanged")
        check(vm.catalog.first { $0.id == daily.id }?.name == "Read", "Occurrence edit preserves library")
        check(vm.hasOverride(id: daily.id, on: future), "Occurrence override recorded")
        check(vm.setDayHours(start: 540, end: 900, on: future), "Set future day hours")
        check(vm.schedule(for: future).start == 540 && vm.schedule(for: originalToday).start == 480, "Day hours are independent")
        vm.markComplete(name: "Read a chapter", on: future)
        check(vm.schedule(for: future).eligible.first { $0.id == daily.id }?.completed == true, "Future occurrence completion saved")
        check(!vm.isCompleted(name: "Read"), "Completion does not leak to today")
        let reloaded = AppViewModel(defaults: defaults, calendar: calendar, now: { now })
        check(names(reloaded.schedule(for: future).schedule) == ["Read a chapter", "Dentist"], "Future edits survive reload")
        check(reloaded.schedule(for: future).start == 540, "Future hours survive reload")
        check(reloaded.schedule(for: future).eligible.first { $0.id == daily.id }?.completed == true, "Future completion survives reload")
        reloaded.toggleSelection(of: once, on: future)
        check(!names(reloaded.schedule(for: future).schedule).contains("Dentist"), "Remove only selected date")
        check(reloaded.catalog.contains { $0.id == once.id }, "Removing from a day retains library")
        now = future
        reloaded.refreshIfNeeded()
        check(names(reloaded.schedule) == ["Read a chapter"], "Future plan becomes today's schedule at rollover")
        check(reloaded.isCompleted(name: "Read a chapter"), "Rollover preserves previously saved occurrence state")
        now = after
        reloaded.refreshIfNeeded()
        check(names(reloaded.schedule) == ["Read"], "Next day uses normal recurrence")
        check(!reloaded.isCompleted(name: "Read"), "Completion remains date-scoped")
        reloaded.resetOccurrence(id: daily.id, on: future)
        check(names(reloaded.schedule(for: future).schedule) == ["Read"], "Restore library version for one date")
        reloaded.useDefaultHours(on: future)
        check(reloaded.schedule(for: future).start == 480, "Restore default hours")
        reloaded.deleteActivity(id: daily.id)
        check(reloaded.schedule(for: future).eligible.isEmpty, "Delete removes all future occurrences and overrides")
        check(reloaded.schedule(for: originalToday).eligible.isEmpty, "Delete removes historical occurrences too")
        let startLater = calendar.date(byAdding: .day, value: 10, to: now)!
        let newSeries = ActivityRecord(name: "Future series", details: .flexible(duration: 15, value: 5, doToday: false), weekdays: Set(1...7))
        check(reloaded.addActivity(newSeries, on: startLater), "Create recurrence starting in future")
        check(!names(reloaded.schedule).contains("Future series"), "New recurrence never appears before its start date")
        check(names(reloaded.schedule(for: startLater).schedule).contains("Future series"), "New recurrence starts on chosen date")
        var invalid = newSeries
        invalid.details = .set(start: 600, end: 500)
        check(!reloaded.updateOccurrence(invalid, on: startLater), "Invalid occurrence edit is rejected")
        check(reloaded.schedule(for: startLater).error == nil, "Rejected edit preserves valid future schedule")
        check(!reloaded.setDayHours(start: 900, end: 600, on: startLater), "Reject invalid per-date hours")
    }

    static func migrateDailyState() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let date = calendar.date(from: DateComponents(year: 2026, month: 12, day: 1))!
        let defaults = MemoryDefaults()
        let vm = AppViewModel(defaults: defaults, calendar: calendar, now: { date })
        let activity = ActivityRecord(name: "Legacy", details: .flexible(duration: 5, value: 2, doToday: false))
        check(vm.addActivity(activity), "Create migration fixture")
        vm.markComplete(name: "Legacy")
        var legacy = try JSONSerialization.jsonObject(with: defaults.data(forKey: "ProductiviMe.library.v1")!) as! [String: Any]
        legacy.removeValue(forKey: "dayPlans")
        legacy.removeValue(forKey: "username")
        legacy.removeValue(forKey: "progressHistory")
        var catalog = legacy["catalog"] as! [[String: Any]]
        catalog[0].removeValue(forKey: "recurrenceStart")
        legacy["catalog"] = catalog
        defaults.set(try JSONSerialization.data(withJSONObject: legacy), forKey: "ProductiviMe.library.v1")
        let migrated = AppViewModel(defaults: defaults, calendar: calendar, now: { date })
        check(migrated.catalog.count == 1 && migrated.errorMessage == nil, "Old saved library loads")
        check(migrated.isCompleted(name: "Legacy"), "Old daily completion migrates")
        let nextDay = calendar.date(byAdding: .day, value: 1, to: date)!
        check(names(migrated.schedule(for: nextDay).schedule).isEmpty, "Legacy one-time date is preserved")
        let nextLaunch = AppViewModel(defaults: defaults, calendar: calendar, now: { date })
        check(nextLaunch.isCompleted(name: "Legacy"), "Migrated state survives next restart")
        var legacyRecurring = catalog[0]
        legacyRecurring["weekdays"] = Array(1...7)
        legacy["catalog"] = [legacyRecurring]
        defaults.set(try JSONSerialization.data(withJSONObject: legacy), forKey: "ProductiviMe.library.v1")
        let recurringMigration = AppViewModel(defaults: defaults, calendar: calendar, now: { date })
        var edited = recurringMigration.catalog[0]
        edited.name = "Edited legacy series"
        check(recurringMigration.addActivity(edited, on: nextDay), "Edit migrated recurring activity from future day")
        check(names(recurringMigration.schedule).contains("Edited legacy series"), "Editing legacy series must not move its start date")
    }

    static func calendarGeometry() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        for firstWeekday in [1, 2, 7] {
            calendar.firstWeekday = firstWeekday
            for (year, month, expectedCount) in [(2028, 2, 29), (2027, 2, 28), (2026, 11, 30), (2026, 3, 31), (2026, 12, 31)] {
                let date = calendar.date(from: DateComponents(year: year, month: month, day: 15))!
                let grid = CalendarMonth(containing: date, calendar: calendar)
                let dates = grid.cells.compactMap { $0 }
                check(dates.count == expectedCount, "Month includes every date once")
                check(grid.cells.count % 7 == 0, "Month has complete weeks")
                let leading = grid.cells.prefix { $0 == nil }.count
                check(leading == (calendar.component(.weekday, from: grid.firstDay) - firstWeekday + 7) % 7, "Month respects first weekday")
                check(Set(dates.map { calendar.component(.day, from: $0) }).count == expectedCount, "No duplicates across DST")
            }
        }
    }

    static func homeStatusAndProfile() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        var now = calendar.date(from: DateComponents(year: 2026, month: 11, day: 23, hour: 9))!
        let defaults = MemoryDefaults()
        let vm = AppViewModel(defaults: defaults, calendar: calendar, now: { now })
        func at(_ hour: Int, _ minute: Int = 0) -> Date {
            calendar.date(bySettingHour: hour, minute: minute, second: 0, of: now)!
        }
        check(vm.username.isEmpty, "New users have no invented name")
        vm.setUsername("  Jordan  ")
        check(vm.username == "Jordan", "Trim the user's name")
        let reloaded = AppViewModel(defaults: defaults, calendar: calendar, now: { now })
        check(reloaded.username == "Jordan", "Greeting name survives restart")
        check(vm.currentScheduleStatus(at: at(7, 59)) == .beforeDay(starts: "8:00"), "Home before day begins")
        check(vm.currentScheduleStatus(at: at(8)) == .freeTime(until: "18:00"), "Empty day is free time")
        check(vm.currentScheduleStatus(at: at(18)) == .dayEnded, "Day end is exclusive")
        let meeting = ActivityRecord(name: "Meeting", details: .set(start: 540, end: 600), weekdays: Set(1...7))
        let walk = ActivityRecord(name: "Walk", details: .set(start: 600, end: 630))
        check(vm.addActivity(meeting) && vm.addActivity(walk), "Create current activity fixtures")
        check(vm.currentScheduleStatus(at: at(8, 59)) == .freeTime(until: "9:00"), "Before first appointment")
        check(vm.currentScheduleStatus(at: at(9)) == .activity(name: "Meeting", start: "9:00", end: "10:00", completed: false), "Activity begins exactly on boundary")
        check(vm.currentScheduleStatus(at: at(9, 59)) == .activity(name: "Meeting", start: "9:00", end: "10:00", completed: false), "Activity is current until its end")
        check(vm.currentScheduleStatus(at: at(10)) == .activity(name: "Walk", start: "10:00", end: "10:30", completed: false), "Next activity replaces previous at exact boundary")
        check(vm.currentScheduleStatus(at: at(10, 30)) == .freeTime(until: "18:00"), "Completed time block becomes free time")
        vm.markComplete(name: "Meeting")
        check(vm.currentScheduleStatus(at: at(9)) == .activity(name: "Meeting", start: "9:00", end: "10:00", completed: true), "Scheduled activity still shows when completed early")
        var edited = vm.catalog.first { $0.id == meeting.id }!
        edited.name = "Planning"
        check(vm.updateOccurrence(edited, on: now), "Edit today's displayed occurrence")
        check(vm.currentScheduleStatus(at: at(9)) == .activity(name: "Planning", start: "9:00", end: "10:00", completed: true), "Home reflects date-specific edits")
        check(vm.setDayHours(start: 540, end: 660, on: now), "Set actual day hours")
        check(vm.currentScheduleStatus(at: at(8)) == .beforeDay(starts: "9:00"), "Home honors today's hours")
        check(vm.currentScheduleStatus(at: at(11)) == .dayEnded, "Home honors today's custom end")
        let impossible = ActivityRecord(name: "Too long", details: .flexible(duration: 180, value: nil, doToday: true))
        check(vm.addActivity(impossible), "Keep conflicting activity in library")
        if case .conflict = vm.currentScheduleStatus(at: at(9)) { check(true, "Surface scheduling conflict") }
        else { check(false, "Home must not display a stale activity when schedule conflicts") }
        vm.deleteActivity(id: impossible.id)
        now = calendar.date(byAdding: .day, value: 1, to: now)!
        check(vm.currentScheduleStatus(at: at(9)) == .activity(name: "Meeting", start: "9:00", end: "10:00", completed: false), "Home projects correct day before rollover refresh")
        vm.refreshIfNeeded()
        check(vm.currentScheduleStatus(at: at(9)) == .activity(name: "Meeting", start: "9:00", end: "10:00", completed: false), "Rollover updates Home without old completion")
        vm.setUsername("  ")
        check(vm.username.isEmpty, "Clearing name restores neutral greeting")
    }

    static func progressHistoryLifecycle() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        var now = calendar.date(from: DateComponents(year: 2026, month: 10, day: 31, hour: 9))!
        let firstDate = now
        let defaults = MemoryDefaults()
        let vm = AppViewModel(defaults: defaults, calendar: calendar, now: { now })
        check(vm.progressDays(count: 7).count == 7, "Progress has exact selected date range")
        check(vm.progressDays(count: 7).dropLast().allSatisfy { $0.result == nil }, "Do not invent historic zero-percent days")
        check(vm.progressDays(count: 1)[0].result?.scheduledCount == 0, "Empty day is recorded as empty")
        check(vm.progressDays(count: 1)[0].percentage == nil, "Empty day has no completion percentage")
        check(ProgressSummary(days: vm.progressDays(count: 7)).percentage == nil, "Empty history has no average")
        let first = ActivityRecord(name: "First", details: .flexible(duration: 30, value: 1, doToday: false), weekdays: Set(1...7))
        let second = ActivityRecord(name: "Second", details: .flexible(duration: 30, value: 10, doToday: false), weekdays: Set(1...7))
        check(vm.addActivity(first) && vm.addActivity(second), "Create progress fixtures")
        check(vm.progressDays(count: 1)[0].percentage == 0, "Scheduled but unfinished is a real zero")
        vm.markComplete(name: "First")
        check(vm.progressDays(count: 1)[0].percentage == 50, "Completion weights activities equally regardless of priority")
        vm.markComplete(name: "Second")
        check(vm.progressDays(count: 1)[0].percentage == 100, "All completed is 100 percent")
        vm.markComplete(name: "Second")
        check(vm.progressDays(count: 1)[0].percentage == 50, "Undo completion updates today's chart")
        let future = calendar.date(byAdding: .day, value: 5, to: now)!
        vm.markComplete(name: "Second", on: future)
        check(vm.progressDays(count: 7).allSatisfy { $0.date <= now }, "Future progress is excluded")
        check(vm.progressDays(count: 1)[0].percentage == 50, "Future completion does not alter today")
        now = calendar.date(byAdding: .day, value: 1, to: now)!
        vm.refreshIfNeeded()
        let twoDays = vm.progressDays(count: 2)
        check(twoDays[0].percentage == 50 && twoDays[1].percentage == 0, "Rollover preserves history and starts a fresh day")
        check(twoDays.map(\.id).count == Set(twoDays.map(\.id)).count, "No duplicate dates across DST")
        check(calendar.component(.day, from: twoDays[0].date) == 31 && calendar.component(.day, from: twoDays[1].date) == 1, "Calendar-day arithmetic crosses DST correctly")
        vm.deleteActivity(id: first.id)
        check(vm.progressDays(count: 2)[0].percentage == 50, "Deleting a completed library activity doesn't erase history")
        check(vm.progressDays(count: 2)[0].result?.scheduledCount == 2, "Historical denominator survives deletion")
        check(vm.progressDays(count: 1)[0].result?.scheduledCount == 1, "Today's denominator reflects actual schedule")
        vm.markComplete(name: "Second")
        let summary = ProgressSummary(days: vm.progressDays(count: 2))
        check(summary.completed == 2 && summary.scheduled == 3, "Period summary combines counts, not daily averages")
        check(abs(summary.percentage! - 200.0 / 3.0) < 0.001, "Weighted period completion")
        vm.markComplete(name: "Second", on: firstDate)
        check(vm.progressDays(count: 2)[0].percentage == 100, "Late completion preserves archived deleted activities")
        vm.markComplete(name: "Second", on: firstDate)
        check(vm.progressDays(count: 2)[0].percentage == 50, "Undo late completion keeps archived denominator")
        let restored = AppViewModel(defaults: defaults, calendar: calendar, now: { now })
        check(restored.progressDays(count: 2)[0].percentage == 50, "Progress history survives restart")
        check(restored.progressDays(count: 1)[0].percentage == 100, "Today's progress survives restart")
        var renamed = restored.catalog[0]
        renamed.name = "Renamed"
        check(restored.addActivity(renamed), "Edit saved activity")
        check(restored.progressDays(count: 2)[0].percentage == 50, "Library edits do not rewrite historic progress")
        let overload = ActivityRecord(name: "Overload", details: .flexible(duration: 700, value: nil, doToday: true))
        // Use a valid five-minute duration that cannot fit in the day.
        var tooLong = overload
        tooLong.details = .flexible(duration: 700, value: nil, doToday: true)
        check(restored.addActivity(tooLong), "Save a schedule conflict")
        check(restored.progressDays(count: 1)[0].result?.hasConflict == true, "Conflicted schedule is flagged")
        check(restored.progressDays(count: 1)[0].percentage == nil, "Conflict is not a misleading zero")
        restored.deleteActivity(id: tooLong.id)
        check(restored.progressDays(count: 1)[0].percentage == 100, "Resolving conflict restores current result")
        now = calendar.date(byAdding: .day, value: 3, to: now)!
        restored.refreshIfNeeded()
        check(restored.progressDays(count: 3).dropLast().allSatisfy { $0.result == nil }, "Unobserved days remain missing, not inferred failures")
        check(restored.progressDays(count: 90).count == 90, "90-day range")
        check(restored.progressDays(count: 0).isEmpty, "Empty range is safe")
        let key = "ProductiviMe.library.v1"
        var legacy = try JSONSerialization.jsonObject(with: defaults.data(forKey: key)!) as! [String: Any]
        legacy.removeValue(forKey: "progressHistory")
        defaults.set(try JSONSerialization.data(withJSONObject: legacy), forKey: key)
        let migrated = AppViewModel(defaults: defaults, calendar: calendar, now: { now })
        check(migrated.errorMessage == nil, "Libraries without progress data still load")
        check(migrated.progressDays(count: 7).contains { $0.result?.completedCount == 1 }, "Migrate available dated completion state")
        check(migrated.progressDays(count: 7).last?.result != nil, "Migration initializes today's record")

        let optionalDefaults = MemoryDefaults()
        let packed = AppViewModel(defaults: optionalDefaults, calendar: calendar, now: { now })
        check(packed.setDayHours(start: 480, end: 510, on: now), "Small day fixture")
        let required = ActivityRecord(name: "Required", details: .flexible(duration: 30, value: nil, doToday: true))
        let optional = ActivityRecord(name: "Optional", details: .flexible(duration: 30, value: 10, doToday: false))
        check(packed.addActivity(required) && packed.addActivity(optional), "Add optional that won't fit")
        packed.markComplete(name: "Required")
        check(packed.progressDays(count: 1)[0].result?.scheduledCount == 1, "Unscheduled activities don't inflate denominator")
        check(packed.progressDays(count: 1)[0].percentage == 100, "Completing all scheduled work earns 100 percent")
        check(restored.progress(on: firstDate).percentage == 50, "Direct lookup returns archived daily progress")
        now = calendar.date(byAdding: .day, value: 120, to: now)!
        restored.refreshIfNeeded()
        check(!restored.progressDays(count: 90).contains { $0.id == restored.dayKey(firstDate) }, "Lookup fixture is outside chart window")
        check(restored.progress(on: firstDate).percentage == 50, "Date lookup can retrieve history older than 90 days")
        let sameDayAfternoon = calendar.date(bySettingHour: 16, minute: 25, second: 0, of: firstDate)!
        check(restored.progress(on: sameDayAfternoon).id == restored.progress(on: firstDate).id, "Date lookup ignores time of day")
        let missingDay = calendar.date(byAdding: .day, value: -300, to: now)!
        check(restored.progress(on: missingDay).result == nil, "Unknown date returns no recorded data")
        let futureDay = calendar.date(byAdding: .day, value: 1, to: now)!
        check(restored.progress(on: futureDay).result == nil, "Future lookup cannot return a completion result")
    }

}
