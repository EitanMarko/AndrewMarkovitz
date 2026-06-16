import Foundation

enum TimeParser {

    static func parse(_ timeStr: String) throws -> Int {
        guard let colon = timeStr.lastIndex(of: ":") else {
            throw TimeError.badFormat
        }
        guard colon != timeStr.startIndex,
              timeStr.index(after: colon) != timeStr.endIndex else {
            throw TimeError.badFormat
        }
        let hourStr = String(timeStr[..<colon])
        let minStr  = String(timeStr[timeStr.index(after: colon)...])

        guard minStr.count == 2, hourStr.count <= 2 else {
            throw TimeError.badFormat
        }
        guard let hour = Int(hourStr), let mins = Int(minStr) else {
            throw TimeError.badFormat
        }
        if hour == 24 && mins == 0 { return 24 * 60 }

        guard hour >= 0, hour <= 23 else { throw TimeError.invalidHour }
        guard mins >= 0, mins <= 59 else { throw TimeError.invalidMinute }
        return hour * 60 + mins
    }

    static func toString(_ minutes: Int) -> String {
        String(format: "%d:%02d", minutes / 60, minutes % 60)
    }

    enum TimeError: LocalizedError {
        case badFormat, invalidHour, invalidMinute
        var errorDescription: String? {
            switch self {
            case .badFormat:     return "Use format H:MM or HH:MM (e.g. 9:00 or 14:30)"
            case .invalidHour:   return "Hours must be between 0 and 23"
            case .invalidMinute: return "Minutes must be between 0 and 59"
            }
        }
    }
}
