import Foundation

final class FlexibleActivity: Activity {
    let name: String
    let durationMinutes: Int
    let priority: Int?
    let isDoToday: Bool
    private(set) var completed = false

    init(name: String, durationStr: String, value: Int?, mustDo: Bool = false) throws {
        if !mustDo {
            guard let value, (1...10).contains(value) else { throw FlexError.invalidValue }
        }
        let duration = try TimeParser.parse(durationStr)
        guard duration > 0, duration % 5 == 0 else { throw FlexError.invalidDuration }
        self.name = name
        durationMinutes = duration
        priority = mustDo ? nil : value
        isDoToday = mustDo
    }

    // Required activities are constraints, not inflated priority scores.
    var schedulingValue: Int { priority ?? 0 }
    var scoringValue: Int { priority ?? 1 }
    func markComplete() { completed = true }

    enum FlexError: LocalizedError {
        case invalidValue, invalidDuration
        var errorDescription: String? {
            switch self {
            case .invalidValue: return "Priority must be between 1 and 10"
            case .invalidDuration: return "Duration must be positive and a multiple of 5 minutes"
            }
        }
    }
}
