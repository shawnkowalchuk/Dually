import DuallyShared
import SwiftUI

// What can be done with one trip (phase 4c): mark it Business or Personal, give it a label, change
// its times, places and distance (the edit sheet, 2026-11-13), and delete it (or restore or count
// it), each through the shared code as on Android.
struct TripSheet: View {
    let trip: TripText
    @ObservedObject var model: TripsModel
    let onClose: () -> Void

    @State private var label: String
    @State private var choices: [String] = []
    @State private var askingToDelete = false
    @State private var editing = false

    init(trip: TripText, model: TripsModel, onClose: @escaping () -> Void) {
        self.trip = trip
        self.model = model
        self.onClose = onClose
        _label = State(initialValue: trip.label ?? "")
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(trip.time).font(Typography.display(22)).foregroundStyle(Palette.text)
                        if let route = trip.route {
                            Text(route).font(Typography.body(15)).foregroundStyle(Palette.textSecondary)
                        }
                        if let distance = trip.distance {
                            Text("\(distance) \(trip.unit)").font(Typography.figure(20)).foregroundStyle(Palette.text)
                        }
                        if let note = trip.note {
                            Text(note).font(Typography.body(14)).foregroundStyle(Palette.amber)
                        }
                    }
                    if trip.canMarkBusiness || trip.canMarkPersonal {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Saved as \(trip.category ?? "not sorted yet")")
                                .font(Typography.body(15))
                                .foregroundStyle(Palette.textSecondary)
                            if trip.canMarkBusiness {
                                WideButton(title: "Mark Business", filled: true) {
                                    Task { await model.mark(trip, business: true); onClose() }
                                }
                            }
                            if trip.canMarkPersonal {
                                WideButton(title: "Mark Personal", filled: true) {
                                    Task { await model.mark(trip, business: false); onClose() }
                                }
                            }
                        }
                    }
                    if trip.canLabel {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("What was it for?")
                                .font(Typography.body(15, weight: .semibold))
                                .foregroundStyle(Palette.text)
                            TextField("Site visit, Supplier, Service call…", text: $label)
                                .font(Typography.body(16))
                                .padding(12)
                                .background(Palette.controlFill, in: RoundedRectangle(cornerRadius: 12))
                                .foregroundStyle(Palette.text)
                            if !choices.isEmpty {
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack {
                                        ForEach(choices, id: \.self) { choice in
                                            Button(choice) { label = choice }
                                                .font(Typography.body(14))
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 8)
                                                .background(Palette.controlFill, in: Capsule())
                                                .foregroundStyle(Palette.text)
                                        }
                                    }
                                }
                            }
                            WideButton(title: "Save the label", filled: false) {
                                Task { await model.setLabel(trip, to: label); onClose() }
                            }
                        }
                    }
                    if trip.canEdit {
                        WideButton(title: EnglishFormWordsKt.englishFormText(name: "trip_edit_title"), filled: false) {
                            editing = true
                        }
                    }
                    if let words = trip.correctionWords {
                        WideButton(title: words, filled: false) {
                            if trip.correction == "DELETE" {
                                askingToDelete = true
                            } else {
                                Task { await model.correct(trip); onClose() }
                            }
                        }
                    }
                }
                .padding(20)
            }
            .background(Palette.tile.ignoresSafeArea())
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", action: onClose).foregroundStyle(Palette.text)
                }
            }
            .confirmationDialog("Delete this trip?", isPresented: $askingToDelete, titleVisibility: .visible) {
                Button("Delete", role: .destructive) {
                    Task { await model.correct(trip); onClose() }
                }
            } message: {
                Text("It leaves the list and the month's total. It is not destroyed: switch on \"Show deleted and discarded\" to see it again and restore it.")
            }
        }
        .sheet(isPresented: $editing) {
            TripEditSheet(load: { await model.editor(for: trip) }) { saved in
                editing = false
                if saved {
                    Task { await model.load() }
                    onClose()
                }
            }
        }
        .presentationDetents([.medium, .large])
        .task { choices = await model.labelChoices(for: trip) }
    }
}
