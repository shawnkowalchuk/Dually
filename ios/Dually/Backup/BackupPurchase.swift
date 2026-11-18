import DuallyShared
import Foundation
import StoreKit

// The online backup's one-time purchase on the iPhone (ADR-013, ADR-016), through StoreKit 2:
// the App Store product `cloud_backup`, US$19.99, as Google Play's on Android. StoreKit hands
// every purchase over signed by Apple; that signed transaction goes to the purchase check on
// the server, which unlocks the account (IosCloud). Nothing here decides what a purchase
// unlocks.

/// How StoreKit's purchase sheet ended.
enum StorePurchase {
    /// Paid for: Apple's signed transaction, and the transaction, to finish once the server
    /// has given its answer for good.
    case bought(signed: String, transaction: Transaction)
    case pending
    case cancelled
    case notOffered
    case failed(String)
}

@MainActor
enum BackupPurchase {
    nonisolated static let productID = "cloud_backup"

    /// The product, once the App Store has said what it is.
    private static var product: Product?
    private static var listening: Task<Void, Never>?

    /// The price as the App Store shows it to this person ("$19.99", "CA$27.99"), or nil.
    static func price() async -> String? {
        await loaded()?.displayPrice
    }

    /// StoreKit's own sheet. The purchase carries the account's `appAccountToken` (ADR-016), so
    /// that the server unlocks the account that bought it and no other.
    static func buy(accountToken: String) async -> StorePurchase {
        guard let product = await loaded() else { return .notOffered }
        var options: Set<Product.PurchaseOption> = []
        if let token = UUID(uuidString: accountToken) { options.insert(.appAccountToken(token)) }
        do {
            switch try await product.purchase(options: options) {
            case .success(let verification):
                guard case .verified(let transaction) = verification else {
                    return .failed("not verified by StoreKit")
                }
                return .bought(signed: verification.jwsRepresentation, transaction: transaction)
            case .pending:
                return .pending
            case .userCancelled:
                return .cancelled
            @unknown default:
                return .failed("an answer this build does not know")
            }
        } catch {
            return .failed("\((error as NSError).domain) \((error as NSError).code)")
        }
    }

    /// "Restore purchase": asks the App Store to bring this Apple account's purchases up to
    /// date (it may ask the person to sign in to it), then hands over each purchase of the
    /// backup it holds, signed.
    static func owned() async -> [String] {
        try? await AppStore.sync()
        var signed: [String] = []
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result, transaction.productID == productID {
                signed.append(result.jwsRepresentation)
            }
        }
        return signed
    }

    /// Purchases StoreKit delivers by itself (approved later, made on another device of the
    /// Apple account, left unfinished by a check that could not be reached): each goes to the
    /// check, and is finished once the server's answer is for good. Started once, at the
    /// app's start, as Apple asks.
    static func listen(core: DuallyCore) {
        guard listening == nil else { return }
        listening = Task.detached {
            for await result in Transaction.updates {
                guard case .verified(let transaction) = result,
                      transaction.productID == productID
                else { continue }
                let finish = (try? await core.cloud.delivered(signed: result.jwsRepresentation))?.boolValue ?? false
                if finish { await transaction.finish() }
            }
        }
    }

    private static func loaded() async -> Product? {
        if let product { return product }
        product = try? await Product.products(for: [productID]).first
        return product
    }
}
