import Foundation

struct ScheduleResult {
    let intervals: [ScheduledInterval]
    let totalValue: Int   // sum of scoringValue (1–10) of scheduled activities

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
        let value: Int  // scoringValue (1–10 or 12), not the 3000 sentinel
    }
}
