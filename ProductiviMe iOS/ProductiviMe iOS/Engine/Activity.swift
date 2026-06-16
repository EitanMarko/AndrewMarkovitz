import Foundation

// AnyObject constraint gives reference semantics so markComplete() mutates in place.
protocol Activity: AnyObject {
    var name: String { get }
    /// Value used by the scheduler. May be 3000 for a doToday FlexibleActivity.
    var schedulingValue: Int { get }
    /// Value used for day-effectiveness scoring. Always the user-assigned 1–10 (or 12 for set).
    var scoringValue: Int { get }
    var durationMinutes: Int { get }
    var completed: Bool { get }
    func markComplete()
}
