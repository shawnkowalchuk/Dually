import DuallyShared
import SwiftUI

// Setup on the iPhone (phase 4d, second part), as Android's Setup screen: how many requirements
// are ready, then each one with its state, one sentence and the button that fixes it. Here they
// are the location, the vehicle, and the two Shortcuts automations that start and end a trip,
// with the steps to make them and the check that each has run.
struct SetupView: View {
    @ObservedObject var model: SetupModel
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        PinnedScreen(title: "Setup") {
            if let screen = model.screen {
                ReadyTile(screen: screen)
                ForEach(Array(screen.rows.enumerated()), id: \.offset) { _, row in
                    SetupRowTile(row: row, model: model)
                }
            }
        }
        .task { model.refresh() }
        .onChange(of: scenePhase) { phase in
            // Back from the Shortcuts app or Settings: an automation may have run, a permission
            // may have changed.
            if phase == .active { model.refresh() }
        }
    }
}

/// How many requirements are ready, and what that means for trips.
private struct ReadyTile: View {
    let screen: SetupScreen

    private var words: String {
        if !screen.complete {
            return "Until the rows for trips are ready, a trip starts only with Start trip on Home."
        }
        if screen.ready < screen.total {
            return "Trips start and end with your vehicle, even with Dually closed. The rows still open are for the driving alert."
        }
        return "Trips start and end with your vehicle, even with Dually closed."
    }

    var body: some View {
        Tile(ground: screen.complete ? Palette.tile : Palette.accent) {
            Text("\(screen.ready) of \(screen.total) ready")
                .font(Typography.display(24))
                .foregroundStyle(screen.complete ? Palette.text : Palette.markLetter)
            Text(words)
                .font(Typography.body(15))
                .foregroundStyle(screen.complete ? Palette.textSecondary : Palette.markLetter)
        }
    }
}

private struct SetupRowTile: View {
    let row: SetupRow
    @ObservedObject var model: SetupModel

    var body: some View {
        Tile {
            HStack(alignment: .firstTextBaseline) {
                Text(row.title)
                    .font(Typography.body(17, weight: .semibold))
                    .foregroundStyle(Palette.text)
                Spacer()
                Text(stateWords)
                    .font(Typography.body(14, weight: .semibold))
                    .foregroundStyle(row.state == SetupState.needsYou ? Palette.amber : Palette.textSecondary)
            }
            Text(row.sentence)
                .font(Typography.body(15))
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            if !row.steps.isEmpty && row.state != SetupState.ready {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(Array(row.steps.enumerated()), id: \.offset) { index, step in
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text("\(index + 1)")
                                .font(Typography.figure(14))
                                .foregroundStyle(Palette.accent)
                            Text(step)
                                .font(Typography.body(15))
                                .foregroundStyle(Palette.text)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
                .padding(.top, 4)
            }
            buttons.padding(.top, 6)
        }
    }

    @ViewBuilder
    private var buttons: some View {
        if row.action == SetupAction.chooseVehicle && model.vehicle == nil {
            HStack(spacing: 10) {
                WideButton(title: "CarPlay", filled: true) { model.choose(.carplay) }
                WideButton(title: "Bluetooth", filled: true) { model.choose(.bluetooth) }
            }
        } else if row.action == SetupAction.chooseVehicle, let button = row.button {
            WideButton(title: button, filled: false) {
                model.choose(model.vehicle == .carplay ? .bluetooth : .carplay)
            }
        } else if let action = row.action, let button = row.button {
            VStack(spacing: 8) {
                WideButton(title: button, filled: row.state == SetupState.needsYou) {
                    model.perform(action)
                }
                if let confirm = row.confirmButton, let confirmAction = row.confirmAction {
                    WideButton(title: confirm, filled: false) { model.perform(confirmAction) }
                }
            }
        }
    }

    private var stateWords: String {
        switch row.state {
        case SetupState.ready: return "Ready"
        case SetupState.needsYou: return "Needs you"
        default: return "Waiting"
        }
    }
}
