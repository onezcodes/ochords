import Foundation

/// The rules of a tuning session: which string the tuner is showing, how far off it is, and which strings are done.
/// Pure logic (time is passed in), so the "is it tuned?" behaviour can be tested without a microphone.
struct TuningSession {
    enum Event: Equatable {
        /// Auto mode moved to another string.
        case switched(to: Int)
        /// A string came into tune (`all`: every string is now tuned).
        case tuned(Int, all: Bool)
        /// A tuned string was clearly turned out of tune again.
        case untuned(Int)
    }

    /// ±3 ¢ is inaudible to most players; it must hold briefly before a string counts as tuned.
    static let tolerance = 3.0
    static let holdToTune: TimeInterval = 0.4
    /// The last reading stays live this long after the sound stops (bridges short gaps); after that it's shown greyed
    /// as "pluck again", so nobody keeps turning a peg against a stale reading.
    static let holdAfterNote: TimeInterval = 0.3
    /// Auto mode moves to another string only after this many readings in a row (≈ 80 ms) point to it, so one stray
    /// reading — a neighbouring string still ringing — can't pull the tuner away.
    static let readingsToSwitch = 3
    /// A reading this far from a string that's already tuned belongs to another string…
    static let tunedStringReach = 20.0
    /// …the nearest untuned one, if it's no more than this much further away than the tuned one (so a tuned string
    /// re-plucked a little sharp stays itself, while a neighbour two semitones flat is recognised).
    static let untunedMargin = 250.0
    /// A tuned string loses its tick only when it's clearly been turned away: this far off…
    static let untuneCents = 12.0
    /// …for this long…
    static let untuneAfter: TimeInterval = 1.5
    /// …not counting the start of each pluck, which always rings a little sharp.
    static let attack: TimeInterval = 0.5

    /// Each string's target pitch, low to high.
    private(set) var targets: [Double]
    var auto = true
    private(set) var selected: Int
    private(set) var tuned: Set<Int> = []
    /// Last offset of each string from its target, in cents (nil = not played yet).
    private(set) var cents: [Double?]
    /// The pitch being heard right now, smoothed (nil = quiet).
    private(set) var heard: Double?

    private var recent: [Double] = []
    private var lastHeardAt: TimeInterval?
    private var inTuneSince: TimeInterval?
    private var outOfTuneSince: TimeInterval?
    /// When the current note started (after a gap or a switch).
    private var noteStartedAt: TimeInterval?
    private var candidate: (string: Int, count: Int)?

    init(targets: [Double], selected: Int = 1) {
        self.targets = targets
        self.selected = selected
        cents = Array(repeating: nil, count: targets.count)
    }

    var isPlaying: Bool { heard != nil }
    var allTuned: Bool { tuned.count == targets.count }

    func offset(_ hz: Double, from string: Int) -> Double { 1200 * log2(hz / targets[string]) }

    /// New tuning or reference pitch: start over.
    mutating func reset(targets: [Double]) {
        self.targets = targets
        tuned = []
        cents = Array(repeating: nil, count: targets.count)
        quiet()
    }

    /// The player picked a string (switches to manual mode).
    mutating func select(_ string: Int) {
        auto = false
        selected = string
        quiet()
    }

    #if DEBUG
    mutating func markTuned(_ strings: Set<Int>) { tuned = strings }
    #endif

    /// Feeds one reading (nil = nothing clear heard) at time `now` (seconds).
    mutating func hear(_ hz: Double?, at now: TimeInterval) -> [Event] {
        guard let hz else {
            if let last = lastHeardAt, now - last < Self.holdAfterNote { return [] }
            quiet()
            return []
        }
        var events: [Event] = []
        if heard == nil { noteStartedAt = now }
        lastHeardAt = now

        // Median of the last three readings (≈ 60 ms) drops the odd stray reading without lagging behind the peg.
        recent.append(hz)
        if recent.count > 3 { recent.removeFirst() }
        let smooth = recent.sorted()[recent.count / 2]
        heard = smooth

        if auto, let nearest = string(for: hz) {
            if nearest == selected {
                candidate = nil
            } else {
                let count = candidate?.string == nearest ? candidate!.count + 1 : 1
                candidate = (nearest, count)
                guard count >= Self.readingsToSwitch else { return events }
                selected = nearest
                candidate = nil
                recent = [hz]
                heard = hz
                inTuneSince = nil
                outOfTuneSince = nil
                noteStartedAt = now
                events.append(.switched(to: nearest))
            }
        }

        let off = offset(heard!, from: selected)
        cents[selected] = off

        if abs(off) <= Self.tolerance {
            outOfTuneSince = nil
            let since = inTuneSince ?? now
            inTuneSince = since
            if now - since >= Self.holdToTune, !tuned.contains(selected) {
                tuned.insert(selected)
                events.append(.tuned(selected, all: allTuned))
            }
        } else {
            inTuneSince = nil
            let settled = now - (noteStartedAt ?? now) >= Self.attack
            if tuned.contains(selected), settled, abs(off) > Self.untuneCents {
                let since = outOfTuneSince ?? now
                outOfTuneSince = since
                if now - since >= Self.untuneAfter {
                    tuned.remove(selected)
                    outOfTuneSince = nil
                    events.append(.untuned(selected))
                }
            } else {
                outOfTuneSince = nil
            }
        }
        return events
    }

    /// The string a pitch most likely belongs to. Usually the nearest one — but a string far out of tune can sound
    /// closer to its already-tuned neighbour (a B two semitones flat is as near G as B), and a tuned string sits
    /// close to its note, so a pitch well away from a tuned string goes to the nearest untuned string instead.
    private func string(for hz: Double) -> Int? {
        let byDistance = targets.indices.sorted { abs(offset(hz, from: $0)) < abs(offset(hz, from: $1)) }
        guard let nearest = byDistance.first else { return nil }
        let distance = abs(offset(hz, from: nearest))
        guard tuned.contains(nearest), distance > Self.tunedStringReach else { return nearest }
        return byDistance.first { !tuned.contains($0) && abs(offset(hz, from: $0)) < distance + Self.untunedMargin } ?? nearest
    }

    private mutating func quiet() {
        heard = nil
        recent.removeAll()
        lastHeardAt = nil
        inTuneSince = nil
        outOfTuneSince = nil
        noteStartedAt = nil
        candidate = nil
    }
}
