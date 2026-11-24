import Foundation

protocol Activity: AnyObject {
    var name: String { get }
    var schedulingValue: Int { get }
    var scoringValue: Int { get }
    var durationMinutes: Int { get }
    var completed: Bool { get }
    func markComplete()
}
