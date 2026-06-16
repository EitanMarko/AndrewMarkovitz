import SwiftUI

struct DaySetupView: View {
    @ObservedObject var vm: AppViewModel

    var body: some View {
        NavigationStack {
            Form {
                Section("Day hours  (24-hr format, e.g. 8:00)") {
                    LabeledContent("Start") {
                        TextField("e.g. 8:00", text: $vm.dayStartTime)
                            .multilineTextAlignment(.trailing)
                            .keyboardType(.numbersAndPunctuation)
                    }
                    LabeledContent("End") {
                        TextField("e.g. 18:00", text: $vm.dayEndTime)
                            .multilineTextAlignment(.trailing)
                            .keyboardType(.numbersAndPunctuation)
                    }
                }

                if vm.dayActive {
                    Section {
                        Label(
                            "Day active: \(vm.dayStartTime) – \(vm.dayEndTime)",
                            systemImage: "checkmark.circle.fill"
                        )
                        .foregroundStyle(.green)

                        if let eff = vm.effectiveness {
                            Label(
                                String(format: "Effectiveness: %.0f%%", eff * 100),
                                systemImage: "chart.bar.fill"
                            )
                            .foregroundStyle(.blue)
                        }
                    }
                }

                Section {
                    Button(vm.dayActive ? "Reset Day" : "Start Day") {
                        vm.startDay()
                    }
                    .disabled(vm.dayStartTime.isEmpty || vm.dayEndTime.isEmpty)
                    .frame(maxWidth: .infinity, alignment: .center)
                }
            }
            .navigationTitle("ProductiviMe")
        }
    }
}
