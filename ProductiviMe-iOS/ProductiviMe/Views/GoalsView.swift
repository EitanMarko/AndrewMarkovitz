import SwiftUI
import Charts

struct GoalsView: View {
    @ObservedObject var vm: AppViewModel
    @State private var editingGoal: PersonalGoal?

    var body: some View {
        List {
            if vm.goals.isEmpty {
                ContentUnavailableView {
                    Label("Build toward your next goal", systemImage: "target")
                } description: {
                    Text("Choose a productivity target, a time period, and an optional reward.")
                } actions: {
                    Button("Create a Goal") { editingGoal = newGoal() }.buttonStyle(.borderedProminent)
                }
                .listRowBackground(Color.clear)
            }
            ForEach(vm.goals) { goal in
                NavigationLink(value: AppRoute.goal(goal.id)) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(goal.title).font(.headline)
                        if let progress = vm.goalProgress(for: goal) {
                            Text("Target: \(goalPercent(progress.target.percentage)) · \(goalPeriod(progress))")
                                .font(.caption).foregroundStyle(.secondary)
                            Text(vm.goalActivityNames(progress.target).joined(separator: ", "))
                                .font(.caption).foregroundStyle(.secondary).lineLimit(2)
                            Text(goalStatus(progress)).font(.subheadline)
                            if let average = progress.average {
                                ProgressView(value: min(average / progress.target.percentage, 1))
                                    .accessibilityLabel("Progress toward target")
                                    .accessibilityValue("\(goalPercent(average)) average, target \(goalPercent(progress.target.percentage))")
                            }
                        } else {
                            Text("Set a productivity target to start tracking").font(.subheadline).foregroundStyle(.secondary)
                        }
                        if !goal.incentive.isEmpty {
                            Label(goal.incentive, systemImage: "gift").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 6)
                }
                .swipeActions {
                    Button("Delete", role: .destructive) { vm.deleteGoal(id: goal.id) }
                    Button("Edit") { editingGoal = goal }
                }
            }
        }
        .navigationTitle("Goals")
        .onAppear { vm.refreshIfNeeded() }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { editingGoal = newGoal() } label: { Label("New Goal", systemImage: "plus") }
            }
        }
        .sheet(item: $editingGoal) { GoalEditor(vm: vm, goal: $0) }
    }

    private func newGoal() -> PersonalGoal {
        PersonalGoal(title: "", target: ProductivityTarget(startDate: vm.today))
    }
}

struct GoalDetailView: View {
    @ObservedObject var vm: AppViewModel
    let goalID: UUID
    @State private var editingGoal: PersonalGoal?

    var body: some View {
        Group {
            if let goal = vm.goals.first(where: { $0.id == goalID }) {
                List {
                    Section {
                        Text(goal.title).font(.title2.bold())
                        if !goal.notes.isEmpty { Text(goal.notes).foregroundStyle(.secondary) }
                    }
                    if let progress = vm.goalProgress(for: goal) {
                        Section("Your productivity") {
                            LabeledContent("Target", value: goalPercent(progress.target.percentage))
                            LabeledContent("Completion rate", value: progress.average.map(goalPercent) ?? "No recorded occurrences")
                            LabeledContent("Completed / scheduled", value: "\(progress.completedCount) / \(progress.scheduledCount)")
                            Text(vm.goalActivityNames(progress.target).joined(separator: ", ")).foregroundStyle(.secondary)
                            Text(goalStatus(progress)).font(.headline)
                            Text(goalPeriod(progress)).foregroundStyle(.secondary)
                            LabeledContent("Planned occurrences remaining", value: "\(progress.remainingOccurrences)")
                            LabeledContent("Days remaining", value: "\(progress.remainingDays)\(progress.hasStarted && !progress.hasEnded ? " (including today)" : "")")
                        }
                        Section(progress.hasEnded ? "Your result" : "The path to your goal") {
                            outlook(progress)
                        }
                        if !progress.records.isEmpty {
                            Section("Recent daily productivity") {
                                Chart {
                                    ForEach(Array(progress.records.suffix(30))) { record in
                                        BarMark(x: .value("Day", record.date, unit: .day),
                                                y: .value("Productivity", record.percentage!))
                                            .foregroundStyle(Color.accentColor)
                                            .accessibilityLabel(record.date.formatted(date: .abbreviated, time: .omitted))
                                            .accessibilityValue(goalPercent(record.percentage!))
                                    }
                                    RuleMark(y: .value("Target", progress.target.percentage))
                                        .foregroundStyle(.secondary).lineStyle(StrokeStyle(dash: [4, 4]))
                                }
                                .chartYScale(domain: 0...100)
                                .frame(height: 200)
                                Text("Up to 30 recorded days. The dashed line is your target; your average uses the entire goal period so far.")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        if !goal.incentive.isEmpty {
                            Section(progress.isAchieved ? "Reward earned" : "Your incentive") {
                                Label(goal.incentive, systemImage: "gift")
                            }
                        }
                        Section("How this is calculated") {
                            Text("Completion rate = completed occurrences ÷ scheduled occurrences × 100, using only this goal's activities. Each occurrence counts equally. Days without a matching activity do not count as failures.")
                            Text("The remaining estimate includes all of today's scheduled occurrences plus upcoming recurrences and saved include/skip decisions. Future occurrences are planned estimates and may not all fit the final schedule. The estimate updates when plans or recorded results change.")
                            Text("All-activity goals include new activities automatically. Selected-activity goals follow the chosen activities through renames; deleting one preserves recorded history but removes its future occurrences.")
                            if progress.missingPastDays > 0 {
                                Text("\(progress.missingPastDays) elapsed days have no usable result and are excluded. This includes unresolved schedules or missing history. Recorded days with no matching activities are simply rest days. Results may change if those days gain data.")
                            }
                        }
                        .font(.footnote).foregroundStyle(.secondary)
                    } else {
                        Section {
                            Text("Choose a percentage and time period to turn this saved goal into a tracked productivity goal.")
                            Button("Set Up Tracking") { editingGoal = goal }
                        }
                    }
                }
                .toolbar {
                    ToolbarItem(placement: .primaryAction) {
                        Button("Edit") { editingGoal = goal }
                    }
                }
            } else {
                ContentUnavailableView("Goal no longer available", systemImage: "target")
            }
        }
        .navigationTitle("Goal Progress")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { vm.refreshIfNeeded() }
        .sheet(item: $editingGoal) { GoalEditor(vm: vm, goal: $0) }
    }

    @ViewBuilder
    private func outlook(_ progress: GoalProgress) -> some View {
        if progress.hasEnded {
            if progress.isAchieved {
                Label("You reached your target!", systemImage: "checkmark.seal")
                Text("Your consistency added up. Take a moment to celebrate what you accomplished.")
            } else if progress.average == nil {
                Text("This period has ended without enough recorded data to measure your result. A fresh goal is a chance to start building your story.")
            } else {
                Text("This period finished below your target, but every activity you completed still counts. Take what you learned into a new goal—you can build from here.")
            }
        } else if !progress.hasStarted {
            Text("Tracking starts \(progress.start.formatted(date: .abbreviated, time: .omitted)). Aim to complete \(goalPercent(progress.target.percentage)) of the matching occurrences over the period. You've given yourself something to work toward.")
            if progress.remainingOccurrences == 0 {
                Text("No matching occurrences are planned yet. Add them to your schedule to see the remaining estimate.")
            }
        } else if progress.isImpossible {
            Text("This target is now out of reach for this period with the currently recorded results, even if you complete every remaining planned occurrence.").font(.headline)
            Text("That doesn't erase the work you've done. Every next step still matters. You can adjust your target or extend the time frame, and keep building a rhythm that works for you.")
        } else if let required = progress.requiredAverage {
            Text(goalPercent(ceil(required * 10) / 10)).font(.largeTitle.bold())
            Text("Complete at least \(progress.requiredCompletions) of \(progress.remainingOccurrences) planned occurrences, including all of today's occurrences.")
            if required <= 0 {
                Text("Your recorded completions have already put this target within reach even with no further completions, based on the current plan. Keep going at a pace that works for you.")
            } else {
                Text("You still have a path to your goal. Focus on the next activity—small completions add up.")
            }
        } else {
            Text("No matching occurrences are planned for the rest of this period. Add activities to your schedule to continue working toward your goal.")
        }
    }
}

private struct GoalEditor: View {
    @ObservedObject var vm: AppViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var goal: PersonalGoal
    @State private var target: ProductivityTarget
    @State private var period: Period
    @State private var selectedScope: Bool
    @State private var showingActivityPicker = false
    @State private var saveFailed = false
    private let isNew: Bool
    private enum Period: String, CaseIterable { case week = "Week", month = "Month", year = "Year", custom = "Custom" }

    init(vm: AppViewModel, goal: PersonalGoal) {
        self.vm = vm
        _goal = State(initialValue: goal)
        let target = goal.target ?? ProductivityTarget(startDate: vm.today)
        _target = State(initialValue: target)
        _selectedScope = State(initialValue: target.activityIDs != nil)
        let period: Period = target.amount == 1 ? (target.unit == .weeks ? .week : target.unit == .months ? .month : target.unit == .years ? .year : .custom) : .custom
        _period = State(initialValue: period)
        isNew = !vm.goals.contains { $0.id == goal.id }
    }

    private var deletedSelection: [UUID] {
        (target.activityIDs ?? []).filter { id in !vm.catalog.contains { $0.id == id } }
            .sorted { $0.uuidString < $1.uuidString }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Goal") {
                    TextField("Goal name (optional)", text: $goal.title, axis: .vertical)
                    TextField("Details (optional)", text: $goal.notes, axis: .vertical).lineLimit(2...5)
                }
                Section {
                    Menu {
                        Button("All activities") {
                            selectedScope = false
                            target.activityIDs = nil
                        }
                        Button("Selected activities") {
                            selectedScope = true
                            if target.activityIDs == nil { target.activityIDs = [] }
                            showingActivityPicker = true
                        }
                    } label: {
                        LabeledContent("Include", value: selectedScope ? "Selected activities" : "All activities")
                    }
                    if selectedScope {
                        ForEach(vm.orderedCatalog.filter { target.activityIDs?.contains($0.id) == true }) { activity in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(activity.name)
                                Text(activity.recurrenceText).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        if target.activityIDs?.isEmpty != false {
                            Text("No activities selected yet.").foregroundStyle(.secondary)
                        }
                        Button {
                            showingActivityPicker = true
                        } label: {
                            Label("Choose Activities", systemImage: "list.bullet")
                        }
                        ForEach(deletedSelection, id: \.self) { id in
                            HStack {
                                Text("\(target.activityNames?[id] ?? "Activity") (deleted)").foregroundStyle(.secondary)
                                Spacer()
                                Button("Remove") { target.activityIDs?.remove(id) }
                            }
                        }
                    }
                } header: { Text("Activities to track") } footer: {
                    Text(selectedScope ? "Choose one activity for an individual goal, or several for a combined completion rate. Select at least one." : "Includes all scheduled activities, including activities you add later.")
                }
                Section {
                    Stepper("Target: \(goalPercent(target.percentage))", value: $target.percentage, in: 1...100, step: 1)
                    Slider(value: $target.percentage, in: 1...100, step: 1)
                        .accessibilityLabel("Target productivity percentage")
                } header: { Text("Productivity target") } footer: {
                    Text("Aim to complete this percentage of the included activity occurrences over your chosen period.")
                }
                Section("Time period") {
                    DatePicker("Start date", selection: $target.startDate, displayedComponents: .date)
                    Picker("Length", selection: $period) {
                        ForEach(Period.allCases, id: \.self) { Text($0.rawValue).tag($0) }
                    }
                    if period == .custom {
                        TextField("Number of \(target.unit.rawValue)", value: $target.amount, format: .number)
                            .keyboardType(.numberPad)
                        Picker("Unit", selection: $target.unit) {
                            ForEach(ProductivityTarget.Unit.allCases, id: \.self) { Text($0.rawValue.capitalized).tag($0) }
                        }
                        Text("Enter a whole number from 1 to 1,000.").font(.caption).foregroundStyle(.secondary)
                    }
                    if let end = target.endExclusive(calendar: .autoupdatingCurrent),
                       let last = Calendar.autoupdatingCurrent.date(byAdding: .day, value: -1, to: end) {
                        LabeledContent("Last day (included)", value: last.formatted(date: .abbreviated, time: .omitted))
                    }
                }
                Section {
                    TextField("Your incentive (optional)", text: $goal.incentive, axis: .vertical).lineLimit(2...4)
                } header: { Label("Incentive", systemImage: "gift") } footer: {
                    Text("Choose a reward to look forward to when you reach your target.")
                }
            }
            .navigationTitle(isNew ? "New Goal" : "Edit Goal")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(isPresented: $showingActivityPicker) {
                GoalActivityPicker(vm: vm, selection: Binding(
                    get: { target.activityIDs ?? [] },
                    set: { target.activityIDs = $0 }
                ))
            }
            .onChange(of: period) { _, value in
                switch value {
                case .week: target.amount = 1; target.unit = .weeks
                case .month: target.amount = 1; target.unit = .months
                case .year: target.amount = 1; target.unit = .years
                case .custom: break
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    if !showingActivityPicker { Button("Cancel") { dismiss() } }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if !showingActivityPicker {
                    Button("Save") {
                        var saved = goal
                        var configured = target
                        var names = configured.activityNames ?? [:]
                        for activity in vm.catalog where configured.activityIDs?.contains(activity.id) == true {
                            names[activity.id] = activity.name
                        }
                        configured.activityNames = configured.activityIDs == nil ? nil : names.filter { configured.activityIDs!.contains($0.key) }
                        saved.target = configured
                        if saved.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            saved.title = "\(goalPercent(target.percentage)) productivity goal"
                        }
                        if vm.saveGoal(saved) { dismiss() } else { saveFailed = true }
                    }
                    .disabled(target.endExclusive(calendar: .autoupdatingCurrent) == nil)
                    }
                }
            }
            .alert("Goal could not be saved", isPresented: $saveFailed) {
                Button("OK", role: .cancel) { }
            } message: { Text("Check your goal settings and try again. Your existing saved data has been preserved.") }
        }
    }
}

private struct GoalActivityPicker: View {
    @ObservedObject var vm: AppViewModel
    @Binding var selection: Set<UUID>
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""

    private var matchingActivities: [ActivityRecord] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        return vm.orderedCatalog.filter { query.isEmpty || $0.name.localizedStandardContains(query) }
    }

    var body: some View {
        List {
            Section {
                ForEach(matchingActivities) { activity in
                    Button {
                        if selection.contains(activity.id) { selection.remove(activity.id) }
                        else { selection.insert(activity.id) }
                    } label: {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(activity.name).foregroundStyle(.primary)
                                Text(activity.recurrenceText).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: selection.contains(activity.id) ? "checkmark.circle.fill" : "circle")
                                .foregroundStyle(selection.contains(activity.id) ? Color.accentColor : .secondary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityValue(selection.contains(activity.id) ? "Selected" : "Not selected")
                    .accessibilityHint("Toggle inclusion in this goal")
                }
            } footer: {
                Text("\(selection.count) selected. Choose one or more activities, then tap Done.")
            }
        }
        .overlay {
            if vm.catalog.isEmpty {
                ContentUnavailableView("No saved activities", systemImage: "list.bullet",
                    description: Text("Create activities in your activity bank, then return here to select them."))
            } else if matchingActivities.isEmpty {
                ContentUnavailableView.search(text: searchText)
            }
        }
        .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search activities")
        .navigationTitle("Select Activities")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
        }
    }
}

private func goalPercent(_ value: Double) -> String {
    value.formatted(.number.precision(.fractionLength(0...1))) + "%"
}

private func goalPeriod(_ progress: GoalProgress) -> String {
    "\(progress.start.formatted(date: .abbreviated, time: .omitted)) – \(progress.lastDay.formatted(date: .abbreviated, time: .omitted))"
}

private func goalStatus(_ progress: GoalProgress) -> String {
    if !progress.hasStarted { return "Starts \(progress.start.formatted(date: .abbreviated, time: .omitted))" }
    if progress.hasEnded { return progress.isAchieved ? "Target achieved" : progress.average == nil ? "Ended · No recorded data" : "Period finished · Below target" }
    if progress.isImpossible { return "Target needs adjusting · Keep building" }
    return progress.average.map { "\(goalPercent($0)) completed so far" } ?? "Ready for your first recorded occurrence"
}
