import SwiftUI

/// Edits one line of a song. By default just the words: the chords stay with their words
/// (`ChordSheetParser.reword`), or — for a scanned line whose lyrics couldn't be read — are lined up over the words
/// typed (`ChordSheetParser.place`). "Edit chords too" switches to the line as text with chords in [brackets].
struct LyricLineEditor: View {
    let sectionLabel: String
    let line: String
    /// The scan couldn't read this line's lyrics (only its chords are known).
    var missingLyrics = false
    let onSave: (String) -> Void
    let onDelete: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var rawMode = false
    @FocusState private var focused: Bool

    private var chords: [String] { ChordLine.chords(in: line) }

    /// What will be saved. Text typed on a Zawgyi keyboard is converted to Unicode first.
    private var preview: String {
        let t = Zawgyi.normalised(text.trimmingCharacters(in: .whitespaces))
        if rawMode { return t }
        if missingLyrics { return t.isEmpty ? line : ChordSheetParser.place(chordsOf: line, onto: t) }
        return ChordSheetParser.reword(line, with: t)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Button("Cancel") { dismiss() }.font(.outfit(16, .semibold)).foregroundStyle(OC.muted).frame(minHeight: 44)
                Spacer()
                Text(sectionLabel).font(.outfit(16, .bold)).foregroundStyle(OC.ink)
                Spacer()
                Button("Done") { onSave(preview) }.font(.outfit(16, .bold)).foregroundStyle(OC.goldText).frame(minHeight: 44)
            }

            if rawMode {
                Text("Put chords in [brackets] right before the word, e.g. [Am]words").font(.outfit(13)).foregroundStyle(OC.muted)
            } else {
                Text(missingLyrics ? "Type the lyrics for this line" : "Edit the words — the chords stay with them")
                    .font(.outfit(13, .bold)).foregroundStyle(OC.muted)
                if !chords.isEmpty {
                    HStack(spacing: 6) {
                        ForEach(Array(chords.enumerated()), id: \.offset) { _, c in
                            Text(c).font(.mono(13)).foregroundStyle(OC.goldText)
                                .padding(.horizontal, 7).padding(.vertical, 4)
                                .background(RoundedRectangle(cornerRadius: 8).fill(OC.goldTint))
                        }
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel("Chords: \(chords.joined(separator: ", "))")
                }
            }

            TextField(rawMode ? "Line" : "Lyrics", text: $text, axis: .vertical)
                .font(rawMode ? .mono(15) : .myanmar(18))
                .foregroundStyle(OC.ink)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(rawMode)
                .focused($focused)
                .padding(12)
                .glass(radius: 14)

            if !rawMode && !text.trimmingCharacters(in: .whitespaces).isEmpty && !chords.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("PREVIEW").font(.outfit(11, .heavy)).tracking(1).foregroundStyle(OC.muted)
                    WrapFlow(spacing: 5, lineSpacing: 4) {
                        ForEach(Array(ChordLine.parse(preview).enumerated()), id: \.offset) { _, seg in
                            VStack(alignment: .leading, spacing: 0) {
                                Text(seg.chord ?? " ").font(.mono(13)).foregroundStyle(OC.goldText)
                                Text(seg.text.isEmpty ? " " : seg.text).font(.myanmar(15)).foregroundStyle(OC.ink)
                            }
                        }
                    }
                }
            }

            HStack {
                Button(rawMode ? "Just edit the words" : "Edit chords too") {
                    let current = preview
                    rawMode.toggle()
                    text = rawMode ? current : (missingLyrics ? "" : ChordSheetParser.words(of: current))
                }
                .font(.outfit(14, .bold)).foregroundStyle(OC.goldText).frame(minHeight: 44)
                Spacer()
                Button("Delete line", role: .destructive, action: onDelete)
                    .font(.outfit(14, .bold)).foregroundStyle(OC.red).frame(minHeight: 44)
            }
            Spacer(minLength: 0)
        }
        .padding(20)
        .background(WarmBackground().ignoresSafeArea())
        .onAppear {
            text = missingLyrics ? "" : ChordSheetParser.words(of: line)
            focused = true
        }
    }
}

/// Wrapping flow layout for chips and chord-over-lyric segments.
struct WrapFlow: Layout {
    var spacing: CGFloat = 6
    var lineSpacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(maxWidth: proposal.width ?? .infinity, subviews: subviews)
        let width = rows.map(\.width).max() ?? 0
        let height = rows.reduce(0) { $0 + $1.height } + CGFloat(max(0, rows.count - 1)) * lineSpacing
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(maxWidth: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for (i, size) in zip(row.indices, row.sizes) {
                subviews[i].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + lineSpacing
        }
    }

    private struct Row { var indices: [Int] = []; var sizes: [CGSize] = []; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func arrange(maxWidth: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = [Row()]
        for i in subviews.indices {
            var size = subviews[i].sizeThatFits(.unspecified)
            if size.width > maxWidth { size = subviews[i].sizeThatFits(ProposedViewSize(width: maxWidth, height: nil)) }
            let extra = rows[rows.count - 1].indices.isEmpty ? size.width : size.width + spacing
            if !rows[rows.count - 1].indices.isEmpty, rows[rows.count - 1].width + extra > maxWidth {
                rows.append(Row())
            }
            let isFirst = rows[rows.count - 1].indices.isEmpty
            rows[rows.count - 1].indices.append(i)
            rows[rows.count - 1].sizes.append(size)
            rows[rows.count - 1].width += isFirst ? size.width : size.width + spacing
            rows[rows.count - 1].height = max(rows[rows.count - 1].height, size.height)
        }
        return rows.filter { !$0.indices.isEmpty }
    }
}
