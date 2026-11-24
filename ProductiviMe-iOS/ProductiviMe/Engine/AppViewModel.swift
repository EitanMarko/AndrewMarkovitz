import Foundation
import Combine

final class AppViewModel: ObservableObject {
    @Published var dayStartMinutes = 8 * 60
    @Published var dayEndMinutes = 18 * 60
    @Published private(set) var catalog: [ActivityRecord] = []
    @Published private(set) var eligible: [EligibleActivity] = []
    @Published private(set) var customOrder = false
    @Published private(set) var schedule: ScheduleResult?
    @Published private(set) var scheduleError: String?
    @Published private(set) var today: Date
    @Published var errorMessage: String?
    @Published private(set) var username = ""
    @Published private(set) var goals: [PersonalGoal] = []

    private let defaults: UserDefaults
    private let now: () -> Date
    private let calendar: Calendar
    private let storageKey = "ProductiviMe.library.v1"
    private var activeStart = 8 * 60
    private var activeEnd = 18 * 60
    private var currentDay = ""
    @Published private var progressHistory: [String: DailyProgress] = [:]
    @Published private var dayPlans: [String: DayPlan] = [:]

    struct DayPlan: Codable {
        var selections: [UUID: Bool] = [:]
        var completed: Set<UUID> = []
        var overrides: [UUID: ActivityRecord] = [:]
        var start: Int?
        var end: Int?
    }

    struct DaySchedule {
        let date: Date
        let start: Int
        let end: Int
        var eligible: [EligibleActivity]
        var schedule: ScheduleResult?
        var error: String?

        var completionSummary: String {
            let names = Set(schedule?.intervals.flatMap { $0.activities.map(\.name) } ?? [])
            let count = eligible.filter { names.contains($0.record.name) && $0.completed }.count
            return "\(count) of \(names.count) completed"
        }
    }
    private var canSave = true

    private struct SavedState: Codable {
        var catalog: [ActivityRecord]
        var customOrder: Bool
        var dayStart: Int
        var dayEnd: Int
        var day: String
        var selections: [UUID: Bool]
        var completed: Set<UUID>
        var dayPlans: [String: DayPlan]?
        var username: String?
        var goals: [PersonalGoal]?
        var progressHistory: [String: DailyProgress]?
    }

    init(defaults: UserDefaults = .standard, calendar: Calendar = .autoupdatingCurrent,
         now: @escaping () -> Date = Date.init) {
        self.defaults = defaults
        self.calendar = calendar
        self.now = now
        today = now()
        if let data = defaults.data(forKey: storageKey) {
            do {
                let saved = try JSONDecoder().decode(SavedState.self, from: data)
                progressHistory = saved.progressHistory ?? [:]
                username = saved.username ?? ""
                goals = saved.goals ?? []
                catalog = saved.catalog
                customOrder = saved.customOrder
                dayStartMinutes = saved.dayStart
                dayEndMinutes = saved.dayEnd
                activeStart = saved.dayStart
                activeEnd = saved.dayEnd
                currentDay = saved.day
                dayPlans = saved.dayPlans ?? [:]
                if saved.dayPlans == nil, !saved.day.isEmpty {
                    dayPlans[saved.day] = DayPlan(selections: saved.selections, completed: saved.completed)
                }
                if saved.progressHistory == nil {
                    let knownDays = Set(dayPlans.keys).union([saved.day])
                    for key in knownDays {
                        if let date = date(forDayKey: key) { recordProgress(for: date) }
                    }
                }
            } catch {
                // Never overwrite a library that could not be read.
                canSave = false
                errorMessage = "Your saved activities could not be loaded. The saved library has been preserved."
            }
        }
        refreshToday()
    }

    var orderedCatalog: [ActivityRecord] {
        customOrder ? catalog : catalog.sorted {
            $0.name.localizedStandardCompare($1.name) == .orderedAscending
        }
    }

    var activeDayRange: String {
        "\(TimeParser.toString(activeStart)) – \(TimeParser.toString(activeEnd))"
    }

    var completionSummary: String {
        let scheduled = Set(schedule?.intervals.flatMap { $0.activities.map(\.id) } ?? [])
        let done = eligible.filter { scheduled.contains($0.record.name) && $0.completed }.count
        return "\(done) of \(scheduled.count) completed"
    }

    func refreshToday() {
        today = now()
        let day = ActivityRecord.dayKey(today, calendar: calendar)
        currentDay = day
        rebuildSchedule()
        save()
    }

    func refreshIfNeeded() {
        if currentDay != ActivityRecord.dayKey(now(), calendar: calendar) { refreshToday() }
    }

    @discardableResult
    func applyDayHours() -> Bool {
        do {
            _ = try BangForYourBuck(startTime: TimeParser.toString(dayStartMinutes),
                                   endTime: TimeParser.toString(dayEndMinutes))
            activeStart = dayStartMinutes
            activeEnd = dayEndMinutes
            refreshToday()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    /// Creating or editing an activity always saves it, even if today's hours conflict.
    @discardableResult
    func addActivity(_ input: ActivityRecord, on date: Date? = nil) -> Bool {
        guard canSave else {
            errorMessage = "The saved library could not be loaded. Restart the app before changing activities."
            return false
        }
        refreshIfNeeded()
        var record = input
        record.name = record.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !record.name.isEmpty else { errorMessage = "Enter an activity name."; return false }
        guard !catalog.contains(where: { $0.id != record.id && $0.hasSameName(as: record.name) }) else {
            errorMessage = "An activity with this name already exists. Edit it in Activities instead."
            return false
        }
        guard record.weekdays.isSubset(of: Set(1...7)) else { return false }
        if case .flexible(let duration, let value, let required) = record.details {
            record.details = .flexible(duration: duration, value: required ? nil : value, doToday: required)
        }
        do { _ = try makeEngineActivity(record) }
        catch { errorMessage = error.localizedDescription; return false }
        let targetDate = date ?? today
        if record.oneTimeDay.isEmpty { record.oneTimeDay = dayKey(targetDate) }
        let previous = catalog.first { $0.id == record.id }
        // Older recurring records have no start date; editing them must not
        // unexpectedly hide their earlier occurrences.
        if !record.weekdays.isEmpty && record.recurrenceStart == nil && (previous == nil || previous!.weekdays.isEmpty) {
            record.recurrenceStart = calendar.startOfDay(for: targetDate)
        }
        if let index = catalog.firstIndex(where: { $0.id == record.id }) {
            catalog[index] = record
        } else {
            catalog.append(record)
        }
        rebuildSchedule()
        save()
        return true
    }

    func deleteActivity(id: UUID) {
        refreshIfNeeded()
        catalog.removeAll { $0.id == id }
        for day in Array(dayPlans.keys) {
            dayPlans[day]?.selections.removeValue(forKey: id)
            dayPlans[day]?.completed.remove(id)
            dayPlans[day]?.overrides.removeValue(forKey: id)
        }
        rebuildSchedule()
        save()
    }

    func moveActivities(from offsets: IndexSet, to destination: Int) {
        var ordered = orderedCatalog
        let moved = offsets.sorted().map { ordered[$0] }
        for index in offsets.sorted(by: >) { ordered.remove(at: index) }
        ordered.insert(contentsOf: moved, at: destination - offsets.filter { $0 < destination }.count)
        catalog = ordered
        customOrder = true
        save()
    }

    func useAlphabeticalOrder() { customOrder = false; save() }

    func dayKey(_ date: Date) -> String { ActivityRecord.dayKey(date, calendar: calendar) }

    func toggleSelection(of record: ActivityRecord, on date: Date? = nil) {
        refreshIfNeeded()
        let date = date ?? today
        guard let saved = catalog.first(where: { $0.id == record.id }) else { return }
        let selected = isSelected(saved, on: date)
        dayPlans[dayKey(date), default: DayPlan()].selections[saved.id] = !selected
        rebuildSchedule()
        recordProgress(for: date)
        save()
    }

    func isSelected(_ record: ActivityRecord, on date: Date? = nil) -> Bool {
        let date = date ?? today
        return dayPlans[dayKey(date)]?.selections[record.id] ?? record.occurs(on: date, calendar: calendar)
    }

    func hasActivities(on date: Date) -> Bool {
        catalog.contains { isSelected($0, on: date) }
    }

    func markComplete(name: String, on date: Date? = nil) {
        refreshIfNeeded()
        let date = date ?? today
        let snapshot = schedule(for: date)
        guard let entry = snapshot.eligible.first(where: { $0.record.name == name }),
              snapshot.schedule?.intervals.contains(where: { $0.activities.contains(where: { $0.name == name }) }) == true
        else { return }
        let key = dayKey(date)
        if dayPlans[key]?.completed.contains(entry.id) == true {
            dayPlans[key]?.completed.remove(entry.id)
        } else {
            dayPlans[key, default: DayPlan()].completed.insert(entry.id)
        }
        if let history = progressHistory[key], history.scheduledIDs.contains(entry.id), calendar.startOfDay(for: date) < calendar.startOfDay(for: now()) {
            var finished = history.completedIDs
            if dayPlans[key]?.completed.contains(entry.id) == true { finished.insert(entry.id) }
            else { finished.remove(entry.id) }
            progressHistory[key] = DailyProgress(id: key, date: history.date, scheduledIDs: history.scheduledIDs,
                                                 completedIDs: finished, hasConflict: history.hasConflict)
        } else { recordProgress(for: date) }
        rebuildSchedule()
        save()
    }

    func isCompleted(name: String) -> Bool {
        eligible.contains { $0.record.name == name && $0.completed }
    }

    /// A date-scoped edit preserves the library record and all other occurrences.
    @discardableResult
    func updateOccurrence(_ input: ActivityRecord, on date: Date) -> Bool {
        refreshIfNeeded()
        guard canSave, catalog.contains(where: { $0.id == input.id }) else { return false }
        var record = input
        record.name = record.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !record.name.isEmpty else { errorMessage = "Enter an activity name."; return false }
        let snapshot = schedule(for: date)
        guard !snapshot.eligible.contains(where: { $0.id != record.id && $0.record.hasSameName(as: record.name) }) else {
            errorMessage = "Another activity on this day already has this name."
            return false
        }
        if case .flexible(let duration, let value, let required) = record.details {
            record.details = .flexible(duration: duration, value: required ? nil : value, doToday: required)
        }
        do { _ = try makeEngineActivity(record) }
        catch { errorMessage = error.localizedDescription; return false }
        let key = dayKey(date)
        dayPlans[key, default: DayPlan()].overrides[record.id] = record
        dayPlans[key, default: DayPlan()].selections[record.id] = true
        rebuildSchedule()
        recordProgress(for: date)
        save()
        return true
    }

    func hasOverride(id: UUID, on date: Date) -> Bool { dayPlans[dayKey(date)]?.overrides[id] != nil }

    func resetOccurrence(id: UUID, on date: Date) {
        refreshIfNeeded()
        dayPlans[dayKey(date)]?.overrides.removeValue(forKey: id)
        rebuildSchedule()
        recordProgress(for: date)
        save()
    }

    @discardableResult
    func setDayHours(start: Int, end: Int, on date: Date) -> Bool {
        refreshIfNeeded()
        do {
            _ = try BangForYourBuck(startTime: TimeParser.toString(start), endTime: TimeParser.toString(end))
            dayPlans[dayKey(date), default: DayPlan()].start = start
            dayPlans[dayKey(date), default: DayPlan()].end = end
            rebuildSchedule()
            recordProgress(for: date)
            save()
            return true
        } catch { errorMessage = error.localizedDescription; return false }
    }

    func useDefaultHours(on date: Date) {
        refreshIfNeeded()
        dayPlans[dayKey(date)]?.start = nil
        dayPlans[dayKey(date)]?.end = nil
        rebuildSchedule()
        recordProgress(for: date)
        save()
    }

    /// Pure projection: browsing the calendar never changes today's state or saves previews.
    func schedule(for date: Date) -> DaySchedule {
        let plan = dayPlans[dayKey(date)] ?? DayPlan()
        let start = plan.start ?? activeStart
        let end = plan.end ?? activeEnd
        let entries = catalog.filter { isSelected($0, on: date) }
            .sorted { $0.id.uuidString < $1.id.uuidString }.map {
                EligibleActivity(record: plan.overrides[$0.id] ?? $0, completed: plan.completed.contains($0.id))
            }
        var result = DaySchedule(date: date, start: start, end: end, eligible: entries)
        do {
            let engine = try BangForYourBuck(startTime: TimeParser.toString(start), endTime: TimeParser.toString(end))
            for index in result.eligible.indices {
                do { try engine.addActivity(makeEngineActivity(result.eligible[index].record)) }
                catch { result.eligible[index].conflict = error.localizedDescription }
            }
            guard !result.eligible.contains(where: { $0.conflict != nil }) else {
                result.error = "Resolve the activities below or change this day's hours to generate its schedule."
                return result
            }
            result.schedule = try engine.generateSchedule()
        } catch { result.error = error.localizedDescription }
        return result
    }

    /// Empty/missing days stay distinct from genuine zero-percent days.
    func progressDays(count: Int) -> [ProgressDay] {
        guard count > 0 else { return [] }
        let end = calendar.startOfDay(for: now())
        return (0..<count).reversed().compactMap { offset in
            guard let date = calendar.date(byAdding: .day, value: -offset, to: end) else { return nil }
            let key = dayKey(date)
            return ProgressDay(id: key, date: date, result: progressHistory[key])
        }
    }

    /// Direct lookup is not limited to the graph's 7/30/90-day window.
    func progress(on date: Date) -> ProgressDay {
        let date = calendar.startOfDay(for: date)
        let key = dayKey(date)
        let result = date <= calendar.startOfDay(for: now()) ? progressHistory[key] : nil
        return ProgressDay(id: key, date: date, result: result)
    }

    private func recordProgress(for date: Date) {
        // Today's snapshot is recorded by rebuildSchedule; future plans aren't results.
        guard calendar.startOfDay(for: date) < calendar.startOfDay(for: now()) else { return }
        recordProgress(schedule(for: date))
    }

    private func recordProgress(_ day: DaySchedule) {
        guard calendar.startOfDay(for: day.date) <= calendar.startOfDay(for: now()) else { return }
        let names = Set(day.schedule?.intervals.flatMap { $0.activities.map(\.name) } ?? [])
        let scheduled = day.eligible.filter { names.contains($0.record.name) }
        let key = dayKey(day.date)
        progressHistory[key] = DailyProgress(id: key, date: calendar.startOfDay(for: day.date),
            scheduledIDs: Set(scheduled.map(\.id)), completedIDs: Set(scheduled.filter(\.completed).map(\.id)),
            hasConflict: day.error != nil)
    }

    private func date(forDayKey key: String) -> Date? {
        let parts = key.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 4 else { return nil }
        return calendar.date(from: DateComponents(era: parts[0], year: parts[1], month: parts[2], day: parts[3]))
    }

    @discardableResult
    func saveGoal(_ goal: PersonalGoal) -> Bool {
        guard canSave else { return false }
        var goal = goal
        goal.title = goal.title.trimmingCharacters(in: .whitespacesAndNewlines)
        goal.notes = goal.notes.trimmingCharacters(in: .whitespacesAndNewlines)
        goal.incentive = goal.incentive.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !goal.title.isEmpty else { return false }
        if let target = goal.target, target.endExclusive(calendar: calendar) == nil { return false }
        if let index = goals.firstIndex(where: { $0.id == goal.id }) {
            goals[index] = goal
        } else {
            goals.append(goal)
        }
        save()
        return true
    }

    func goalProgress(for goal: PersonalGoal) -> GoalProgress? {
        guard let target = goal.target else { return nil }
        return GoalProgress(target: target, history: Array(progressHistory.values), now: now(), calendar: calendar,
                            remainingOccurrences: projectedGoalOccurrences(target))
    }

    func goalActivityNames(_ target: ProductivityTarget) -> [String] {
        guard let ids = target.activityIDs else { return ["All activities"] }
        return ids.map { id in
            catalog.first { $0.id == id }?.name ?? "\(target.activityNames?[id] ?? "Deleted activity") (deleted)"
        }.sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    private func projectedGoalOccurrences(_ target: ProductivityTarget) -> Int {
        guard let end = target.endExclusive(calendar: calendar) else { return 0 }
        let today = calendar.startOfDay(for: now())
        let start = max(calendar.startOfDay(for: target.startDate), today)
        guard start < end else { return 0 }
        let activities = catalog.filter { target.activityIDs?.contains($0.id) ?? true }
        // Today uses the actual generated schedule. Future counts are planned
        // occurrences, not optimizer promises; edits automatically update the estimate.
        var count = 0
        var futureStart = start
        if start == today {
            let record = progressHistory[dayKey(today)]
            if let record, !record.hasConflict {
                count = target.activityIDs.map { record.scheduledIDs.intersection($0).count } ?? record.scheduledCount
            }
            futureStart = calendar.date(byAdding: .day, value: 1, to: today)!
        }
        guard futureStart < end else { return count }
        for activity in activities {
            if activity.weekdays.isEmpty {
                if let date = date(forDayKey: activity.oneTimeDay), date >= futureStart && date < end { count += 1 }
            } else {
                let lower = max(futureStart, activity.recurrenceStart.map { calendar.startOfDay(for: $0) } ?? futureStart)
                if lower < end {
                    let days = calendar.dateComponents([.day], from: lower, to: end).day!
                    count += (days / 7) * activity.weekdays.count
                    let weekday = calendar.component(.weekday, from: lower)
                    for offset in 0..<(days % 7) {
                        if activity.weekdays.contains((weekday - 1 + offset) % 7 + 1) { count += 1 }
                    }
                }
            }
        }
        // Apply explicit include/skip decisions without iterating every future day.
        for (key, plan) in dayPlans {
            guard let date = date(forDayKey: key), date >= futureStart && date < end else { continue }
            for activity in activities {
                if let selected = plan.selections[activity.id] {
                    count += (selected ? 1 : 0) - (activity.occurs(on: date, calendar: calendar) ? 1 : 0)
                }
            }
        }
        return max(0, count)
    }

    func toggleGoalCompletion(id: UUID) {
        guard canSave, let index = goals.firstIndex(where: { $0.id == id }) else { return }
        goals[index].isCompleted.toggle()
        save()
    }

    func deleteGoal(id: UUID) {
        guard canSave else { return }
        goals.removeAll { $0.id == id }
        save()
    }

    func setUsername(_ name: String) {
        username = name.trimmingCharacters(in: .whitespacesAndNewlines)
        save()
    }

    enum CurrentScheduleStatus: Equatable {
        case activity(name: String, start: String, end: String, completed: Bool)
        case freeTime(until: String)
        case beforeDay(starts: String)
        case dayEnded
        case conflict(String)
    }

    /// Uses the cached schedule during the day, so the live clock never reruns
    /// the optimizer every second. A new date is projected until the day refresh fires.
    func currentScheduleStatus(at date: Date) -> CurrentScheduleStatus {
        let day: DaySchedule
        if dayKey(date) == currentDay {
            let plan = dayPlans[currentDay]
            day = DaySchedule(date: date, start: plan?.start ?? activeStart,
                              end: plan?.end ?? activeEnd, eligible: eligible,
                              schedule: schedule, error: scheduleError)
        } else { day = schedule(for: date) }
        let components = calendar.dateComponents([.hour, .minute], from: date)
        let minute = (components.hour ?? 0) * 60 + (components.minute ?? 0)
        if minute < day.start { return .beforeDay(starts: TimeParser.toString(day.start)) }
        if minute >= day.end { return .dayEnded }
        if let error = day.error { return .conflict(error) }
        guard let interval = day.schedule?.intervals.first(where: {
            guard let start = try? TimeParser.parse($0.start), let end = try? TimeParser.parse($0.end) else { return false }
            return minute >= start && minute < end
        }) else { return .freeTime(until: TimeParser.toString(day.end)) }
        guard let activity = interval.activities.first else { return .freeTime(until: interval.end) }
        return .activity(name: activity.name, start: interval.start, end: interval.end,
                         completed: day.eligible.first { $0.record.name == activity.name }?.completed ?? false)
    }

    private func rebuildSchedule() {
        let snapshot = schedule(for: today)
        eligible = snapshot.eligible
        schedule = snapshot.schedule
        scheduleError = snapshot.error
        recordProgress(snapshot)
    }

    private func makeEngineActivity(_ record: ActivityRecord) throws -> Activity {
        switch record.details {
        case .set(let start, let end):
            return try SetActivity(name: record.name, startTime: TimeParser.toString(start),
                                   endTime: TimeParser.toString(end))
        case .flexible(let duration, let value, let required):
            return try FlexibleActivity(name: record.name, durationStr: TimeParser.toString(duration),
                                        value: value, mustDo: required)
        }
    }

    private func save() {
        guard canSave else { return }
        do {
            let data = try JSONEncoder().encode(SavedState(catalog: catalog, customOrder: customOrder,
                dayStart: activeStart, dayEnd: activeEnd, day: currentDay,
                selections: dayPlans[currentDay]?.selections ?? [:],
                completed: dayPlans[currentDay]?.completed ?? [], dayPlans: dayPlans, username: username, goals: goals, progressHistory: progressHistory))
            defaults.set(data, forKey: storageKey)
        } catch { errorMessage = "Your changes could not be saved: \(error.localizedDescription)" }
    }
}
