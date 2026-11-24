import Foundation

struct ScheduleResult {
    let intervals: [ScheduledInterval]
    let totalValue: Int // Sum of optional activities' priority values only.

    struct ScheduledInterval: Identifiable {
        var id: String { "\(start)-\(end)" }
        let start: String
        let end: String
        let durationMinutes: Int
        let activities: [ScheduledActivity]
    }

    struct ScheduledActivity: Identifiable {
        var id: String { name }
        let name: String
        let durationMinutes: Int
        let value: Int?
        var isFixed = false
    }
}
