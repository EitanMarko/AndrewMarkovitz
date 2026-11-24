import SwiftUI
import Combine

enum AppTab: Hashable { case home, schedule, progress }
enum AppRoute: Hashable { case dayHours, activities, profile, calendar, goals, goal(UUID), day(Date) }

struct ContentView: View {
    @StateObject private var vm = AppViewModel()
    @Environment(\.scenePhase) private var scenePhase
    @State private var selectedTab = AppTab.home
    // Paths belong to the tab container, not to a page that can disappear.
    @State private var homePath: [AppRoute] = []
    @State private var schedulePath: [AppRoute] = []
    @State private var scheduleResetID = UUID()
    @State private var progressPath: [AppRoute] = []
    private let dayCheck = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    var body: some View {
        TabView(selection: $selectedTab) {
            tabStack(path: $homePath) { HomeView(vm: vm, onViewToday: openTodaysSchedule) }
                .tabItem { Label("Home", systemImage: "house") }.tag(AppTab.home)
            tabStack(path: $schedulePath) { ScheduleView(vm: vm, onOpenCalendar: { openCalendar(in: $schedulePath) }).id(scheduleResetID) }
                .tabItem { Label("Schedule", systemImage: "clock") }.tag(AppTab.schedule)
            tabStack(path: $progressPath) { ProductivityProgressView(vm: vm) }
                .tabItem { Label("Progress", systemImage: "chart.xyaxis.line") }.tag(AppTab.progress)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { vm.refreshToday() }
        }
        .onReceive(dayCheck) { _ in vm.refreshIfNeeded() }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
            vm.refreshToday()
        }
        .alert("Error", isPresented: Binding(
            get: { vm.errorMessage != nil },
            set: { if !$0 { vm.errorMessage = nil } }
        )) {
            Button("OK") { vm.errorMessage = nil }
        } message: { Text(vm.errorMessage ?? "") }
    }

    private func openTodaysSchedule() {
        vm.refreshIfNeeded()
        // Only the explicit Today shortcut returns to the root and resets its date.
        // Ordinary tab switches preserve each tab's navigation and page state.
        schedulePath = []
        scheduleResetID = UUID()
        selectedTab = .schedule
    }

    private func openCalendar(in path: Binding<[AppRoute]>) {
        // Return to the existing calendar to preserve its browsed month.
        if let index = path.wrappedValue.lastIndex(of: .calendar) {
            path.wrappedValue = Array(path.wrappedValue.prefix(through: index))
        } else {
            path.wrappedValue.append(.calendar)
        }
    }

    private func tabStack<Root: View>(path: Binding<[AppRoute]>, @ViewBuilder root: () -> Root) -> some View {
        NavigationStack(path: path) {
            root()
                .navigationDestination(for: AppRoute.self) { route in
                    switch route {
                    case .dayHours: DaySetupView(vm: vm)
                    case .activities: ActivitiesView(vm: vm)
                    case .profile: ProfileView(vm: vm)
                    case .goals: GoalsView(vm: vm)
                    case .goal(let id): GoalDetailView(vm: vm, goalID: id)
                    case .calendar: CalendarView(vm: vm)
                    case .day(let date):
                        DayScheduleView(vm: vm, date: date, onOpenCalendar: { openCalendar(in: path) })
                    }
                }
        }
    }
}
