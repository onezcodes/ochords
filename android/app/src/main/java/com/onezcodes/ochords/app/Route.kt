package com.onezcodes.ochords.app

import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.UserSummary
import com.onezcodes.ochords.services.ScanResult
import com.onezcodes.ochords.services.ScanSource

/** Every pushable screen. Add a class here, then map it in `RouteView`. */
sealed class Route {
    // Auth
    data object SignIn : Route()
    data object SignUp : Route()
    /** From the `ochords://reset-password?token=…` link in the reset email. */
    data class ResetPassword(val token: String) : Route()

    // Library & account
    data object Notifications : Route()
    data object Profile : Route()
    data class User(val user: UserSummary) : Route()
    data object Widgets : Route()
    data object LiveActivity : Route()

    // Import & listen
    data object ImportSheet : Route()
    /** Scan a photo or PDF already picked (from the Library), without opening the camera. */
    data class ImportPicked(val source: ScanSource) : Route()
    data class Review(val result: ScanResult) : Route()
    data class Zawgyi(val result: ScanResult) : Route()
    data object Listen : Route()

    // Song & tools
    data class SongPage(val song: Song) : Route()
    data class Stage(val song: Song) : Route()
    data class Warmup(val song: Song) : Route()
    data object HandsFree : Route()
    data class PrintPdf(val song: Song) : Route()
    data class Report(val song: Song) : Route()
    data class Jam(val song: Song) : Route()
    data class Chords(val focus: String?, val songChords: List<String>) : Route()
    data object Keys : Route()
    data object Strum : Route()
    data class Practice(val chord: String) : Route()
    data class SaveImage(val chord: String, val song: Song? = null) : Route()

    // Community
    data class Shared(val song: Song) : Route()
    data object Popular : Route()

    // Learn
    data class Lesson(val fretID: String) : Route()
    data object Complete : Route()
    data object Streak : Route()
    data object Tiers : Route()
    data object Game : Route()

    // Settings & support
    data object Settings : Route()
    data object Help : Route()
    data object Feedback : Route()
    data object About : Route()
    data object Terms : Route()
    data object Privacy : Route()
    data object DeleteAccount : Route()
    data object AdChoices : Route()
}

enum class AppPhase { Splash, Onboarding, Welcome, Main }

enum class MainTab(val title: String) {
    Library("Library"), Explore("Explore"), Setlists("Setlists"), Learn("Learn"), Tuner("Tuner");

    companion object { fun from(raw: String?): MainTab? = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
}

enum class PermissionKind { Camera, Microphone, Notifications }
enum class RewardKind { Scan, Strings }

/** Screens presented full-screen over everything. */
sealed class Modal {
    data object Pro : Modal()
    data class Permission(val kind: PermissionKind) : Modal()
    data class Reward(val kind: RewardKind) : Modal()
    data object Interstitial : Modal()
    data object AdConsent : Modal()
}
