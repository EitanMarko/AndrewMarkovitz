import Foundation

struct PersonalGoal: Identifiable, Codable, Equatable {
    var id = UUID()
    var title: String
    var notes = ""
    var incentive = ""
    // Retained for decoding goals created before productivity targets existed.
    var isCompleted = false
    var target: ProductivityTarget?
}

struct ProductivityTarget: Codable, Equatable {
    enum Unit: String, Codable, CaseIterable {
        case days, weeks, months, years
        var component: Calendar.Component {
            switch self {
            case .days: return .day
            case .weeks: return .weekOfYear
            case .months: return .month
            case .years: return .year
            }
        }
    }
    var percentage: Double = 80
    var startDate: Date = Date()
    var amount: Int = 1
    var unit: Unit = .weeks
    // nil means all activities, including ones created after this goal.
    var activityIDs: Set<UUID>?
    var activityNames: [UUID: String]?


    func endExclusive(calendar: Calendar) -> Date? {
        guard activityIDs?.isEmpty != true, percentage.isFinite, (1...100).contains(percentage), (1...1000).contains(amount) else { return nil }
        return calendar.date(byAdding: unit.component, value: amount, to: calendar.startOfDay(for: startDate))
    }
}

/// Counts scheduled occurrences, filtered by stable activity IDs. Today remains
/// adjustable, so the required rate covers all of today's and future occurrences.
struct GoalProgress {
    let target: ProductivityTarget
    let start: Date
    let endExclusive: Date
    let lastDay: Date
    let totalDays: Int
    let remainingDays: Int
    let missingPastDays: Int
    let records: [DailyProgress]
    let average: Double?
    let requiredAverage: Double?
    let scheduledCount: Int
    let completedCount: Int
    let remainingOccurrences: Int
    let requiredCompletions: Int
    let hasStarted: Bool
    let hasEnded: Bool

    var isAchieved: Bool { hasEnded && (average.map { $0 + 0.000001 >= target.percentage } ?? false) }
    var isImpossible: Bool { !hasEnded && requiredCompletions > remainingOccurrences }

    init?(target: ProductivityTarget, history: [DailyProgress], now: Date, calendar: Calendar, remainingOccurrences: Int = 0) {
        guard let end = target.endExclusive(calendar: calendar) else { return nil }
        let start = calendar.startOfDay(for: target.startDate)
        let today = calendar.startOfDay(for: now)
        guard end > start else { return nil }
        self.target = target
        self.start = start
        endExclusive = end
        lastDay = calendar.date(byAdding: .day, value: -1, to: end)!
        totalDays = calendar.dateComponents([.day], from: start, to: end).day!
        hasStarted = today >= start
        hasEnded = today >= end
        let remainingStart = min(end, max(start, today))
        remainingDays = calendar.dateComponents([.day], from: remainingStart, to: end).day!
        let inPeriod = history.filter {
            let day = calendar.startOfDay(for: $0.date)
            return day >= start && day < end && day <= today
        }
        records = inPeriod.compactMap { record in
            let ids = target.activityIDs.map { record.scheduledIDs.intersection($0) } ?? record.scheduledIDs
            guard !record.hasConflict, !ids.isEmpty else { return nil }
            return DailyProgress(id: record.id, date: record.date, scheduledIDs: ids,
                                 completedIDs: record.completedIDs.intersection(ids), hasConflict: false)
        }.sorted { $0.date < $1.date }
        let past = records.filter { calendar.startOfDay(for: $0.date) < today }
        // A recorded rest day is valid history, not a missing or failed occurrence.
        let validPastDays = Set(inPeriod.filter { !$0.hasConflict && calendar.startOfDay(for: $0.date) < today }
            .map { calendar.startOfDay(for: $0.date) }).count
        missingPastDays = max(0, totalDays - remainingDays - validPastDays)
        scheduledCount = records.reduce(0) { $0 + $1.scheduledCount }
        completedCount = records.reduce(0) { $0 + $1.completedCount }
        average = scheduledCount > 0 ? Double(completedCount) / Double(scheduledCount) * 100 : nil
        self.remainingOccurrences = hasEnded ? 0 : max(0, remainingOccurrences)
        let pastScheduled = past.reduce(0) { $0 + $1.scheduledCount }
        let pastCompleted = past.reduce(0) { $0 + $1.completedCount }
        let projectedTotal = pastScheduled + self.remainingOccurrences
        requiredCompletions = max(0, Int(ceil(target.percentage / 100 * Double(projectedTotal) - 0.0000001)) - pastCompleted)
        requiredAverage = self.remainingOccurrences > 0 ? Double(requiredCompletions) / Double(self.remainingOccurrences) * 100 : nil
    }
}
