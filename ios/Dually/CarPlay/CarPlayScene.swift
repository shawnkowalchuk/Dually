import CarPlay
import Combine
import DuallyShared

// Dually on the truck's CarPlay display (phase 4f, 2026-11-14; ADR-014), Android Auto's screen
// on the iPhone: one screen of Apple's information template, nothing to scroll. "This trip"
// says what is being recorded and what it will be saved as, with its distance and time;
// "Business" says today, the month and the odometer. Then Start trip or End trip, and while a
// trip records, Make Personal or Make Business. Every word and figure is the shared code's
// (IosCar); the trip is the one TripSession records, so the phone and the car show the same.
// Redrawn every 15 seconds, as Android Auto's, and at once when a trip starts or ends.
@MainActor
final class CarPlayScene: UIResponder, CPTemplateApplicationSceneDelegate {
    private var interface: CPInterfaceController?
    private var template: CPInformationTemplate?
    private var timer: Timer?
    private var watching: AnyCancellable?
    private var shown: CarText?
    private let session = TripSession.shared

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        interface = interfaceController
        watching = session.$recording
            .map { $0 != nil }
            .removeDuplicates()
            .sink { [weak self] _ in Task { await self?.redraw() } }
        timer = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in
            Task { await self?.redraw() }
        }
        Task { await redraw() }
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnectInterfaceController interfaceController: CPInterfaceController
    ) {
        timer?.invalidate()
        timer = nil
        watching = nil
        interface = nil
        template = nil
        shown = nil
    }

    /// Reads the screen again and redraws it if anything on it changed.
    private func redraw() async {
        guard let interface, let text = try? await session.core.car.screen() else { return }
        guard text != shown else { return }
        shown = text
        let items = [
            CPInformationItem(title: text.tripTitle, detail: text.tripLine),
            CPInformationItem(title: text.businessTitle, detail: text.businessLine),
        ]
        let actions = buttons(text)
        if let template {
            template.items = items
            template.actions = actions
        } else {
            let made = CPInformationTemplate(title: text.title, layout: .leading, items: items, actions: actions)
            template = made
            interface.setRootTemplate(made, animated: false, completion: nil)
        }
    }

    /// Start trip or End trip, the screen's main button, and the switch while a trip records.
    private func buttons(_ text: CarText) -> [CPTextButton] {
        let recording = text.recording
        var buttons = [
            CPTextButton(title: text.button, textStyle: .confirm) { [weak self] _ in
                Task {
                    guard let self else { return }
                    if recording {
                        await self.session.endFromCar()
                    } else {
                        await self.session.startFromCar()
                    }
                    await self.redraw()
                }
            },
        ]
        if let switchTitle = text.switchButton {
            buttons.append(CPTextButton(title: switchTitle, textStyle: .normal) { [weak self] _ in
                Task {
                    guard let self else { return }
                    _ = try? await self.session.core.car.switchCategory()
                    await self.redraw()
                }
            })
        }
        return buttons
    }
}
