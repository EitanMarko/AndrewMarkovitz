import SwiftUI

struct ActivitiesView: View {
    @ObservedObject var vm: AppViewModel
    @State private var showingAddSheet = false
    @State private var editing: ActivityRecord?

    var body: some View {
        List {
            Section {
                ForEach(vm.orderedCatalog) { record in
                    HStack(spacing: 12) {
                        Button { vm.toggleSelection(of: record) } label: {
                            Image(systemName: vm.isSelected(record) ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(vm.isSelected(record) ? Color.accentColor : .secondary)
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("\(vm.isSelected(record) ? "Remove from" : "Add to") today: \(record.name)")
                        Button { editing = record } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(record.name).foregroundStyle(.primary)
                                Text(record.detailText).font(.caption).foregroundStyle(.secondary)
                                Text(record.recurrenceText).font(.caption).foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        Image(systemName: "chevron.right").font(.caption).foregroundStyle(.tertiary)
                    }
                }
                .onMove { vm.moveActivities(from: $0, to: $1) }
                .onDelete { offsets in
                    let ids = offsets.map { vm.orderedCatalog[$0].id }
                    for id in ids { vm.deleteActivity(id: id) }
                }
            } header: {
                Text(vm.customOrder ? "Custom order" : "Alphabetical order")
            } footer: {
                Text("Check an activity to include it today. Tap its name to edit. Use Edit to reorder or delete. Deleted activities must be created again to use them.")
            }
        }
        .overlay {
            if vm.catalog.isEmpty {
                ContentUnavailableView("No activities yet", systemImage: "list.bullet",
                    description: Text("Tap + to create an activity. All activities stay here until you delete them."))
            }
        }
        .navigationTitle("Activities")
        .toolbar {
            ToolbarItem(placement: .primaryAction) { EditButton() }
            ToolbarItemGroup(placement: .primaryAction) {
                Menu {
                    Button("Alphabetical", systemImage: "textformat.abc") { vm.useAlphabeticalOrder() }
                    Text("Use Edit and drag the handles for a custom order.")
                } label: { Label("Sort activities", systemImage: "arrow.up.arrow.down") }
                Button { showingAddSheet = true } label: { Label("Add activity", systemImage: "plus") }
            }
        }
        .sheet(isPresented: $showingAddSheet) { AddActivitySheet(vm: vm) }
        .sheet(item: $editing) { record in AddActivitySheet(vm: vm, record: record) }
    }
}

struct AddActivitySheet: View {
    @ObservedObject var vm: AppViewModel
    @Environment(\.dismiss) private var dismiss
    let record: ActivityRecord?
    let date: Date
    let occurrenceEditing: Bool
    @State private var editSeries = false

    enum ActivityKind: String, CaseIterable {
        case flexible = "Flexible"
        case set = "Fixed"
    }

    @State private var kind = ActivityKind.flexible
    @State private var name = ""
    @State private var setStart = 9 * 60
    @State private var setEnd = 10 * 60
    @State private var flexDuration = 30
    @State private var flexValue = 5
    @State private var doToday = false
    @State private var weekdays: Set<Int> = []
    @State private var errorText: String?

    init(vm: AppViewModel, record: ActivityRecord? = nil, date: Date? = nil) {
        self.vm = vm
        self.record = record
        self.date = date ?? vm.today
        self.occurrenceEditing = record != nil && date != nil
        if let record {
            _name = State(initialValue: record.name)
            _weekdays = State(initialValue: record.weekdays)
            switch record.details {
            case .set(let start, let end):
                _kind = State(initialValue: .set)
                _setStart = State(initialValue: start)
                _setEnd = State(initialValue: end)
            case .flexible(let duration, let value, let required):
                _flexDuration = State(initialValue: duration)
                _flexValue = State(initialValue: value ?? 5)
                _doToday = State(initialValue: required)
            }
        }
    }

    private var canSubmit: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            (kind == .set ? setEnd > setStart : flexDuration > 0)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Date", value: date.formatted(date: .abbreviated, time: .omitted))
                    if occurrenceEditing {
                        Picker("Apply changes to", selection: $editSeries) {
                            Text("Only this day").tag(false)
                            Text("Saved activity").tag(true)
                        }
                    }
                } footer: {
                    if occurrenceEditing {
                        Text(editSeries
                             ? "Updates the saved activity and occurrences without their own edits."
                             : "Changes only this date. Other days and the saved activity stay the same.")
                    }
                }
                Section("Type") {
                    Picker("Activity type", selection: $kind) {
                        ForEach(ActivityKind.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                Section("Details") {
                    TextField("Activity name", text: $name)
                    if kind == .set {
                        TimeWheelPicker(title: "Start time", minutes: $setStart)
                        TimeWheelPicker(title: "End time", minutes: $setEnd)
                    } else {
                        TimeWheelPicker(title: "Duration", minutes: $flexDuration)
                        Toggle(weekdays.isEmpty || (occurrenceEditing && !editSeries) ? "Must do this day" : "Must do on repeat days", isOn: $doToday)
                        if !doToday { Stepper("Priority: \(flexValue)", value: $flexValue, in: 1...10) }
                    }
                }
                if kind == .flexible {
                    Section {
                        Text(doToday
                             ? "Required activities have no priority rating. If they cannot all fit, your schedule will show a conflict."
                             : "Priority 1 = low, 10 = high. Flexible activities are included when time allows.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                if !occurrenceEditing || editSeries {
                    Section {
                        NavigationLink {
                            RepeatDaysView(weekdays: $weekdays)
                        } label: {
                            HStack {
                                Text("Repeat")
                                Spacer()
                                Text(recurrenceText).foregroundStyle(.secondary).multilineTextAlignment(.trailing)
                            }
                        }
                    } footer: {
                        Text(weekdays.isEmpty
                             ? "New activities are added to the selected date and saved in Activities for reuse."
                             : "This activity repeats on the selected weekdays, starting on its first date, and stays in Activities until deleted.")
                    }
                }
            }
            .navigationTitle(record == nil ? "Add Activity" : "Edit Activity")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(record == nil ? "Add" : "Save") { save() }.disabled(!canSubmit)
                }
            }
            .alert("Couldn't Save Activity", isPresented: Binding(
                get: { errorText != nil }, set: { if !$0 { errorText = nil } }
            )) { Button("OK") { errorText = nil } } message: { Text(errorText ?? "") }
        }
    }

    private var recurrenceText: String {
        ActivityRecord(name: name, details: .set(start: 0, end: 5), weekdays: weekdays).recurrenceText
    }

    private func save() {
        let details: ActivityRecord.Details = kind == .set
            ? .set(start: setStart, end: setEnd)
            : .flexible(duration: flexDuration, value: doToday ? nil : flexValue, doToday: doToday)
        let updated = ActivityRecord(id: record?.id ?? UUID(), name: name, details: details,
                                     weekdays: weekdays, oneTimeDay: record?.oneTimeDay ?? "",
                                     recurrenceStart: record?.recurrenceStart)
        let saved = occurrenceEditing && !editSeries
            ? vm.updateOccurrence(updated, on: date)
            : vm.addActivity(updated, on: date)
        if saved {
            if occurrenceEditing && editSeries { vm.resetOccurrence(id: updated.id, on: date) }
            dismiss()
        } else { errorText = vm.errorMessage; vm.errorMessage = nil }
    }
}

private struct RepeatDaysView: View {
    @Binding var weekdays: Set<Int>
    private var orderedDays: [Int] {
        let first = Calendar.current.firstWeekday
        return (0..<7).map { (first - 1 + $0) % 7 + 1 }
    }

    var body: some View {
        List {
            Section {
                Button("Never") { weekdays = [] }
                Button("Every Day") { weekdays = Set(1...7) }
                Button("Weekdays") { weekdays = Set(2...6) }
                Button("Weekends") { weekdays = [1, 7] }
            }
            Section {
                ForEach(orderedDays, id: \.self) { day in
                    Button {
                        if weekdays.contains(day) { weekdays.remove(day) }
                        else { weekdays.insert(day) }
                    } label: {
                        HStack {
                            Text("Every \(Calendar.current.weekdaySymbols[day - 1])").foregroundStyle(.primary)
                            Spacer()
                            if weekdays.contains(day) { Image(systemName: "checkmark") }
                        }
                    }
                    .accessibilityAddTraits(weekdays.contains(day) ? .isSelected : [])
                }
            } footer: { Text("Choose any combination of days. No selected days means this activity does not repeat.") }
        }
        .navigationTitle("Repeat")
        .navigationBarTitleDisplayMode(.inline)
    }
}
