import Foundation

struct Interval: Hashable {
    let start: Int        // minutes since midnight
    let end: Int
    let duration: Int
    let startStr: String
    let endStr: String

    init(startStr: String, endStr: String) throws {
        self.startStr = startStr
        self.endStr   = endStr
        self.start    = try TimeParser.parse(startStr)
        self.end      = try TimeParser.parse(endStr)
        guard self.end > self.start else {
            throw IntervalError.endBeforeStart
        }
        guard self.start % 5 == 0, self.end % 5 == 0 else {
            throw IntervalError.notOnFiveMinuteBoundary
        }
        self.duration = self.end - self.start
    }

    enum IntervalError: LocalizedError {
        case endBeforeStart, notOnFiveMinuteBoundary
        var errorDescription: String? {
            switch self {
            case .endBeforeStart:          return "End time must be later than start time"
            case .notOnFiveMinuteBoundary: return "Times must be on a 5-minute boundary (e.g. 9:00, 9:05)"
            }
        }
    }
}
