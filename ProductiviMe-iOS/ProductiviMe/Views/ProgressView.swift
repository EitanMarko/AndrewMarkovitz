import SwiftUI
import Charts

struct ProductivityProgressView: View {
    @ObservedObject var vm: AppViewModel
    @State private var range = 7
    @State private var selectedDate: Date?
    @State private var showingDateSearch = false
    @State private var lookupDate = Date()
    private let calendar = Calendar.autoupdatingCurrent

    private struct PlotPoint: Identifiable {
        let id: String
        let date: Date
        let percentage: Double
        let segment: Int
    }

    var body: some View {
        let days = vm.progressDays(count: range)
        let summary = ProgressSummary(days: days)
        let selected = vm.progress(on: selectedDate ?? vm.today)
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Picker("Time range", selection: $range) {
                    Text("7 Days").tag(7)
                    Text("30 Days").tag(30)
                    Text("90 Days").tag(90)
                }
                .pickerStyle(.segmented)

                VStack(alignment: .leading, spacing: 8) {
                    Text("Completion rate").font(.headline).foregroundStyle(.secondary)
                    Text(summary.percentage.map { String(format: "%.0f%%", $0) } ?? "—")
                        .font(.system(size: 48, weight: .bold, design: .rounded)).monospacedDigit()
                    Text("\(summary.completed) of \(summary.scheduled) scheduled activities completed")
                        .font(.subheadline).foregroundStyle(.secondary)
                    Text("Across the last \(range) days · Today is still in progress")
                        .font(.caption).foregroundStyle(.secondary)
                }

                VStack(alignment: .leading, spacing: 16) {
                    Text("Daily completion").font(.headline)
                    if days.contains(where: { $0.percentage != nil }) {
                        completionChart(days)
                        Text("Touch the graph to inspect a day.").font(.caption).foregroundStyle(.secondary)
                    } else {
                        ContentUnavailableView("No progress to chart yet", systemImage: "chart.xyaxis.line",
                            description: Text("Schedule activities and mark them complete. Your daily completion percentage will appear here."))
                            .frame(minHeight: 230)
                    }
                    HStack {
                        Text("Selected day").font(.subheadline.weight(.semibold))
                        Spacer()
                        Button {
                            lookupDate = selected.date
                            showingDateSearch = true
                        } label: { Label("Find a Day", systemImage: "magnifyingglass") }
                        .font(.subheadline)
                    }
                    selectedDay(selected)
                    if !days.contains(where: { $0.id == selected.id }) {
                        Text("This date is outside the graph's selected range.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                .padding(20)
                .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))

                VStack(alignment: .leading, spacing: 8) {
                    Text("How it's calculated").font(.subheadline.weight(.semibold))
                    Text("Completed activities ÷ scheduled activities × 100. Each activity counts equally. Activities that didn't fit are excluded. The period total combines activity counts across recorded days.")
                    Text("Gaps mean no activities, an unresolved schedule, or no recorded data. Future dates are excluded. Past results are saved so later changes to your activity bank don't erase your progress.")
                }
                .font(.footnote).foregroundStyle(.secondary)


            }
            .padding(20)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Progress")
        .onAppear { vm.refreshIfNeeded() }
        .onChange(of: range) { _, _ in selectedDate = nil }
        .sheet(isPresented: $showingDateSearch) {
            NavigationStack {
                ScrollView {
                    VStack(spacing: 16) {
                        DatePicker("Find a day", selection: $lookupDate, in: ...Date(), displayedComponents: .date)
                            .datePickerStyle(.graphical)
                            .fixedSize(horizontal: false, vertical: true)
                        Text("Choose a date to see its saved completion percentage and activity counts, including dates outside the graph's range.")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                    .padding()
                }
                .navigationTitle("Find a Day")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showingDateSearch = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Show Progress") {
                            selectedDate = calendar.startOfDay(for: lookupDate)
                            showingDateSearch = false
                        }
                    }
                }
            }
            .presentationDetents([.large])
        }
    }

    private func completionChart(_ days: [ProgressDay]) -> some View {
        let points = plotPoints(days)
        let first = days.first!.date
        let tickSpacing = range == 7 ? 1 : (range == 30 ? 7 : 15)
        let ticks = days.enumerated().compactMap { index, day in index % tickSpacing == 0 ? day.date : nil }
        let end = calendar.date(byAdding: .day, value: 1, to: days.last!.date)!
        return Chart {
            ForEach(points) { point in
                LineMark(x: .value("Date", point.date, unit: .day),
                         y: .value("Completed", point.percentage),
                         series: .value("Recorded stretch", point.segment))
                    .foregroundStyle(Color.accentColor).lineStyle(StrokeStyle(lineWidth: 2.5))
                PointMark(x: .value("Date", point.date, unit: .day), y: .value("Completed", point.percentage))
                    .foregroundStyle(Color.accentColor).symbolSize(range == 7 ? 45 : 20)
                    .accessibilityLabel(point.date.formatted(date: .abbreviated, time: .omitted))
                    .accessibilityValue(String(format: "%.0f percent completed", point.percentage))
            }
            if let selectedDate, days.contains(where: { calendar.isDate($0.date, inSameDayAs: selectedDate) }) {
                RuleMark(x: .value("Selected day", selectedDate, unit: .day))
                    .foregroundStyle(.secondary.opacity(0.4)).lineStyle(StrokeStyle(dash: [4, 4]))
            }
        }
        .chartYScale(domain: 0...100)
        .chartXScale(domain: first...end)
        .chartXSelection(value: $selectedDate)
        .chartLegend(.hidden)
        .chartYAxis {
            AxisMarks(position: .leading, values: [0, 25, 50, 75, 100]) { value in
                AxisGridLine()
                AxisValueLabel {
                    if let percent = value.as(Int.self) { Text("\(percent)%") }
                }
            }
        }
        .chartXAxis {
            AxisMarks(values: ticks) { _ in
                AxisValueLabel(format: range == 7 ? .dateTime.weekday(.abbreviated) : .dateTime.month(.abbreviated).day())
            }
        }
        .frame(height: 240)
    }

    private func plotPoints(_ days: [ProgressDay]) -> [PlotPoint] {
        var segment = 0
        return days.compactMap { day in
            guard let percentage = day.percentage else { segment += 1; return nil }
            return PlotPoint(id: day.id, date: day.date, percentage: percentage, segment: segment)
        }
    }

    private func selectedDay(_ day: ProgressDay) -> some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text(day.date.formatted(date: .abbreviated, time: .omitted)).font(.subheadline.weight(.semibold))
                Text(dayDescription(day)).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Text(day.percentage.map { String(format: "%.0f%%", $0) } ?? "—").font(.title2.bold()).monospacedDigit()
        }
        .padding(.top, 8)
        .accessibilityElement(children: .combine)
    }

    private func dayDescription(_ day: ProgressDay) -> String {
        guard let result = day.result else { return "No recorded data" }
        if result.hasConflict { return "Schedule needs attention" }
        if result.scheduledCount == 0 { return "No scheduled activities" }
        let suffix = calendar.isDate(day.date, inSameDayAs: vm.today) ? " · In progress" : ""
        return "\(result.completedCount) of \(result.scheduledCount) completed\(suffix)"
    }
}
