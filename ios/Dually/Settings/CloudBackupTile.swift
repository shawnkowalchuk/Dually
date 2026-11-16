import AuthenticationServices
import DuallyShared
import SwiftUI

// The Online backup tile in Settings (phase 4h), Android's CloudBackupTile: what the backup is
// and its price before it is bought; the account and "On" after; Merge, or Use only the
// backup's trips, when this iPhone has trips of its own and the other phones theirs (ADR-017);
// Sign out and Delete. Every word comes from
// the shared code; the questions are asked before anything is replaced or deleted.
struct CloudBackupTile: View {
    @StateObject private var model: CloudBackupModel
    @State private var asking: Asked?

    private enum Asked {
        case useBackup, delete
    }

    init(core: DuallyCore) {
        _model = StateObject(wrappedValue: CloudBackupModel(core: core))
    }

    var body: some View {
        Tile {
            if let tile = model.tile {
                HStack {
                    Text(tile.title)
                        .font(Typography.body(17, weight: .semibold))
                        .foregroundStyle(Palette.text)
                    Spacer()
                    if let on = tile.on {
                        Text(on)
                            .font(Typography.body(14, weight: .bold))
                            .foregroundStyle(Palette.accent)
                    }
                }
                Text(tile.text)
                    .font(Typography.body(14))
                    .foregroundStyle(Palette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                if let note = model.note {
                    Text(note.text)
                        .font(Typography.body(14))
                        .foregroundStyle(note.good ? Palette.text : Palette.amber)
                        .fixedSize(horizontal: false, vertical: true)
                }
                buttons(tile)
                    .disabled(model.working)
                    .opacity(model.working ? 0.5 : 1)
            }
        }
        .task { await model.load() }
        .sheet(isPresented: $model.signingIn) {
            if let words = model.tile?.signIn {
                SignInSheet(words: words, cancel: model.tile?.cancel ?? "", model: model)
            }
        }
        .alert(
            question?.title ?? "",
            isPresented: Binding(get: { asking != nil }, set: { if !$0 { asking = nil } }),
            presenting: question
        ) { question in
            Button(question.confirm, role: asking == .delete ? .destructive : nil) { answerYes() }
            Button(model.tile?.cancel ?? "", role: .cancel) { asking = nil }
        } message: { question in
            Text(question.text)
        }
    }

    @ViewBuilder
    private func buttons(_ tile: CloudTile) -> some View {
        VStack(spacing: 8) {
            if let purchase = tile.purchase {
                WideButton(title: purchase, filled: true) { model.purchase() }
            }
            if let restore = tile.restorePurchase {
                WideButton(title: restore, filled: false) { model.restorePurchase() }
            }
            if let merge = tile.merge {
                WideButton(title: merge, filled: true) { model.merge() }
            }
            if let useBackup = tile.useBackup {
                WideButton(title: useBackup.button, filled: false) { asking = .useBackup }
            }
            if let signOut = tile.signOut {
                WideButton(title: signOut, filled: false) { model.signOut() }
            }
            if let delete = tile.deleteAccount {
                WideButton(title: delete.button, filled: false) { asking = .delete }
            }
        }
        .padding(.top, 6)
    }

    private var question: CloudQuestion? {
        switch asking {
        case .useBackup: model.tile?.useBackup
        case .delete: model.tile?.deleteAccount
        case nil: nil
        }
    }

    private func answerYes() {
        let answered = asking
        asking = nil
        switch answered {
        case .useBackup: model.useBackupOnly()
        case .delete: model.delete()
        case nil: break
        }
    }
}

/// The sign-in sheet before a purchase or a restore: Apple's own button, then Google's.
private struct SignInSheet: View {
    @Environment(\.colorScheme) private var colorScheme
    let words: CloudSignIn
    let cancel: String
    @ObservedObject var model: CloudBackupModel

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(words.title)
                .font(Typography.display(24))
                .foregroundStyle(Palette.text)
            Text(words.text)
                .font(Typography.body(15))
                .foregroundStyle(Palette.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            SignInWithAppleButton(.signIn, onRequest: BackupSignIn.prepare) { result in
                model.signedInWithApple(result)
            }
            // Apple's white button on the dark page, its black one on the light page.
            .signInWithAppleButtonStyle(colorScheme == .light ? .black : .white)
            .frame(height: 46)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            WideButton(title: words.google, filled: false) { model.signInWithGoogle() }
            WideButton(title: cancel, filled: false) { model.signingIn = false }
            Spacer(minLength: 0)
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.page.ignoresSafeArea())
        .presentationDetents([.medium])
    }
}
