import Foundation

// MARK: - Response models (mirror the Java ScheduleResult records)

struct ScheduledActivity: Codable, Identifiable {
    var id: String { name }
    let name: String
    let durationMinutes: Int
    let value: Int
}

struct ScheduledInterval: Codable, Identifiable {
    var id: String { "\(start)-\(end)" }
    let start: String
    let end: String
    let durationMinutes: Int
    let activities: [ScheduledActivity]
}

struct ScheduleResult: Codable {
    let intervals: [ScheduledInterval]
    let totalValue: Int
}

struct EffectivenessResponse: Codable {
    let percentage: String
    let raw: Double
}

// MARK: - Errors

enum APIError: LocalizedError {
    case serverError(String)

    var errorDescription: String? {
        if case .serverError(let msg) = self {
            // Strip the "Error: " prefix the Java controller adds
            return msg.hasPrefix("Error: ") ? String(msg.dropFirst(7)) : msg
        }
        return nil
    }
}

// MARK: - NetworkManager

@MainActor
final class NetworkManager: ObservableObject {

    static let shared = NetworkManager()

    // When testing on a real iPhone, replace "localhost" with your Mac's local IP
    // e.g. "http://192.168.1.42:8080"
    let baseURL = "http://localhost:8080"

    private init() {}

    // MARK: Day

    func startDay(startTime: String, endTime: String) async throws {
        try await post("/day/start", body: ["startTime": startTime, "endTime": endTime])
    }

    // MARK: Activities

    func addSetActivity(name: String, startTime: String, endTime: String) async throws {
        try await post("/activities/set",
                       body: ["name": name, "startTime": startTime, "endTime": endTime])
    }

    func addFlexActivity(name: String, duration: String, value: Int, doToday: Bool) async throws {
        try await post("/activities/flexible",
                       body: ["name": name, "duration": duration, "value": value, "doToday": doToday])
    }

    // MARK: Schedule

    func generateSchedule() async throws -> ScheduleResult {
        let url = URL(string: baseURL + "/schedule/generate")!
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        let (data, response) = try await URLSession.shared.data(for: req)
        try validate(data: data, response: response)
        return try JSONDecoder().decode(ScheduleResult.self, from: data)
    }

    // MARK: Effectiveness

    func getEffectiveness() async throws -> EffectivenessResponse {
        let url = URL(string: baseURL + "/day/effectiveness")!
        let (data, response) = try await URLSession.shared.data(from: url)
        try validate(data: data, response: response)
        return try JSONDecoder().decode(EffectivenessResponse.self, from: data)
    }

    // MARK: Helpers

    private func post(_ path: String, body: [String: Any]) async throws {
        let url = URL(string: baseURL + path)!
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: req)
        try validate(data: data, response: response)
    }

    private func validate(data: Data, response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            let msg = String(data: data, encoding: .utf8) ?? "Unknown server error"
            throw APIError.serverError(msg)
        }
    }
}
