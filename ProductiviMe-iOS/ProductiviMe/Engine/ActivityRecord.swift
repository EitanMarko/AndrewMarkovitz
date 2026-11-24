import Foundation

/// The saved library is independent of any day's selections or completions.
struct ActivityRecord: Identifiable, Codable {
    var id = UUID()
    var name: String
    var details: Details
    var weekdays: Set<Int> = [] // Calendar weekday: Sunday = 1, Saturday = 7.
    var oneTimeDay: String = ""
    var recurrenceStart: Date? = nil

    enum Details: Codable {
        case set(start: Int, end: Int)
        case flexible(duration: Int, value: Int?, doToday: Bool)
    }

    enum Kind { case set, flexible }

    var kind: Kind {
        if case .set = details { return .set }
        return .flexible
    }

    var detailText: String {
        switch details {
        case .set(let start, let end):
            return "\(TimeParser.toString(start)) – \(TimeParser.toString(end))"
        case .flexible(let duration, let value, let required):
            let description = required ? "Required" : "Priority \(value ?? 5)"
            return "\(duration) min • \(description)"
        }
    }

    var recurrenceText: String {
        if weekdays.isEmpty { return "Does not repeat" }
        if weekdays.count == 7 { return "Every day" }
        if weekdays == Set(2...6) { return "Weekdays" }
        if weekdays == [1, 7] { return "Weekends" }
        return weekdays.sorted().map { Calendar.current.shortWeekdaySymbols[$0 - 1] }.joined(separator: ", ")
    }

    func occurs(on date: Date, calendar: Calendar) -> Bool {
        if weekdays.isEmpty { return oneTimeDay == Self.dayKey(date, calendar: calendar) }
        if let recurrenceStart, calendar.startOfDay(for: date) < calendar.startOfDay(for: recurrenceStart) { return false }
        return weekdays.contains(calendar.component(.weekday, from: date))
    }

    static func dayKey(_ date: Date, calendar: Calendar) -> String {
        let parts = calendar.dateComponents([.era, .year, .month, .day], from: date)
        return "\(parts.era ?? 0)-\(parts.year ?? 0)-\(parts.month ?? 0)-\(parts.day ?? 0)"
    }

    func hasSameName(as other: String) -> Bool {
        name.caseInsensitiveCompare(other) == .orderedSame
    }
}

struct EligibleActivity: Identifiable {
    let record: ActivityRecord
    var conflict: String?
    var completed = false
    var id: UUID { record.id }
}
