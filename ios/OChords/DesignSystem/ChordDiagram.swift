import SwiftUI

/// Fretboard diagram with coloured finger dots (index gold-brown, middle blue, ring teal, pinky purple).
/// Scales to the given width; 4 frets shown.
struct ChordDiagram: View {
    let voicing: ChordVoicing
    var width: CGFloat = 260
    var leftHanded = false
    var showFingers = true
    var showStringNames = true
    var dark = false

    private let names = ["E", "A", "D", "G", "B", "e"]
    /// Lines scale with the diagram, but never thinner than this — small diagrams (an image's or a PDF's chord list)
    /// would otherwise lose their grid.
    private static let hairline: CGFloat = 0.75
    private let stringWidths: [CGFloat] = [3, 2.6, 2.2, 1.8, 1.4, 1.2]

    var body: some View {
        let s = width / 260
        let fretH = 46 * s
        let gridH = fretH * 4
        let dot = 32 * s
        let pattern = Array(voicing.pattern)
        let ink = dark ? OC.nightText : OC.ink
        let fretLine = dark ? Color.white.opacity(0.18) : Color(hex: 0xD6D4CF)
        let stringCol = dark ? Color.white.opacity(0.5) : Color(hex: 0x8E8A84)

        VStack(spacing: 6 * s) {
            // open / muted markers
            ZStack(alignment: .topLeading) {
                ForEach(0..<6, id: \.self) { i in
                    let c = i < pattern.count ? pattern[i] : "0"
                    Text(c == "x" ? "×" : c == "0" ? "○" : "")
                        .font(.outfit(15 * s, .heavy))
                        .foregroundStyle(c == "x" ? OC.faint : ink)
                        .frame(width: 20 * s, height: 20 * s)
                        .position(x: x(i, s), y: 10 * s)
                }
            }
            .frame(width: width, height: 22 * s)

            ZStack(alignment: .topLeading) {
                // nut
                RoundedRectangle(cornerRadius: 8 * s)
                    .fill(voicing.baseFret > 1 ? fretLine : ink)
                    .frame(width: width - 18 * s, height: max(6 * s, 1.5))
                    .position(x: width / 2, y: 0)
                ForEach(1...4, id: \.self) { f in
                    Rectangle().fill(fretLine).frame(width: width - 20 * s, height: max(2 * s, Self.hairline))
                        .position(x: width / 2, y: CGFloat(f) * fretH - (f == 4 ? 1 : 0) * s)
                }
                ForEach(0..<6, id: \.self) { i in
                    Rectangle().fill(stringCol).frame(width: max(stringWidths[i] * s, Self.hairline), height: gridH)
                        .position(x: x(i, s), y: gridH / 2)
                }
                if let b = voicing.barre {
                    let a = min(x(b.from, s), x(b.to, s)), z = max(x(b.from, s), x(b.to, s))
                    Capsule().fill(OC.finger[1]!)
                        .frame(width: z - a + dot, height: dot)
                        .overlay(Text(showFingers ? "1" : "").font(.outfit(15 * s, .heavy)).foregroundStyle(.white))
                        .position(x: (a + z) / 2, y: (CGFloat(b.fret) - 0.5) * fretH)
                }
                ForEach(voicing.dots, id: \.self) { d in
                    Circle().fill(OC.finger[d.finger] ?? OC.inkFixed)
                        .frame(width: dot, height: dot)
                        .overlay(Circle().strokeBorder(dark ? OC.night : .white, lineWidth: 3 * s))
                        .overlay(Text(showFingers ? "\(d.finger)" : "").font(.outfit(15 * s, .heavy)).foregroundStyle(.white))
                        .position(x: x(d.string, s), y: (CGFloat(d.fret) - 0.5) * fretH)
                }
            }
            .frame(width: width, height: gridH)
            .overlay(alignment: .topLeading) {
                if voicing.baseFret > 1 {
                    Text("\(voicing.baseFret)fr").font(.mono(11 * s)).foregroundStyle(OC.faint).offset(x: -26 * s, y: 15 * s)
                }
            }

            if showStringNames {
                ZStack {
                    ForEach(0..<6, id: \.self) { i in
                        Text(names[i]).font(.mono(12 * s)).foregroundStyle(dark ? OC.nightMuted : OC.muted)
                            .position(x: x(i, s), y: 9 * s)
                    }
                }
                .frame(width: width, height: 18 * s)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Chord diagram, pattern \(voicing.pattern.map(String.init).joined(separator: " "))")
    }

    private func x(_ i: Int, _ s: CGFloat) -> CGFloat {
        leftHanded ? (250 - 48 * CGFloat(i)) * s : (10 + 48 * CGFloat(i)) * s
    }
}

/// Small chord card: name + mini diagram (Warm up, Keys, Song chord strip).
struct ChordCard: View {
    let name: String
    var width: CGFloat = 96
    var highlighted = false
    var isEasySwap = false

    var body: some View {
        VStack(spacing: 6) {
            Text(name).font(.mono(17)).foregroundStyle(isEasySwap ? OC.teal : OC.goldText)
            if let v = ChordLibrary.info(name)?.voicings.first {
                ChordDiagram(voicing: v, width: width - 20, showFingers: false, showStringNames: false)
            } else {
                Text("?").font(.mono(20)).foregroundStyle(OC.faint).frame(height: width * 0.8)
            }
        }
        .padding(10)
        .frame(width: width)
        .glass(radius: 16)
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(highlighted ? OC.gold : .clear, lineWidth: 2))
    }
}
