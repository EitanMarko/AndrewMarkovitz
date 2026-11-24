import SwiftUI
import Combine

struct CalendarView: View {
    @ObservedObject var vm: AppViewModel
    @StateObject private var scrollController = CalendarScrollController()
    @State private var firstMonth: Date
    @State private var lastMonth: Date
    @State private var visibleMonth: Date?
    @State private var hasPositionedCalendar = false
    @State private var jumpDate: Date
    @State private var showingDatePicker = false
    @State private var showingAdd = false
    private let calendar = Calendar.autoupdatingCurrent

    init(vm: AppViewModel) {
        self.vm = vm
        let calendar = Calendar.autoupdatingCurrent
        let current = CalendarMonth(containing: vm.today, calendar: calendar).firstDay
        _firstMonth = State(initialValue: calendar.date(byAdding: .month, value: -24, to: current)!)
        _lastMonth = State(initialValue: calendar.date(byAdding: .month, value: 24, to: current)!)
        _visibleMonth = State(initialValue: nil)
        _jumpDate = State(initialValue: vm.today)
    }

    private var months: [Date] {
        let count = calendar.dateComponents([.month], from: firstMonth, to: lastMonth).month ?? 0
        return (0...max(0, count)).compactMap { calendar.date(byAdding: .month, value: $0, to: firstMonth) }
    }

    var body: some View {
        GeometryReader { geometry in
            ScrollViewReader { scrollProxy in
                ScrollView(.vertical) {
                    LazyVStack(alignment: .leading, spacing: 28) {
                        ForEach(months, id: \.self) { month in
                            monthSection(month, width: max(0, geometry.size.width - 32))
                                .id(month)
                        }
                    }
                    .frame(width: max(0, geometry.size.width - 32), alignment: .leading)
                    .scrollTargetLayout()
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                    .background(CalendarScrollProbe(controller: scrollController))
                }
                .scrollPosition(id: $visibleMonth, anchor: .top)
                .onChange(of: visibleMonth) { _, month in
                    if hasPositionedCalendar, let month { extendMonths(around: month) }
                }
                .onAppear {
                    guard !hasPositionedCalendar else { return }
                    vm.refreshIfNeeded()
                    let target = CalendarMonth(containing: vm.today, calendar: calendar).firstDay
                    DispatchQueue.main.async {
                        scrollProxy.scrollTo(target, anchor: .top)
                        visibleMonth = target
                        hasPositionedCalendar = true
                    }
                }
                .background(Color(uiColor: .systemBackground))
                .navigationTitle("Calendar")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItemGroup(placement: .primaryAction) {
                        Button("Today") {
                            scrollController.stopScrolling()
                            vm.refreshIfNeeded()
                            let target = CalendarMonth(containing: vm.today, calendar: calendar).firstDay
                            extendMonths(around: target)
                            // Cancel momentum first, then begin the return animation immediately.
                            withAnimation(.easeInOut(duration: 0.35)) {
                                scrollProxy.scrollTo(target, anchor: .top)
                                visibleMonth = target
                            }
                        }
                        .accessibilityLabel("Return to the current month")
                        Button {
                            jumpDate = visibleMonth ?? vm.today
                            showingDatePicker = true
                        } label: { Label("Go to date", systemImage: "calendar.badge.clock") }
                        Button { showingAdd = true } label: { Label("Add activity", systemImage: "plus") }
                    }
                }
                .tint(.red)
                .sheet(isPresented: $showingAdd) { AddActivitySheet(vm: vm, date: vm.today) }
                .sheet(isPresented: $showingDatePicker) {
                    NavigationStack {
                        ScrollView {
                            DatePicker("Go to date", selection: $jumpDate, displayedComponents: .date)
                                .datePickerStyle(.graphical)
                                .fixedSize(horizontal: false, vertical: true)
                                .padding()
                        }
                            .navigationTitle("Go to Date")
                            .navigationBarTitleDisplayMode(.inline)
                            .toolbar {
                                ToolbarItem(placement: .confirmationAction) {
                                    Button("Done") {
                                        let target = CalendarMonth(containing: jumpDate, calendar: calendar).firstDay
                                        extendMonths(around: target)
                                        showingDatePicker = false
                                        // Make the target available in the layout before scrolling to it.
                                        DispatchQueue.main.async {
                                            scrollProxy.scrollTo(target, anchor: .top)
                                            visibleMonth = target
                                        }
                                    }
                                }
                            }
                    }
                    .presentationDetents([.large])
                }
            }
        }
    }

    private func monthSection(_ month: Date, width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 2) {
                Text(month.formatted(.dateTime.year())).font(.subheadline).foregroundStyle(.red)
                Text(month.formatted(.dateTime.month(.wide))).font(.largeTitle.bold())
                    .accessibilityAddTraits(.isHeader)
            }
            monthGrid(month, width: width)
        }
    }

    private func monthGrid(_ month: Date, width: CGFloat) -> some View {
        let grid = CalendarMonth(containing: month, calendar: calendar)
        return VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(0..<7, id: \.self) { offset in
                    let index = (calendar.firstWeekday - 1 + offset) % 7
                    Text(calendar.veryShortStandaloneWeekdaySymbols[index])
                        .font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(.bottom, 10)
            // Only months are lazy, so their complete height is known when scrolling.
            VStack(spacing: 0) {
                ForEach(0..<(grid.cells.count / 7), id: \.self) { row in
                    HStack(spacing: 0) {
                        ForEach(0..<7, id: \.self) { column in
                            VStack(spacing: 0) {
                                Divider()
                                if let date = grid.cells[row * 7 + column] {
                                    NavigationLink(value: AppRoute.day(date)) { dayCell(date) }
                                        .buttonStyle(.plain)
                                        .accessibilityLabel("\(date.formatted(date: .complete, time: .omitted)), \(vm.hasActivities(on: date) ? "has activities" : "no activities")")
                                } else {
                                    Color.clear.frame(height: 62)
                                }
                            }
                            .frame(width: width / 7)
                        }
                    }
                }
            }
        }
    }

    private func dayCell(_ date: Date) -> some View {
        let isToday = calendar.isDate(date, inSameDayAs: vm.today)
        return VStack(spacing: 4) {
            Text("\(calendar.component(.day, from: date))")
                .font(.title3.weight(isToday ? .semibold : .regular))
                .foregroundStyle(isToday ? Color.white : (calendar.isDateInWeekend(date) ? .secondary : .primary))
                .frame(width: 36, height: 36)
                .background(isToday ? Color.red : .clear, in: Circle())
            Circle().fill(vm.hasActivities(on: date) ? Color.secondary : .clear).frame(width: 5, height: 5)
        }
        .frame(maxWidth: .infinity).frame(height: 62)
        .contentShape(Rectangle())
    }

    private func extendMonths(around month: Date) {
        // Grow before the user reaches an edge, retaining stable month IDs and scroll position.
        if month < calendar.date(byAdding: .month, value: 6, to: firstMonth)! {
            firstMonth = calendar.date(byAdding: .month, value: -24, to: month)!
        }
        if month > calendar.date(byAdding: .month, value: -6, to: lastMonth)! {
            lastMonth = calendar.date(byAdding: .month, value: 24, to: month)!
        }
    }
}

/// SwiftUI's scroll target can wait for an active gesture/deceleration to finish.
/// Stop the owning scroll view first so an explicit Today tap takes precedence.
private final class CalendarScrollController: ObservableObject {
    weak var probe: UIView?

    func stopScrolling() {
        var ancestor = probe?.superview
        while let view = ancestor {
            if let scrollView = view as? UIScrollView {
                scrollView.stopScrollingAndZooming()
                return
            }
            ancestor = view.superview
        }
    }
}

private struct CalendarScrollProbe: UIViewRepresentable {
    let controller: CalendarScrollController

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.isUserInteractionEnabled = false
        controller.probe = view
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        controller.probe = uiView
    }
}
