import Foundation

/// A saved result, independent of later edits to the activity bank.
struct DailyProgress: Codable, Identifiable {
    let id: String
    let date: Date
    let scheduledIDs: Set<UUID>
    let completedIDs: Set<UUID>
    let hasConflict: Bool

    var scheduledCount: Int { scheduledIDs.count }
    var completedCount: Int { scheduledIDs.intersection(completedIDs).count }
    var percentage: Double? {
        guard !hasConflict, scheduledCount > 0 else { return nil }
        return Double(completedCount) / Double(scheduledCount) * 100
    }
}

struct ProgressDay: Identifiable {
    let id: String
    let date: Date
    let result: DailyProgress?
    var percentage: Double? { result?.percentage }
}

struct ProgressSummary {
    let completed: Int
    let scheduled: Int
    var percentage: Double? { scheduled > 0 ? Double(completed) / Double(scheduled) * 100 : nil }

    init(days: [ProgressDay]) {
        let results = days.compactMap(\.result).filter { $0.percentage != nil }
        completed = results.reduce(0) { $0 + $1.completedCount }
        scheduled = results.reduce(0) { $0 + $1.scheduledCount }
    }
}
