import SwiftUI

// MARK: - Local activity record (display only — engine holds the real objects)

struct LocalActivity: Identifiable {
    let id = UUID()
    let name: String
    let type: Kind
    let detail: String
    let completed: Bool

    enum Kind { case set, flexible }
}

// MARK: - App view model

@MainActor
final class AppViewModel: ObservableObject {

    @Published var dayStartTime = ""
    @Published var dayEndTime   = ""
    @Published var dayActive    = false

    @Published var activities: [LocalActivity] = []
    @Published var schedule: ScheduleResult?
    @Published var effectiveness: Double?

    @Published var errorMessage: String?

    private var engine: BangForYourBuck?

    // MARK: Actions

    func startDay() {
        run {
            let e = try BangForYourBuck(startTime: self.dayStartTime, endTime: self.dayEndTime)
            self.engine      = e
            self.activities  = []
            self.schedule    = nil
            self.effectiveness = nil
            self.dayActive   = true
        }
    }

    func addSetActivity(name: String, start: String, end: String) {
        run {
            guard let engine else { return }
            try engine.addActivity(SetActivity(name: name, startTime: start, endTime: end))
            self.activities.append(LocalActivity(
                name: name, type: .set,
                detail: "\(start) – \(end)", completed: false))
        }
    }

    func addFlexActivity(name: String, duration: String, value: Int, doToday: Bool) {
        run {
            guard let engine else { return }
            let flex = try FlexibleActivity(name: name, durationStr: duration, value: value)
            if doToday { flex.doToday() }
            try engine.addActivity(flex)
            let tag = doToday ? "  •  must do today" : ""
            self.activities.append(LocalActivity(
                name: name, type: .flexible,
                detail: "\(duration)  •  priority \(value)\(tag)", completed: false))
        }
    }

    func generateSchedule() {
        run {
            guard let engine else { return }
            self.schedule = try engine.generateSchedule()
        }
    }

    func markComplete(name: String) {
        run {
            guard let engine else { return }
            try engine.completeActivity(name: name)
            self.activities = self.activities.map {
                $0.name == name
                    ? LocalActivity(name: $0.name, type: $0.type, detail: $0.detail, completed: true)
                    : $0
            }
            self.effectiveness = engine.getDayEffectiveness()
        }
    }

    // MARK: Private

    private func run(_ block: () throws -> Void) {
        do { try block() } catch { errorMessage = error.localizedDescription }
    }
}

// MARK: - Root view

struct ContentView: View {
    @StateObject private var vm = AppViewModel()

    var body: some View {
        TabView {
            DaySetupView(vm: vm)
                .tabItem { Label("Day", systemImage: "calendar") }

            ActivitiesView(vm: vm)
                .tabItem { Label("Activities", systemImage: "list.bullet") }

            ScheduleView(vm: vm)
                .tabItem { Label("Schedule", systemImage: "clock") }
        }
        .alert("Error", isPresented: Binding(
            get: { vm.errorMessage != nil },
            set: { if !$0 { vm.errorMessage = nil } }
        )) {
            Button("OK") { vm.errorMessage = nil }
        } message: {
            Text(vm.errorMessage ?? "")
        }
    }
}
