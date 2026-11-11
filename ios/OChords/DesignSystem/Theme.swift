import SwiftUI
import UIKit

// MARK: - Colour tokens
// Source: "HANDOVER · DESIGN TOKENS (GLASS UI)" + "DARK MODE MAPPING" on the design canvas.

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255,
                  green: CGFloat((hex >> 8) & 0xFF) / 255,
                  blue: CGFloat(hex & 0xFF) / 255,
                  alpha: alpha)
    }
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(uiColor: UIColor(hex: hex, alpha: alpha))
    }

    /// A colour that switches with the interface style.
    init(light: UIColor, dark: UIColor) {
        self.init(uiColor: UIColor { $0.userInterfaceStyle == .dark ? dark : light })
    }
}

enum OC {
    // Brand
    static let gold = Color(hex: 0xFFB800)
    static let goldTop = Color(hex: 0xFFCB3D)
    static let goldBottom = Color(hex: 0xFFB300)
    /// Gold used for text on light surfaces (#8F5E00) / dark (#FFC940).
    static let goldText = Color(light: UIColor(hex: 0x8F5E00), dark: UIColor(hex: 0xFFC940))
    static let goldDeep = Color(hex: 0x6B4600)
    static let goldTint = Color(light: UIColor(hex: 0xFFF4D6), dark: UIColor(hex: 0xFFB800, alpha: 0.16))

    // Ink & neutrals
    static let ink = Color(light: UIColor(hex: 0x171614), dark: UIColor(hex: 0xF4F1EA))
    static let inkFixed = Color(hex: 0x171614)
    static let paper = Color(hex: 0xFAFAF8)
    static let muted = Color(light: UIColor(hex: 0x6B6660), dark: UIColor(hex: 0xA8A49E))
    static let faint = Color(light: UIColor(hex: 0x8E8A84), dark: UIColor(hex: 0x8E8A84))
    static let line = Color(light: UIColor(hex: 0xF1F0ED), dark: UIColor(white: 1, alpha: 0.10))
    static let border = Color(light: UIColor(hex: 0xE8E7E4), dark: UIColor(white: 1, alpha: 0.14))
    static let softFill = Color(light: UIColor(hex: 0x171614, alpha: 0.05), dark: UIColor(white: 1, alpha: 0.08))
    static let switchOff = Color(light: UIColor(hex: 0xD6D4CF), dark: UIColor(white: 1, alpha: 0.2))
    /// Background of the page (under the gold glow).
    static let background = Color(light: UIColor(hex: 0xF6F4EF), dark: UIColor(hex: 0x0B0A09))
    static let surface = Color(light: UIColor.white, dark: UIColor(hex: 0x1A1917))

    // Glass
    static let glass = Color(light: UIColor(white: 1, alpha: 0.58), dark: UIColor(white: 1, alpha: 0.07))
    static let glassBorder = Color(light: UIColor(white: 1, alpha: 0.85), dark: UIColor(white: 1, alpha: 0.12))
    static let inkGlass = Color(hex: 0x171614, alpha: 0.86)

    // Semantic
    static let blue = Color(light: UIColor(hex: 0x1E3A8A), dark: UIColor(hex: 0x93A8E8))
    static let blueTint = Color(light: UIColor(hex: 0xE3E8F6), dark: UIColor(hex: 0x1E3A8A, alpha: 0.35))
    static let teal = Color(light: UIColor(hex: 0x0F766E), dark: UIColor(hex: 0x5EEAD4))
    static let tealText = Color(light: UIColor(hex: 0x0F5B53), dark: UIColor(hex: 0x5EEAD4))
    static let tealTint = Color(light: UIColor(hex: 0xDDEFEA), dark: UIColor(hex: 0x0F766E, alpha: 0.28))
    static let red = Color(light: UIColor(hex: 0xB91C1C), dark: UIColor(hex: 0xF87171))
    static let redTint = Color(light: UIColor(hex: 0xFDE8E8), dark: UIColor(hex: 0xB91C1C, alpha: 0.25))
    static let purple = Color(light: UIColor(hex: 0x6D28D9), dark: UIColor(hex: 0xC4B5FD))

    // Finger colours (chord diagrams) — fixed, always white text.
    static let finger: [Int: Color] = [1: Color(hex: 0x8F5E00), 2: Color(hex: 0x1E3A8A), 3: Color(hex: 0x0F766E), 4: Color(hex: 0x6D28D9)]

    // Always-dark screens (Stage, Complete, Listen, Game…)
    static let night = Color(hex: 0x0B0A09)
    static let nightText = Color(hex: 0xF4F1EA)
    static let nightMuted = Color(hex: 0xA8A49E)
    static let nightGlass = Color.white.opacity(0.07)
    static let nightBorder = Color.white.opacity(0.12)

    // Radii
    static let rCard: CGFloat = 20
    static let rButton: CGFloat = 14
    static let rChip: CGFloat = 8
}

// MARK: - Typography
// Outfit (UI) · JetBrains Mono (chords) · Noto Sans Myanmar (lyrics, line-height 1.8)

extension Font {
    static func outfit(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom("Outfit", size: size).weight(weight)
    }

    static func mono(_ size: CGFloat, _ weight: Font.Weight = .bold) -> Font {
        .custom("JetBrains Mono", size: size).weight(weight)
    }

    static func myanmar(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom("Noto Sans Myanmar", size: size).weight(weight)
    }
}

// MARK: - Appearance

enum Appearance: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var title: String { rawValue.capitalized }
    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}
