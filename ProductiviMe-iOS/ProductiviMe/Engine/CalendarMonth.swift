import Foundation

/// Calendar arithmetic keeps month navigation correct across leap years and DST.
struct CalendarMonth {
    let firstDay: Date
    let cells: [Date?]

    init(containing date: Date, calendar: Calendar = .autoupdatingCurrent) {
        let first = calendar.dateInterval(of: .month, for: date)!.start
        firstDay = first
        let offset = (calendar.component(.weekday, from: firstDay) - calendar.firstWeekday + 7) % 7
        let count = calendar.range(of: .day, in: .month, for: firstDay)!.count
        var days: [Date?] = Array(repeating: nil, count: offset)
        days += (0..<count).map { calendar.date(byAdding: .day, value: $0, to: first) }
        while days.count % 7 != 0 { days.append(nil) }
        cells = days
    }
}
