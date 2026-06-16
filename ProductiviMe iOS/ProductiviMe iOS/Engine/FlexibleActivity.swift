import Foundation

final class FlexibleActivity: Activity {
    let name: String
    let durationMinutes: Int
    private var flexValue: Int         // user-assigned 1–10, never inflated
    private var _schedulingValue: Int  // may become 3000 after doToday()
    private(set) var isDoToday = false
    private(set) var completed = false

    init(name: String, durationStr: String, value: Int) throws {
        guard value >= 1, value <= 10 else { throw FlexError.invalidValue }
        let dur = try TimeParser.parse(durationStr)
        guard dur % 5 == 0 else { throw FlexError.durationNotMultipleOfFive }
        self.name             = name
        self.durationMinutes  = dur
        self.flexValue        = value
        self._schedulingValue = value
    }

    var schedulingValue: Int { _schedulingValue }
    var scoringValue: Int    { flexValue }  // never 3000

    func doToday() {
        _schedulingValue = 3000
        isDoToday = true
    }

    func makeFlexible() {
        _schedulingValue = flexValue
        isDoToday = false
    }

    func markComplete() { completed = true }

    enum FlexError: LocalizedError {
        case invalidValue, durationNotMultipleOfFive
        var errorDescription: String? {
            switch self {
            case .invalidValue:              return "Priority must be between 1 and 10"
            case .durationNotMultipleOfFive: return "Duration must be a multiple of 5 minutes (e.g. 0:30, 1:00)"
            }
        }
    }
}
