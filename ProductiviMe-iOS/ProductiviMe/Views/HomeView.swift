import SwiftUI

struct HomeView: View {
    @ObservedObject var vm: AppViewModel
    let onViewToday: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                VStack(alignment: .leading, spacing: 8) {
                    Text(vm.username.isEmpty ? "Welcome" : "Welcome \(vm.username)")
                        .font(.largeTitle.bold())
                        .accessibilityAddTraits(.isHeader)
                    if vm.username.isEmpty {
                        NavigationLink("Set your name", value: AppRoute.profile).font(.subheadline)
                    }
                }
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    VStack(alignment: .leading, spacing: 20) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(context.date.formatted(.dateTime.weekday(.wide).month(.wide).day()))
                                .font(.subheadline).foregroundStyle(.secondary)
                            Text(context.date, format: .dateTime.hour().minute().second())
                                .font(.system(.largeTitle, design: .rounded, weight: .medium))
                                .monospacedDigit()
                                .contentTransition(.numericText())
                        }
                        currentActivity(vm.currentScheduleStatus(at: context.date))
                    }
                }
                VStack(spacing: 12) {
                    NavigationLink(value: AppRoute.dayHours) {
                        homeLink(title: "Day Hours", subtitle: "Set the start and end of your day", icon: "sun.max", color: .orange)
                    }
                    NavigationLink(value: AppRoute.activities) {
                        homeLink(title: "Activities", subtitle: "Open your activity bank", icon: "list.bullet.rectangle", color: .blue)
                    }
                    NavigationLink(value: AppRoute.goals) {
                        homeLink(title: "Goals", subtitle: "Set goals and choose your rewards", icon: "target", color: .purple)
                    }
                    NavigationLink(value: AppRoute.calendar) {
                        homeLink(title: "Calendar", subtitle: "Browse and plan your upcoming days", icon: "calendar", color: .red)
                    }
                }
                .buttonStyle(.plain)
            }
            .padding(20)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Home")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                NavigationLink(value: AppRoute.profile) { Label("Your name", systemImage: "person.crop.circle") }
            }
        }
        .onAppear { vm.refreshIfNeeded() }
    }

    private func currentActivity(_ status: AppViewModel.CurrentScheduleStatus) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("RIGHT NOW", systemImage: "clock").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            switch status {
            case .activity(let name, let start, let end, let completed):
                Text(name).font(.title2.bold())
                Text("\(start) – \(end)").font(.subheadline).foregroundStyle(.secondary)
                if completed { Label("Completed", systemImage: "checkmark.circle.fill").font(.caption).foregroundStyle(.green) }
            case .freeTime(let end):
                Text("Free time").font(.title2.bold())
                Text("Nothing scheduled until \(end).").font(.subheadline).foregroundStyle(.secondary)
            case .beforeDay(let start):
                Text("Your day starts at \(start)").font(.title2.bold())
                Text("Enjoy the time before your day begins.").font(.subheadline).foregroundStyle(.secondary)
            case .dayEnded:
                Text("Your scheduled day is over").font(.title2.bold())
                Text("No activity scheduled right now.").font(.subheadline).foregroundStyle(.secondary)
            case .conflict(let error):
                Text("Schedule needs attention").font(.title2.bold())
                Text(error).font(.subheadline).foregroundStyle(.secondary)
            }
            Button("View Today's Schedule", action: onViewToday)
                .font(.subheadline.weight(.semibold)).padding(.top, 4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
    }

    private func homeLink(title: String, subtitle: String, icon: String, color: Color) -> some View {
        HStack(spacing: 16) {
            Image(systemName: icon).font(.title2).foregroundStyle(color)
                .frame(width: 48, height: 48).background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline)
                Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
            }
            Spacer(minLength: 4)
            Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(.tertiary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18))
    }
}

struct ProfileView: View {
    @ObservedObject var vm: AppViewModel
    @State private var name: String
    @State private var saved = false

    init(vm: AppViewModel) {
        self.vm = vm
        _name = State(initialValue: vm.username)
    }

    var body: some View {
        Form {
            Section {
                TextField("Your name", text: $name).textContentType(.givenName).submitLabel(.done)
                    .onSubmit { save() }
            } footer: { Text("This name appears in your Home greeting.") }
            Section {
                Button("Save Name") { save() }
                if saved { Label("Name saved", systemImage: "checkmark.circle.fill").foregroundStyle(.green) }
            }
        }
        .navigationTitle("Your Name")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: name) { _, _ in saved = false }
    }

    private func save() { vm.setUsername(name); saved = true }
}
