import SwiftUI

// MARK: - Activities list

struct ActivitiesView: View {
    @ObservedObject var vm: AppViewModel
    @State private var showingAddSheet = false

    var body: some View {
        NavigationStack {
            Group {
                if !vm.dayActive {
                    ContentUnavailableView(
                        "No active day",
                        systemImage: "calendar.badge.exclamationmark",
                        description: Text("Go to the Day tab and set your hours first.")
                    )
                } else if vm.activities.isEmpty {
                    ContentUnavailableView(
                        "No activities yet",
                        systemImage: "plus.circle",
                        description: Text("Tap + to add your first activity.")
                    )
                } else {
                    List(vm.activities) { activity in
                        HStack(spacing: 12) {
                            Image(systemName: activity.type == .set
                                  ? "pin.fill" : "clock.badge.questionmark")
                                .foregroundStyle(activity.type == .set ? .red : .blue)
                                .frame(width: 24)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(activity.name)
                                    .font(.body)
                                Text(activity.detail)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Activities")
            .toolbar {
                if vm.dayActive {
                    ToolbarItem(placement: .primaryAction) {
                        Button {
                            showingAddSheet = true
                        } label: {
                            Image(systemName: "plus")
                        }
                    }
                }
            }
            .sheet(isPresented: $showingAddSheet) {
                AddActivitySheet(vm: vm, isPresented: $showingAddSheet)
            }
        }
    }
}

// MARK: - Add activity sheet

struct AddActivitySheet: View {
    @ObservedObject var vm: AppViewModel
    @Binding var isPresented: Bool

    enum ActivityKind: String, CaseIterable {
        case flexible = "Flexible"
        case set      = "Fixed (Set)"
    }

    @State private var kind      = ActivityKind.flexible
    @State private var name      = ""

    // Fixed-time fields
    @State private var setStart  = ""
    @State private var setEnd    = ""

    // Flexible fields
    @State private var flexDur   = ""
    @State private var flexValue = 5
    @State private var doToday   = false

    private var canSubmit: Bool {
        guard !name.trimmingCharacters(in: .whitespaces).isEmpty else { return false }
        switch kind {
        case .set:      return !setStart.isEmpty && !setEnd.isEmpty
        case .flexible: return !flexDur.isEmpty
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Type") {
                    Picker("Activity type", selection: $kind) {
                        ForEach(ActivityKind.allCases, id: \.self) { Text($0.rawValue) }
                    }
                    .pickerStyle(.segmented)
                }

                Section("Details") {
                    TextField("Activity name", text: $name)

                    if kind == .set {
                        TextField("Start time  (e.g. 9:00)", text: $setStart)
                            .keyboardType(.numbersAndPunctuation)
                        TextField("End time    (e.g. 10:30)", text: $setEnd)
                            .keyboardType(.numbersAndPunctuation)
                    } else {
                        TextField("Duration  (e.g. 0:45 for 45 min)", text: $flexDur)
                            .keyboardType(.numbersAndPunctuation)

                        Stepper("Priority: \(flexValue)", value: $flexValue, in: 1...10)

                        Toggle("Must do today", isOn: $doToday)
                    }
                }

                if kind == .flexible {
                    Section {
                        Text("Priority 1 = low, 10 = high. \"Must do today\" guarantees this activity appears in the schedule if it fits.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Add Activity")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { isPresented = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        Task {
                            switch kind {
                            case .set:
                                await vm.addSetActivity(name: name, start: setStart, end: setEnd)
                            case .flexible:
                                await vm.addFlexActivity(
                                    name: name, duration: flexDur,
                                    value: flexValue, doToday: doToday)
                            }
                            if vm.errorMessage == nil { isPresented = false }
                        }
                    }
                    .disabled(!canSubmit || vm.isLoading)
                }
            }
        }
    }
}
