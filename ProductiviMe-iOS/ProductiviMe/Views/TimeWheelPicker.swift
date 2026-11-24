import SwiftUI

/// Scrollable hour/minute wheels bound to a total-minutes value.
/// Minutes advance in 5-minute steps because the engine requires
/// all times and durations to sit on 5-minute boundaries.
struct TimeWheelPicker: View {
    let title: String
    @Binding var minutes: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            HStack(spacing: 0) {
                Picker("\(title) hours", selection: hour) {
                    ForEach(0..<24, id: \.self) { Text("\($0)").tag($0) }
                }
                .pickerStyle(.wheel)
                .frame(maxWidth: .infinity)
                .clipped()

                Text(":")
                    .font(.headline)

                Picker("\(title) minutes", selection: minute) {
                    ForEach(Array(stride(from: 0, through: 55, by: 5)), id: \.self) {
                        Text(String(format: "%02d", $0)).tag($0)
                    }
                }
                .pickerStyle(.wheel)
                .frame(maxWidth: .infinity)
                .clipped()
            }
            .frame(height: 110)
        }
    }

    private var hour: Binding<Int> {
        Binding(
            get: { minutes / 60 },
            set: { minutes = $0 * 60 + (minutes % 60) })
    }

    private var minute: Binding<Int> {
        Binding(
            get: { minutes % 60 },
            set: { minutes = (minutes / 60) * 60 + $0 })
    }
}
