import Foundation
import StoreKit

/// O Chords Pro through StoreKit 2: the two auto-renewing subscriptions, what the App Store says the player is
/// entitled to, and purchases/restores. Pro on this phone is whatever the App Store says; the account is told
/// about each verified transaction so Pro follows the player to their other devices (`AppBackend.syncSubscription`).
@Observable
final class StoreService: @unchecked Sendable {
    static let monthlyID = "com.onezcodes.ochords.pro.monthly"
    static let yearlyID = "com.onezcodes.ochords.pro.yearly"
    static let productIDs = [yearlyID, monthlyID]

    private(set) var products: [Product] = []
    /// An active Pro subscription (or trial), as verified by the App Store on this phone.
    private(set) var isPro = false
    /// The signed transaction behind `isPro`, for the account.
    private(set) var transactionJWS: String?
    private(set) var loaded = false
    /// Called whenever the entitlement changes (renewals, refunds, purchases on another device).
    var onChange: ((Bool) -> Void)?
    private var updates: Task<Void, Never>?

    enum PurchaseOutcome { case purchased, cancelled, pending }

    enum StoreError: LocalizedError {
        case unavailable, failedVerification
        var errorDescription: String? {
            switch self {
            case .unavailable: "The App Store isn’t available right now. Try again in a moment."
            case .failedVerification: "The App Store couldn’t confirm that purchase."
            }
        }
    }

    /// Loads the products and the current entitlement, and keeps following App Store updates.
    func start() async {
        if updates == nil {
            updates = Task { [weak self] in
                for await update in Transaction.updates {
                    guard let self else { return }
                    if case .verified(let t) = update { await t.finish() }
                    await self.refreshEntitlement()
                }
            }
        }
        await loadProducts()
        await refreshEntitlement()
        loaded = true
    }

    func loadProducts() async {
        guard products.isEmpty else { return }
        let found = (try? await Product.products(for: Self.productIDs)) ?? []
        products = Self.productIDs.compactMap { id in found.first { $0.id == id } }
    }

    var yearly: Product? { products.first { $0.id == Self.yearlyID } }
    var monthly: Product? { products.first { $0.id == Self.monthlyID } }

    /// "save 33%" for the yearly plan against twelve monthly payments; nil when the prices aren't known.
    var yearlySavingPercent: Int? {
        guard let y = yearly?.price, let m = monthly?.price, m > 0 else { return nil }
        let saving = (1 - y / (m * 12)) * 100
        let rounded = NSDecimalNumber(decimal: saving).intValue
        return rounded > 0 ? rounded : nil
    }

    /// Whether a product's introductory offer (the free trial) is still available to this player.
    func trialAvailable(for product: Product) async -> Bool {
        guard let intro = product.subscription?.introductoryOffer, intro.paymentMode == .freeTrial else { return false }
        return await product.subscription?.isEligibleForIntroOffer ?? false
    }

    func purchase(_ product: Product) async throws -> PurchaseOutcome {
        let result: Product.PurchaseResult
        do {
            result = try await product.purchase()
        } catch StoreKitError.userCancelled {
            return .cancelled
        } catch {
            throw StoreError.unavailable
        }
        switch result {
        case .success(let verification):
            guard case .verified(let transaction) = verification else { throw StoreError.failedVerification }
            await transaction.finish()
            await refreshEntitlement()
            return .purchased
        case .userCancelled:
            return .cancelled
        case .pending:
            return .pending
        @unknown default:
            return .cancelled
        }
    }

    /// "Restore purchases": asks the App Store for this Apple ID's transactions, then re-reads the entitlement.
    func restore() async {
        try? await AppStore.sync()
        await refreshEntitlement()
    }

    /// Re-reads the current entitlement from the App Store (verified transactions only).
    func refreshEntitlement() async {
        var active: (Transaction, String)? = nil
        for await result in Transaction.currentEntitlements {
            guard case .verified(let t) = result, Self.productIDs.contains(t.productID), t.revocationDate == nil else { continue }
            if let expiry = t.expirationDate, expiry < Date() { continue }
            // Prefer the longest-running plan when both show up during an upgrade.
            if active == nil || t.productID == Self.yearlyID { active = (t, result.jwsRepresentation) }
        }
        let was = isPro
        isPro = active != nil
        transactionJWS = active?.1
        if was != isPro { onChange?(isPro) }
    }
}
