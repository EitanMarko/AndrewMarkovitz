import SwiftUI

struct ScheduleView: View {
    @ObservedObject var vm: AppViewModel

    var body: some View {
        NavigationStack {
            Group {
                if !vm.dayActive {
                    ContentUnavailableView(
                        "No active day",
                        systemImage: "calendar.badge.exclamationmark",
                        description: Text("Set your day hours before generating a schedule.")
                    )
                } else if let schedule = vm.schedule {
                    scheduleList(schedule)
                } else {
                    emptyPrompt
                }
            }
            .navigationTitle("Schedule")
            .toolbar {
                if vm.dayActive {
                    ToolbarItem(placement: .primaryAction) {
                        Button {
                            vm.generateSchedule()
                        } label: {
                            Label("Generate", systemImage: "wand.and.stars")
                        }
                    }
                }
            }
        }
    }

    // MARK: - Sub-views

    private var emptyPrompt: some View {
        VStack(spacing: 16) {
            Image(systemName: "calendar.badge.clock")
                .font(.system(size: 60))
                .foregroundStyle(.secondary)
            Text("No schedule yet")
                .font(.headline)
            Text("Add activities on the Activities tab, then tap the wand to generate your optimal day.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
    }

    private func scheduleList(_ result: ScheduleResult) -> some View {
        List {
            Section {
                HStack {
                    Label("Total priority value", systemImage: "star.fill")
                    Spacer()
                    Text("\(result.totalValue)")
                        .font(.headline)
                        .foregroundStyle(.accentColor)
                }
                if let eff = vm.effectiveness {
                    HStack {
                        Label("Day effectiveness", systemImage: "chart.bar.fill")
                        Spacer()
                        Text(String(format: "%.0f%%", eff * 100))
                            .font(.headline)
                            .foregroundStyle(.blue)
                    }
                }
            }

            ForEach(result.intervals) { interval in
                Section {
                    if interval.activities.isEmpty {
                        Label("Free time", systemImage: "moon.zzz")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(interval.activities) { activity in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(activity.name)
                                    Text("\(activity.durationMinutes) min")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text("★ \(activity.value)")
                                    .font(.caption)
                                    .foregroundStyle(.orange)
                            }
                        }
                    }
                } header: {
                    Text("\(interval.start) – \(interval.end)  (\(interval.durationMinutes) min)")
                }
            }
        }
    }
}
