import DuallyShared
import SwiftUI

// The vehicles on the iPhone (ADR-018, 2026-11-17; Shawn's choice "Match by name, ask once").
// The iPhone cannot pair a vehicle by its Bluetooth address, so each is named: picked from the
// ones the account's other phone has (the backup brings them), or added by name. Each has its
// own odometer, the report says which vehicle each trip was in, and the Shortcuts actions Start
// a trip and End a trip name the vehicle they are for. With one vehicle nothing here is needed.
struct VehiclesTile: View {
    let core: DuallyCore
    /// Called after a vehicle is added or removed: the odometers are read again.
    let changed: () -> Void
    @State private var vehicles: [IosVehicle] = []
    @State private var offered: [IosVehicle] = []
    @State private var adding = false
    @State private var typed = ""
    @State private var removing: IosVehicle?

    var body: some View {
        Tile {
            Text("Vehicles")
                .font(Typography.body(17, weight: .semibold))
                .foregroundStyle(Palette.text)
            if vehicles.isEmpty {
                Text("With one vehicle there is nothing to add. With more than one, add each here: each gets its own odometer, and the report says which vehicle every trip was in.")
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                ForEach(vehicles, id: \.id) { vehicle in
                    HStack {
                        Text(vehicle.name)
                            .font(Typography.body(17))
                            .foregroundStyle(Palette.text)
                        Spacer()
                        Button("Remove") { removing = vehicle }
                            .font(Typography.body(15, weight: .semibold))
                            .foregroundStyle(Palette.accent)
                            .accessibilityLabel("Remove \(vehicle.name)")
                    }
                }
                Text("In the Shortcuts app, pick the vehicle in each automation's Start a trip and End a trip action. A trip whose action names no vehicle counts for \(vehicles[0].name).")
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if adding {
                if !offered.isEmpty {
                    Text("From your other phone")
                        .font(Typography.body(13, weight: .semibold))
                        .foregroundStyle(Palette.textSecondary)
                    ForEach(offered, id: \.id) { vehicle in
                        WideButton(title: vehicle.name, filled: false) {
                            Task { await change { try await core.vehicles.pick(id: vehicle.id) } }
                        }
                    }
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("A new vehicle's name")
                        .font(Typography.body(13, weight: .semibold))
                        .foregroundStyle(Palette.textSecondary)
                    TextField("Work truck", text: $typed)
                        .font(Typography.body(16))
                        .padding(12)
                        .background(Palette.controlFill, in: RoundedRectangle(cornerRadius: 12))
                        .foregroundStyle(Palette.text)
                        .accessibilityLabel("A new vehicle's name")
                }
                HStack(spacing: 8) {
                    WideButton(title: "Cancel", filled: false) { adding = false }
                    WideButton(title: "Add", filled: true) {
                        Task { await change { try await core.vehicles.add(name: typed) } }
                    }
                }
            } else {
                WideButton(title: "Add a vehicle", filled: false) {
                    typed = ""
                    adding = true
                }
            }
        }
        .task { await read() }
        .confirmationDialog(
            "Remove \(removing?.name ?? "")?",
            isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }),
            titleVisibility: .visible,
            presenting: removing
        ) { vehicle in
            Button("Remove", role: .destructive) {
                Task {
                    await change {
                        try await core.vehicles.remove(id: vehicle.id)
                        return KotlinBoolean(value: true)
                    }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("Its trips keep its name. Its automations in the Shortcuts app still run, and their trips count for the first vehicle.")
        }
    }

    /// Adds or removes, then reads the list again; a refused add (a blank name) stays open.
    private func change(_ action: () async throws -> KotlinBoolean) async {
        let done = (try? await action())?.boolValue ?? false
        if done { adding = false }
        await read()
        changed()
    }

    private func read() async {
        vehicles = (try? await core.vehicles.list()) ?? []
        offered = (try? await core.vehicles.offered()) ?? []
    }
}
