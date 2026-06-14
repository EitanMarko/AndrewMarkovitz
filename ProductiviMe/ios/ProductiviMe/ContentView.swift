import SwiftUI

// MARK: - Local activity record (for display — the backend has no "list all" endpoint)

struct LocalActivity: Identifiable {
    let id = UUID()
    let name: String
    let type: Kind
    let detail: String   // human-readable summary line

    enum Kind { case set, flexible }
}

// MARK: - App-wide view model

@MainActor
final class AppViewModel: ObservableObject {

    // Day
    @Published var dayStartTime = ""
    @Published var dayEndTime   = ""
    @Published var dayActive    = false

    // Activities (local display copy)
    @Published var activities: [LocalActivity] = []

    // Schedule
    @Published var schedule: ScheduleResult?

    // UI state
    @Published var errorMessage: String?
    @Published var isLoading    = false

    private let network = NetworkManager.shared

    // MARK: Actions

    func startDay() async {
        await run {
            try await network.startDay(startTime: dayStartTime, endTime: dayEndTime)
            activities = []
            schedule   = nil
            dayActive  = true
        }
    }

    func addSetActivity(name: String, start: String, end: String) async {
        await run {
            try await network.addSetActivity(name: name, startTime: start, endTime: end)
            activities.append(LocalActivity(
                name: name, type: .set,
                detail: "\(start) – \(end)"))
        }
    }

    func addFlexActivity(name: String, duration: String, value: Int, doToday: Bool) async {
        await run {
            try await network.addFlexActivity(
                name: name, duration: duration, value: value, doToday: doToday)
            let tag = doToday ? "  •  must do today" : ""
            activities.append(LocalActivity(
                name: name, type: .flexible,
                detail: "\(duration)  •  priority \(value)\(tag)"))
        }
    }

    func generateSchedule() async {
        await run {
            schedule = try await network.generateSchedule()
        }
    }

    // MARK: Private

    private func run(_ block: () async throws -> Void) async {
        isLoading = true
        defer { isLoading = false }
        do {
            try await block()
        } catch {
            errorMessage = error.localizedDescription
        }
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
