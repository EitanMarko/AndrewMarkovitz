import SwiftUI

struct DaySetupView: View {
    @ObservedObject var vm: AppViewModel
    @State private var date: Date
    @State private var start: Int
    @State private var end: Int
    @State private var useAsDefault = false
    @State private var saved = false

    init(vm: AppViewModel) {
        self.vm = vm
        let day = vm.schedule(for: vm.today)
        _date = State(initialValue: vm.today)
        _start = State(initialValue: day.start)
        _end = State(initialValue: day.end)
    }

    var body: some View {
        Form {
            Section(date.formatted(date: .complete, time: .omitted)) {
                TimeWheelPicker(title: "Start of day", minutes: $start)
                TimeWheelPicker(title: "End of day", minutes: $end)
            }
            Section {
                Toggle("Use as my usual day hours", isOn: $useAsDefault)
            } footer: {
                Text("Save your hours for today. Turn on usual day hours to also use them for other dates that don't have their own hours.")
            }
            Section {
                Button("Save Day Hours") {
                    if vm.setDayHours(start: start, end: end, on: date) {
                        if useAsDefault {
                            vm.dayStartMinutes = start
                            vm.dayEndMinutes = end
                            _ = vm.applyDayHours()
                        }
                        saved = true
                    }
                }
                .disabled(end <= start)
                if end <= start {
                    Text("The end of your day must be after the start.").font(.caption).foregroundStyle(.red)
                }
                if saved { Label("Day hours saved", systemImage: "checkmark.circle.fill").foregroundStyle(.green) }
            }
        }
        .navigationTitle("Day Hours")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: start) { _, _ in saved = false }
        .onChange(of: end) { _, _ in saved = false }
        .onChange(of: useAsDefault) { _, _ in saved = false }
        .onChange(of: vm.today) { _, newDate in
            // A foregrounded app can cross midnight while this page is still open.
            if vm.dayKey(newDate) != vm.dayKey(date) {
                let day = vm.schedule(for: newDate)
                date = newDate
                start = day.start
                end = day.end
                saved = false
            }
        }
    }
}
