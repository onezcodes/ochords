import AuthenticationServices
import UIKit
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

/// Sign in with Apple and Google on the phone: each yields a token the API verifies (`auth/apple`, `auth/google`).
@MainActor
enum SocialSignIn {
    struct AppleResult {
        var identityToken: String
        /// Apple only gives the name on the first sign-in for this app.
        var name: String?
    }

    enum Failure: LocalizedError {
        case cancelled, unavailable(String)
        var errorDescription: String? {
            switch self {
            case .cancelled: nil
            case .unavailable(let why): why
            }
        }
    }

    /// Google needs its OAuth client ID (`OChordsGoogleClientID` in Info.plist, from project.yml).
    static var googleClientID: String? {
        let id = (Bundle.main.object(forInfoDictionaryKey: "OChordsGoogleClientID") as? String ?? "").trimmingCharacters(in: .whitespaces)
        return id.isEmpty ? nil : id
    }

    static var googleAvailable: Bool {
        #if canImport(GoogleSignIn)
        return googleClientID != nil
        #else
        return false
        #endif
    }

    /// Shows Apple's sheet. Throws `.cancelled` when the player backs out.
    static func apple() async throws -> AppleResult {
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        let controller = ASAuthorizationController(authorizationRequests: [request])
        let delegate = AppleDelegate()
        controller.delegate = delegate
        controller.presentationContextProvider = delegate
        return try await withCheckedThrowingContinuation { continuation in
            delegate.continuation = continuation
            Self.retainedApple = delegate
            controller.performRequests()
        }
    }

    private static var retainedApple: AppleDelegate?

    private final class AppleDelegate: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
        var continuation: CheckedContinuation<AppleResult, Error>?

        func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let data = credential.identityToken, let token = String(data: data, encoding: .utf8) else {
                finish(.failure(Failure.unavailable("Apple didn’t return a sign-in token. Try again.")))
                return
            }
            let name = [credential.fullName?.givenName, credential.fullName?.familyName].compactMap { $0 }.joined(separator: " ")
            finish(.success(AppleResult(identityToken: token, name: name.isEmpty ? nil : name)))
        }

        func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
            if let e = error as? ASAuthorizationError, e.code == .canceled { finish(.failure(Failure.cancelled)) } else {
                finish(.failure(Failure.unavailable("Apple sign-in didn’t work. Check your Apple Account settings and try again.")))
            }
        }

        func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
            UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first(where: \.isKeyWindow) ?? ASPresentationAnchor()
        }

        private func finish(_ result: Result<AppleResult, Error>) {
            continuation?.resume(with: result)
            continuation = nil
            SocialSignIn.retainedApple = nil
        }
    }

    /// Google's sheet; returns the ID token for the API.
    static func google() async throws -> String {
        #if canImport(GoogleSignIn)
        guard let clientID = googleClientID else { throw Failure.unavailable("Google sign-in isn’t set up yet. Log in with your email for now.") }
        guard let root = AdService.topViewController() else { throw Failure.unavailable("Google sign-in couldn’t open. Try again.") }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: root)
            guard let token = result.user.idToken?.tokenString else {
                throw Failure.unavailable("Google didn’t return a sign-in token. Try again.")
            }
            return token
        } catch let e as NSError where e.domain == kGIDSignInErrorDomain && e.code == GIDSignInError.canceled.rawValue {
            throw Failure.cancelled
        } catch let f as Failure {
            throw f
        } catch {
            throw Failure.unavailable("Google sign-in didn’t work. Try again.")
        }
        #else
        throw Failure.unavailable("Google sign-in isn’t set up yet. Log in with your email for now.")
        #endif
    }

    /// Google's sign-in flow comes back through a URL; the app passes every opened URL here first.
    static func handle(_ url: URL) -> Bool {
        #if canImport(GoogleSignIn)
        return GIDSignIn.sharedInstance.handle(url)
        #else
        return false
        #endif
    }
}
