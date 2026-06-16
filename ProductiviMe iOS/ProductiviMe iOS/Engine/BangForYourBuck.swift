import Foundation

final class BangForYourBuck {

    // MARK: - State

    private let dayStartStr: String
    private let dayEndStr: String
    private let dayStart: Int   // minutes since midnight
    private let dayEnd: Int

    private var setActivities: [SetActivity]       = []
    private var flexActivities: [FlexibleActivity] = []
    private var filledIntervals: [Interval]        = []
    private var activityNames: Set<String>         = []
    private var activityMap: [String: Activity]    = [:]

    // MARK: - Init

    init(startTime: String, endTime: String) throws {
        dayStartStr = startTime
        dayEndStr   = endTime
        dayStart    = try TimeParser.parse(startTime)
        dayEnd      = try TimeParser.parse(endTime)
        guard dayEnd > dayStart else { throw BFYBError.endBeforeStart }
        guard dayStart % 5 == 0, dayEnd % 5 == 0 else { throw BFYBError.notOnFiveMinuteBoundary }
    }

    // MARK: - Add Activity

    func addActivity(_ activity: Activity) throws {
        guard !activityNames.contains(activity.name) else {
            throw BFYBError.duplicateName(activity.name)
        }
        if let set = activity as? SetActivity {
            try validIntervalCheck(set)
            try overlapCheck(set)
            setActivities.append(set)
            filledIntervals.append(set.interval)
        } else if let flex = activity as? FlexibleActivity {
            let maxFlex = (dayEnd - dayStart) / 5
            guard flexActivities.count < maxFlex else { throw BFYBError.tooManyFlexActivities(maxFlex) }
            guard flex.durationMinutes <= (dayEnd - dayStart) else { throw BFYBError.activityTooLong(flex.name) }
            flexActivities.append(flex)
        }
        activityNames.insert(activity.name)
        activityMap[activity.name] = activity
    }

    private func validIntervalCheck(_ a: SetActivity) throws {
        guard a.interval.start >= dayStart, a.interval.end <= dayEnd else {
            throw BFYBError.setActivityOutsideDay
        }
    }

    private func overlapCheck(_ a: SetActivity) throws {
        for interval in filledIntervals {
            let s = a.interval.start, e = a.interval.end
            if (s >= interval.start && s < interval.end) ||
               (e > interval.start && e <= interval.end) {
                throw BFYBError.overlappingSetActivity
            }
        }
    }

    // MARK: - Schedule Generation

    func generateSchedule() throws -> ScheduleResult {
        let sortedFilled = filledIntervals.sorted { $0.start < $1.start }

        // Build free intervals (gaps between set activities)
        var free: [Interval] = []
        if sortedFilled.isEmpty {
            free.append(try Interval(startStr: dayStartStr, endStr: dayEndStr))
        } else {
            if sortedFilled[0].start > dayStart {
                free.append(try Interval(startStr: dayStartStr, endStr: sortedFilled[0].startStr))
            }
            for i in 1..<sortedFilled.count {
                let prev = sortedFilled[i - 1], curr = sortedFilled[i]
                if curr.start > prev.end {
                    free.append(try Interval(startStr: prev.endStr, endStr: curr.startStr))
                }
            }
            let last = sortedFilled[sortedFilled.count - 1]
            if last.end < dayEnd {
                free.append(try Interval(startStr: last.endStr, endStr: dayEndStr))
            }
        }

        guard !flexActivities.isEmpty else {
            return ScheduleResult(
                intervals: free.map { ScheduleResult.ScheduledInterval(
                    start: $0.startStr, end: $0.endStr, durationMinutes: $0.duration, activities: []) },
                totalValue: 0)
        }

        let N = flexActivities.count
        let K = free.count

        if N > 20 { return generateScheduleGreedy(freeIntervals: free) }

        // Precompute subset totals — both arrays built bottom-up in O(2^N)
        var totalDur = [Int](repeating: 0, count: 1 << N)
        var totalVal = [Int](repeating: 0, count: 1 << N)
        for mask in 1..<(1 << N) {
            let lsb  = mask.trailingZeroBitCount
            let rest = mask ^ (1 << lsb)
            totalDur[mask] = totalDur[rest] + flexActivities[lsb].durationMinutes
            totalVal[mask] = totalVal[rest] + flexActivities[lsb].schedulingValue
        }

        // Bitmask DP — dp[mask] = max total scheduling value using exactly the activities in mask.
        // -1 means infeasible. Complexity: O(K * 3^N).
        var dp = [Int](repeating: -1, count: 1 << N)
        dp[0] = 0
        // prevMask[j][newMask] = dp state before interval j contributed to newMask (for backtracking).
        var prevMask = [[Int]](repeating: [Int](repeating: -1, count: 1 << N), count: K)

        for j in 0..<K {
            let cap = free[j].duration
            var newDp = dp
            for used in 0..<(1 << N) {
                guard dp[used] >= 0 else { continue }
                let available = ((1 << N) - 1) & ~used
                var sub = available
                while sub > 0 {
                    if totalDur[sub] <= cap {
                        let newMask = used | sub
                        let newVal  = dp[used] + totalVal[sub]
                        if newVal > newDp[newMask] {
                            newDp[newMask] = newVal
                            prevMask[j][newMask] = used
                        }
                    }
                    sub = (sub - 1) & available
                }
            }
            dp = newDp
        }

        // Find the globally optimal mask
        var bestMask = 0
        for mask in 1..<(1 << N) {
            if dp[mask] > dp[bestMask] { bestMask = mask }
        }

        // Backtrack to recover which activities go in which interval
        var scheduleByInterval = [Interval: [FlexibleActivity]]()
        var currentMask = bestMask
        for j in stride(from: K - 1, through: 0, by: -1) {
            var assigned = [FlexibleActivity]()
            let before = prevMask[j][currentMask]
            if before >= 0 {
                let subset = currentMask ^ before
                for i in 0..<N where (subset & (1 << i)) != 0 {
                    assigned.append(flexActivities[i])
                }
                currentMask = before
            }
            scheduleByInterval[free[j]] = assigned
        }

        // Build result — use scoringValue for display so doToday shows 1–10, not 3000
        var totalValue = 0
        var resultIntervals = [ScheduleResult.ScheduledInterval]()
        for interval in free {
            let assigned = scheduleByInterval[interval] ?? []
            var resultActivities = [ScheduleResult.ScheduledActivity]()
            for a in assigned {
                totalValue += a.scoringValue
                resultActivities.append(ScheduleResult.ScheduledActivity(
                    name: a.name, durationMinutes: a.durationMinutes, value: a.scoringValue))
            }
            resultIntervals.append(ScheduleResult.ScheduledInterval(
                start: interval.startStr, end: interval.endStr,
                durationMinutes: interval.duration, activities: resultActivities))
        }
        return ScheduleResult(intervals: resultIntervals, totalValue: totalValue)
    }

    // MARK: - Greedy Fallback (N > 20)

    private func generateScheduleGreedy(freeIntervals: [Interval]) -> ScheduleResult {
        let sorted = freeIntervals.sorted { $0.duration < $1.duration }
        var used = [Bool](repeating: false, count: flexActivities.count)
        var scheduleByInterval = [Interval: [FlexibleActivity]]()

        for interval in sorted {
            let cap = interval.duration
            let avail = flexActivities.enumerated().filter { !used[$0.offset] }
            guard !avail.isEmpty else { scheduleByInterval[interval] = []; continue }
            let m = avail.count
            var dpTable = [[Int]](repeating: [Int](repeating: 0, count: cap + 1), count: m + 1)
            for i in 1...m {
                let w = avail[i-1].element.durationMinutes
                let v = avail[i-1].element.schedulingValue
                for c in 0...cap {
                    dpTable[i][c] = dpTable[i-1][c]
                    if w <= c { dpTable[i][c] = max(dpTable[i][c], dpTable[i-1][c-w] + v) }
                }
            }
            var selected = [FlexibleActivity]()
            var c = cap
            for i in stride(from: m, through: 1, by: -1) {
                guard c > 0, dpTable[i][c] != dpTable[i-1][c] else { continue }
                selected.append(avail[i-1].element)
                used[avail[i-1].offset] = true
                c -= avail[i-1].element.durationMinutes
            }
            scheduleByInterval[interval] = selected
        }

        var totalValue = 0
        var resultIntervals = [ScheduleResult.ScheduledInterval]()
        for interval in freeIntervals {
            let assigned = scheduleByInterval[interval] ?? []
            var resultActivities = [ScheduleResult.ScheduledActivity]()
            for a in assigned {
                totalValue += a.scoringValue
                resultActivities.append(ScheduleResult.ScheduledActivity(
                    name: a.name, durationMinutes: a.durationMinutes, value: a.scoringValue))
            }
            resultIntervals.append(ScheduleResult.ScheduledInterval(
                start: interval.startStr, end: interval.endStr,
                durationMinutes: interval.duration, activities: resultActivities))
        }
        return ScheduleResult(intervals: resultIntervals, totalValue: totalValue)
    }

    // MARK: - Completion Tracking

    func completeActivity(name: String) throws {
        guard let a = activityMap[name] else { throw BFYBError.activityNotFound(name) }
        a.markComplete()
    }

    func isActivityCompleted(name: String) throws -> Bool {
        guard let a = activityMap[name] else { throw BFYBError.activityNotFound(name) }
        return a.completed
    }

    // MARK: - Day Effectiveness

    /// Returns a value in [0.0, 1.0]: sum of scoring values of completed activities
    /// divided by sum of all scoring values. Uses scoringValue (1–10), never the 3000 sentinel.
    func getDayEffectiveness() -> Double {
        var total = 0, done = 0
        for a in activityMap.values {
            total += a.scoringValue
            if a.completed { done += a.scoringValue }
        }
        return total == 0 ? 0 : Double(done) / Double(total)
    }

    var allActivities: [Activity] { Array(activityMap.values) }

    // MARK: - Errors

    enum BFYBError: LocalizedError {
        case endBeforeStart, notOnFiveMinuteBoundary
        case duplicateName(String), tooManyFlexActivities(Int), activityTooLong(String)
        case setActivityOutsideDay, overlappingSetActivity, activityNotFound(String)

        var errorDescription: String? {
            switch self {
            case .endBeforeStart:              return "Day end time must be after start time"
            case .notOnFiveMinuteBoundary:     return "Day start/end must be on a 5-minute boundary"
            case .duplicateName(let n):        return "Activity name already exists: \"\(n)\""
            case .tooManyFlexActivities(let m): return "Maximum of \(m) flexible activities reached"
            case .activityTooLong(let n):      return "Activity \"\(n)\" is longer than the day"
            case .setActivityOutsideDay:       return "Set activity must be within day hours"
            case .overlappingSetActivity:      return "This activity overlaps with an existing one"
            case .activityNotFound(let n):     return "No activity found: \"\(n)\""
            }
        }
    }
}
