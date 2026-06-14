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
                    }
                }

                Section {
                    Button(vm.dayActive ? "Reset Day" : "Start Day") {
                        Task { await vm.startDay() }
                    }
                    .disabled(vm.dayStartTime.isEmpty || vm.dayEndTime.isEmpty || vm.isLoading)
                    .frame(maxWidth: .infinity, alignment: .center)
                }
            }
            .navigationTitle("ProductiviMe")
        }
    }
}
