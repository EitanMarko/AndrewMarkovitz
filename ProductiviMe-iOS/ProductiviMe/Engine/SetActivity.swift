import Foundation

final class SetActivity: Activity {
    let name: String
    let interval: Interval
    private(set) var completed = false

    init(name: String, startTime: String, endTime: String) throws {
        self.name     = name
        self.interval = try Interval(startStr: startTime, endStr: endTime)
    }

    var schedulingValue: Int { 12 }
    var scoringValue: Int    { 12 }
    var durationMinutes: Int { interval.duration }

    func markComplete() { completed = true }
}
