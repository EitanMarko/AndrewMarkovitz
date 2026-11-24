import Foundation

final class BangForYourBuck {
    private let dayStart: Int
    private let dayEnd: Int
    private var setActivities: [SetActivity] = []
    private var flexActivities: [FlexibleActivity] = []
    private var activityMap: [String: Activity] = [:]

    init(startTime: String, endTime: String) throws {
        dayStart = try TimeParser.parse(startTime)
        dayEnd = try TimeParser.parse(endTime)
        guard dayEnd > dayStart else { throw BFYBError.endBeforeStart }
        guard dayStart % 5 == 0, dayEnd % 5 == 0 else { throw BFYBError.notOnFiveMinuteBoundary }
    }

    func addActivity(_ activity: Activity) throws {
        guard activityMap[activity.name] == nil else { throw BFYBError.duplicateName(activity.name) }
        if let fixed = activity as? SetActivity {
            guard fixed.interval.start >= dayStart, fixed.interval.end <= dayEnd else {
                throw BFYBError.setActivityOutsideDay
            }
            guard !setActivities.contains(where: {
                fixed.interval.start < $0.interval.end && fixed.interval.end > $0.interval.start
            }) else { throw BFYBError.overlappingSetActivity }
            setActivities.append(fixed)
        } else if let flex = activity as? FlexibleActivity {
            guard flex.durationMinutes <= dayEnd - dayStart else { throw BFYBError.activityTooLong(flex.name) }
            flexActivities.append(flex)
        }
        activityMap[activity.name] = activity
    }

    func generateSchedule() throws -> ScheduleResult {
        var free: [Interval] = []
        var cursor = dayStart
        for fixed in setActivities.sorted(by: { $0.interval.start < $1.interval.start }) {
            if fixed.interval.start > cursor {
                free.append(try Interval(startStr: TimeParser.toString(cursor), endStr: fixed.interval.startStr))
            }
            cursor = fixed.interval.end
        }
        if cursor < dayEnd {
            free.append(try Interval(startStr: TimeParser.toString(cursor), endStr: TimeParser.toString(dayEnd)))
        }
        let assignments: [[FlexibleActivity]]
        if flexActivities.count <= 14 {
            assignments = try optimalAssignments(free: free)
        } else {
            assignments = try largeAssignments(free: free)
        }
        return buildResult(free: free, assignments: assignments)
    }

    /// Exact subset optimization for small daily selections. Every feasible final
    /// state must contain the complete required mask, regardless of priority.
    private func optimalAssignments(free: [Interval]) throws -> [[FlexibleActivity]] {
        let count = flexActivities.count
        let stateCount = 1 << count
        var durations = [Int](repeating: 0, count: stateCount)
        var values = durations
        var requiredMask = 0
        for i in 0..<count where flexActivities[i].isDoToday { requiredMask |= 1 << i }
        for mask in 1..<stateCount {
            let bit = mask.trailingZeroBitCount
            let rest = mask ^ (1 << bit)
            durations[mask] = durations[rest] + flexActivities[bit].durationMinutes
            values[mask] = values[rest] + flexActivities[bit].schedulingValue
        }
        var feasible = [Bool](repeating: false, count: stateCount)
        feasible[0] = true
        var previous = [[Int]](repeating: [Int](repeating: -1, count: stateCount), count: free.count)
        for gap in free.indices {
            var next = feasible
            for used in 0..<stateCount where feasible[used] {
                var subset = (stateCount - 1) & ~used
                let available = subset
                while subset > 0 {
                    let combined = used | subset
                    if durations[subset] <= free[gap].duration && !next[combined] {
                        next[combined] = true
                        previous[gap][combined] = used
                    }
                    subset = (subset - 1) & available
                }
            }
            feasible = next
        }
        var best: Int?
        for mask in 0..<stateCount where feasible[mask] && (mask & requiredMask) == requiredMask {
            if best == nil || values[mask] > values[best!] { best = mask }
        }
        guard var mask = best else { throw BFYBError.requiredActivitiesDoNotFit }
        var assignments = [[FlexibleActivity]](repeating: [], count: free.count)
        for gap in free.indices.reversed() {
            let before = previous[gap][mask]
            if before >= 0 {
                let subset = mask ^ before
                for i in 0..<count where subset & (1 << i) != 0 { assignments[gap].append(flexActivities[i]) }
                mask = before
            }
        }
        return assignments
    }

    /// Pack all required activities first using backtracking, then optimize optional
    /// choices within each remaining gap. The fallback never drops a requirement.
    private func largeAssignments(free: [Interval]) throws -> [[FlexibleActivity]] {
        let required = flexActivities.filter(\.isDoToday).sorted { $0.durationMinutes > $1.durationMinutes }
        var capacities = free.map(\.duration)
        var assignments = [[FlexibleActivity]](repeating: [], count: free.count)
        var failed: Set<String> = []
        guard required.reduce(0, { $0 + $1.durationMinutes }) <= capacities.reduce(0, +) else {
            throw BFYBError.requiredActivitiesDoNotFit
        }
        func place(_ index: Int) -> Bool {
            if index == required.count { return true }
            let key = "\(index):" + capacities.sorted().map(String.init).joined(separator: ",")
            if failed.contains(key) { return false }
            let activity = required[index]
            var triedCapacities: Set<Int> = []
            for gap in capacities.indices.sorted(by: { capacities[$0] < capacities[$1] }) {
                guard capacities[gap] >= activity.durationMinutes,
                      triedCapacities.insert(capacities[gap]).inserted else { continue }
                capacities[gap] -= activity.durationMinutes
                assignments[gap].append(activity)
                if place(index + 1) { return true }
                assignments[gap].removeLast()
                capacities[gap] += activity.durationMinutes
            }
            failed.insert(key)
            return false
        }
        guard place(0) else { throw BFYBError.requiredActivitiesDoNotFit }
        var optional = flexActivities.filter { !$0.isDoToday }
        for gap in capacities.indices.sorted(by: { capacities[$0] < capacities[$1] }) {
            guard !optional.isEmpty, capacities[gap] > 0 else { continue }
            let capacity = capacities[gap] / 5
            var dp = [[Int]](repeating: [Int](repeating: 0, count: capacity + 1), count: optional.count + 1)
            for i in 1...optional.count {
                let weight = optional[i - 1].durationMinutes / 5
                for c in 0...capacity {
                    dp[i][c] = dp[i - 1][c]
                    if weight <= c { dp[i][c] = max(dp[i][c], dp[i - 1][c - weight] + optional[i - 1].schedulingValue) }
                }
            }
            var c = capacity
            var chosen: Set<String> = []
            for i in (1...optional.count).reversed() where dp[i][c] != dp[i - 1][c] {
                let activity = optional[i - 1]
                assignments[gap].append(activity)
                chosen.insert(activity.name)
                c -= activity.durationMinutes / 5
            }
            optional.removeAll { chosen.contains($0.name) }
        }
        return assignments
    }

    private func buildResult(free: [Interval], assignments: [[FlexibleActivity]]) -> ScheduleResult {
        var intervals = setActivities.map { fixed in
            ScheduleResult.ScheduledInterval(start: fixed.interval.startStr, end: fixed.interval.endStr,
                durationMinutes: fixed.durationMinutes, activities: [
                    .init(name: fixed.name, durationMinutes: fixed.durationMinutes, value: nil, isFixed: true)
                ])
        }
        var totalValue = 0
        for gap in free.indices {
            var cursor = free[gap].start
            for activity in assignments[gap] {
                let end = cursor + activity.durationMinutes
                intervals.append(.init(start: TimeParser.toString(cursor), end: TimeParser.toString(end),
                    durationMinutes: activity.durationMinutes, activities: [
                        .init(name: activity.name, durationMinutes: activity.durationMinutes, value: activity.priority)
                    ]))
                totalValue += activity.priority ?? 0
                cursor = end
            }
            if cursor < free[gap].end {
                intervals.append(.init(start: TimeParser.toString(cursor), end: free[gap].endStr,
                                       durationMinutes: free[gap].end - cursor, activities: []))
            }
        }
        intervals.sort { (try? TimeParser.parse($0.start)) ?? 0 < (try? TimeParser.parse($1.start)) ?? 0 }
        return ScheduleResult(intervals: intervals, totalValue: totalValue)
    }

    func completeActivity(name: String) throws {
        guard let activity = activityMap[name] else { throw BFYBError.activityNotFound(name) }
        activity.markComplete()
    }

    func isActivityCompleted(name: String) throws -> Bool {
        guard let activity = activityMap[name] else { throw BFYBError.activityNotFound(name) }
        return activity.completed
    }

    func getDayEffectiveness() -> Double {
        let total = activityMap.values.reduce(0) { $0 + $1.scoringValue }
        let done = activityMap.values.filter(\.completed).reduce(0) { $0 + $1.scoringValue }
        return total == 0 ? 0 : Double(done) / Double(total)
    }

    var allActivities: [Activity] { Array(activityMap.values) }

    enum BFYBError: LocalizedError {
        case endBeforeStart, notOnFiveMinuteBoundary
        case duplicateName(String), activityTooLong(String)
        case setActivityOutsideDay, overlappingSetActivity, activityNotFound(String)
        case requiredActivitiesDoNotFit

        var errorDescription: String? {
            switch self {
            case .endBeforeStart: return "Day end time must be after start time"
            case .notOnFiveMinuteBoundary: return "Day start/end must be on a 5-minute boundary"
            case .duplicateName(let name): return "Activity name already exists: \(name)"
            case .activityTooLong(let name): return "Activity \(name) is longer than the day"
            case .setActivityOutsideDay: return "Fixed activity must be within day hours"
            case .overlappingSetActivity: return "This activity overlaps with another fixed activity"
            case .activityNotFound(let name): return "No activity found: \(name)"
            case .requiredActivitiesDoNotFit:
                return "All required activities cannot fit around your fixed activities. Adjust durations or day hours, or skip an activity for today."
            }
        }
    }
}
