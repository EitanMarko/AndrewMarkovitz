import SwiftUI

struct ScheduleView: View {
    @ObservedObject var vm: AppViewModel
    let onOpenCalendar: () -> Void

    var body: some View {
        DayScheduleView(vm: vm, date: vm.today, onOpenCalendar: onOpenCalendar)
            .id(vm.dayKey(vm.today))
    }
}

/// Shared by today's Schedule tab and any date opened from Calendar.
struct DayScheduleView: View {
    @ObservedObject var vm: AppViewModel
    let onOpenCalendar: () -> Void
    @State private var date: Date
    @State private var showingAdd = false
    @State private var showingLibrary = false
    @State private var showingHours = false
    @State private var editing: ActivityRecord?
    private let calendar = Calendar.autoupdatingCurrent

    init(vm: AppViewModel, date: Date, onOpenCalendar: @escaping () -> Void) {
        self.vm = vm
        self.onOpenCalendar = onOpenCalendar
        _date = State(initialValue: date)
    }

    var body: some View {
        let day = vm.schedule(for: date)
        ScrollViewReader { scrollProxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    dateHeader(day).id("scheduleTop")
                    if let error = day.error { conflicts(day, error: error) }
                    if let schedule = day.schedule {
                        VStack(spacing: 0) {
                            ForEach(schedule.intervals) { interval in
                                timelineRow(interval, day: day)
                            }
                        }
                        let scheduledNames = Set(schedule.intervals.flatMap { $0.activities.map(\.name) })
                        let unscheduled = day.eligible.filter { !scheduledNames.contains($0.record.name) }
                        if !unscheduled.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("Didn't fit this day").font(.headline)
                                ForEach(unscheduled) { entry in
                                    Button { editing = entry.record } label: {
                                        LabeledContent(entry.record.name, value: entry.record.detailText)
                                    }
                                }
                            }
                            .padding().background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
                        }
                    }
                    HStack {
                        Button { showingAdd = true } label: { Label("New Activity", systemImage: "plus") }
                        Spacer()
                        Button("Choose Saved") { showingLibrary = true }
                    }
                    .font(.subheadline.weight(.semibold))
                    Text("Tap an activity to edit it. Changes apply only to this date unless you choose Saved activity.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                .padding(16)
            }
            .background(Color(uiColor: .systemBackground))
            .navigationTitle(calendar.isDate(date, inSameDayAs: vm.today) ? "Today" : date.formatted(.dateTime.month(.abbreviated).day()))
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(true)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: onOpenCalendar) {
                        Label("Calendar", systemImage: "calendar")
                    }
                    .accessibilityLabel("Open Calendar")
                }
                ToolbarItemGroup(placement: .primaryAction) {
                    if !calendar.isDate(date, inSameDayAs: vm.today) {
                        Button("Today") {
                            vm.refreshToday()
                            withAnimation {
                                date = vm.today
                                scrollProxy.scrollTo("scheduleTop", anchor: .top)
                            }
                        }
                        .accessibilityLabel("Return to today's schedule")
                    }
                    Button { showingHours = true } label: { Label("Day hours", systemImage: "clock") }
                    Menu {
                        Button("New Activity", systemImage: "plus") { showingAdd = true }
                        Button("Choose Saved Activity", systemImage: "list.bullet") { showingLibrary = true }
                    } label: { Label("Add activity", systemImage: "plus") }
                }
            }
            .tint(.red)
            .onAppear { vm.refreshIfNeeded() }
            .sheet(isPresented: $showingAdd) { AddActivitySheet(vm: vm, date: date) }
            .sheet(item: $editing) { record in AddActivitySheet(vm: vm, record: record, date: date) }
            .sheet(isPresented: $showingLibrary) { DayActivitiesSheet(vm: vm, date: date) }
            .sheet(isPresented: $showingHours) { DayHoursSheet(vm: vm, date: date) }
        }
    }

    private func dateHeader(_ day: AppViewModel.DaySchedule) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Button { moveDay(-1) } label: { Image(systemName: "chevron.left").frame(width: 44, height: 44) }
                    .accessibilityLabel("Previous day")
                Spacer()
                VStack(spacing: 4) {
                    Text(date.formatted(.dateTime.weekday(.wide))).font(.title2.bold())
                    Text(date.formatted(date: .long, time: .omitted)).font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer()
                Button { moveDay(1) } label: { Image(systemName: "chevron.right").frame(width: 44, height: 44) }
                    .accessibilityLabel("Next day")
            }
            HStack {
                Text("\(TimeParser.toString(day.start)) – \(TimeParser.toString(day.end))")
                Spacer()
                if day.schedule != nil { Text(day.completionSummary) }
            }
            .font(.caption).foregroundStyle(.secondary)
        }
    }

    private func timelineRow(_ interval: ScheduleResult.ScheduledInterval, day: AppViewModel.DaySchedule) -> some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .trailing, spacing: 4) {
                Text(interval.start).font(.caption.weight(.medium))
                Text(interval.end).font(.caption2)
            }
            .monospacedDigit().foregroundStyle(.secondary).frame(width: 48, alignment: .trailing).padding(.top, 12)
            if let activity = interval.activities.first,
               let entry = day.eligible.first(where: { $0.record.name == activity.name }) {
                let color: Color = activity.isFixed ? .purple : .blue
                HStack(alignment: .top, spacing: 10) {
                    RoundedRectangle(cornerRadius: 2).fill(color).frame(width: 4)
                    Button { editing = entry.record } label: {
                        VStack(alignment: .leading, spacing: 5) {
                            Text(activity.name).font(.headline).strikethrough(entry.completed)
                            Text("\(activity.durationMinutes) min · \(activity.isFixed ? "Fixed" : (activity.value.map { "Priority \($0)" } ?? "Required"))")
                                .font(.caption)
                            if vm.hasOverride(id: entry.id, on: date) {
                                Text("Edited for this day").font(.caption2)
                            }
                            Spacer(minLength: 0)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Button { vm.markComplete(name: activity.name, on: date) } label: {
                        Image(systemName: entry.completed ? "checkmark.circle.fill" : "circle")
                            .font(.title3).frame(width: 32, height: 32)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Mark \(activity.name) \(entry.completed ? "incomplete" : "complete")")
                }
                .padding(12).foregroundStyle(color)
                .frame(minHeight: max(82, min(CGFloat(interval.durationMinutes) * 1.4, 180)))
                .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                .contextMenu {
                    Button("Edit This Activity", systemImage: "pencil") { editing = entry.record }
                    Button("Remove from This Day", systemImage: "calendar.badge.minus") { vm.toggleSelection(of: entry.record, on: date) }
                    if vm.hasOverride(id: entry.id, on: date) {
                        Button("Use Saved Activity", systemImage: "arrow.uturn.backward") { vm.resetOccurrence(id: entry.id, on: date) }
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    Divider()
                    Text("Free time").font(.caption).foregroundStyle(.tertiary)
                }
                .frame(maxWidth: .infinity, minHeight: 64, alignment: .topLeading)
                .padding(.top, 12)
            }
        }
        .padding(.bottom, 8)
    }

    private func conflicts(_ day: AppViewModel.DaySchedule, error: String) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Label(error, systemImage: "exclamationmark.triangle").foregroundStyle(.orange)
            ForEach(day.eligible) { entry in
                VStack(alignment: .leading, spacing: 6) {
                    Text(entry.record.name).font(.headline)
                    if let conflict = entry.conflict { Text(conflict).font(.caption).foregroundStyle(.secondary) }
                    HStack {
                        Button("Edit") { editing = entry.record }
                        Spacer()
                        Button("Remove from This Day") { vm.toggleSelection(of: entry.record, on: date) }
                    }
                    .font(.subheadline)
                }
            }
        }
        .padding().background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
    }

    private func moveDay(_ value: Int) {
        if let next = calendar.date(byAdding: .day, value: value, to: date) { date = next }
    }
}

private struct DayActivitiesSheet: View {
    @ObservedObject var vm: AppViewModel
    let date: Date
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(vm.orderedCatalog) { record in
                        Button { vm.toggleSelection(of: record, on: date) } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(record.name).foregroundStyle(.primary)
                                    Text(record.detailText).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: vm.isSelected(record, on: date) ? "checkmark.circle.fill" : "circle")
                            }
                        }
                        .accessibilityValue(vm.isSelected(record, on: date) ? "Included" : "Not included")
                    }
                } footer: {
                    Text("Select activities for \(date.formatted(date: .abbreviated, time: .omitted)). This changes only this day.")
                }
            }
            .overlay {
                if vm.catalog.isEmpty { ContentUnavailableView("No saved activities", systemImage: "list.bullet", description: Text("Create a new activity first.")) }
            }
            .navigationTitle("Choose Activities")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
}

private struct DayHoursSheet: View {
    @ObservedObject var vm: AppViewModel
    let date: Date
    @Environment(\.dismiss) private var dismiss
    @State private var start: Int
    @State private var end: Int
    @State private var error: String?

    init(vm: AppViewModel, date: Date) {
        self.vm = vm
        self.date = date
        let day = vm.schedule(for: date)
        _start = State(initialValue: day.start)
        _end = State(initialValue: day.end)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section(date.formatted(date: .abbreviated, time: .omitted)) {
                    TimeWheelPicker(title: "Start", minutes: $start)
                    TimeWheelPicker(title: "End", minutes: $end)
                }
                Section {
                    Button("Use Default Day Hours") { vm.useDefaultHours(on: date); dismiss() }
                } footer: { Text("Changes apply only to this date. Set your usual hours in Home → Day Hours.") }
            }
            .navigationTitle("Day Hours")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if vm.setDayHours(start: start, end: end, on: date) { dismiss() }
                        else { error = vm.errorMessage; vm.errorMessage = nil }
                    }
                    .disabled(end <= start)
                }
            }
            .alert("Couldn't Save Hours", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
                Button("OK") { error = nil }
            } message: { Text(error ?? "") }
        }
    }
}
