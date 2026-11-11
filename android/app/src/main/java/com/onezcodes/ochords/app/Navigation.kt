package com.onezcodes.ochords.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.features.explore.ExploreView
import com.onezcodes.ochords.features.explore.PopularView
import com.onezcodes.ochords.features.explore.SetlistsView
import com.onezcodes.ochords.features.explore.SharedSheetView
import com.onezcodes.ochords.features.importsheet.ImportView
import com.onezcodes.ochords.features.importsheet.ListenView
import com.onezcodes.ochords.features.importsheet.ReviewView
import com.onezcodes.ochords.features.importsheet.RewardAdView
import com.onezcodes.ochords.features.importsheet.ZawgyiView
import com.onezcodes.ochords.features.learn.CompleteView
import com.onezcodes.ochords.features.learn.GameView
import com.onezcodes.ochords.features.learn.InterstitialAdView
import com.onezcodes.ochords.features.learn.LearnView
import com.onezcodes.ochords.features.learn.LessonView
import com.onezcodes.ochords.features.learn.StreakView
import com.onezcodes.ochords.features.learn.TiersView
import com.onezcodes.ochords.features.library.LibraryView
import com.onezcodes.ochords.features.library.LiveActivityPreviewView
import com.onezcodes.ochords.features.library.NotificationsView
import com.onezcodes.ochords.features.library.ProfileView
import com.onezcodes.ochords.features.library.UserProfileView
import com.onezcodes.ochords.features.library.WidgetsPreviewView
import com.onezcodes.ochords.features.onboarding.AdConsentView
import com.onezcodes.ochords.features.onboarding.OnboardingView
import com.onezcodes.ochords.features.onboarding.PermissionPrimerView
import com.onezcodes.ochords.features.onboarding.ResetPasswordView
import com.onezcodes.ochords.features.onboarding.SignInView
import com.onezcodes.ochords.features.onboarding.SignUpView
import com.onezcodes.ochords.features.onboarding.SplashView
import com.onezcodes.ochords.features.onboarding.WelcomeView
import com.onezcodes.ochords.features.settings.AboutView
import com.onezcodes.ochords.features.settings.AdChoicesView
import com.onezcodes.ochords.features.settings.DeleteAccountView
import com.onezcodes.ochords.features.settings.FeedbackView
import com.onezcodes.ochords.features.settings.HelpView
import com.onezcodes.ochords.features.settings.PrivacyView
import com.onezcodes.ochords.features.settings.ProView
import com.onezcodes.ochords.features.settings.SettingsView
import com.onezcodes.ochords.features.settings.TermsView
import com.onezcodes.ochords.features.song.HandsFreeView
import com.onezcodes.ochords.features.song.SongView
import com.onezcodes.ochords.features.song.StageView
import com.onezcodes.ochords.features.song.WarmupView
import com.onezcodes.ochords.features.tools.ChordGuideView
import com.onezcodes.ochords.features.tools.JamView
import com.onezcodes.ochords.features.tools.KeysView
import com.onezcodes.ochords.features.tools.PracticeView
import com.onezcodes.ochords.features.tools.PrintPdfView
import com.onezcodes.ochords.features.tools.ReportView
import com.onezcodes.ochords.features.tools.SaveImageView
import com.onezcodes.ochords.features.tools.StrumTrainerView
import com.onezcodes.ochords.features.tools.TunerView

/** Maps a `Route` to its screen. */
@Composable
fun RouteView(route: Route) {
    when (route) {
        Route.SignIn -> SignInView()
        Route.SignUp -> SignUpView()
        is Route.ResetPassword -> ResetPasswordView(route.token)
        Route.Notifications -> NotificationsView()
        Route.Profile -> ProfileView()
        is Route.User -> UserProfileView(route.user)
        Route.Widgets -> WidgetsPreviewView()
        Route.LiveActivity -> LiveActivityPreviewView()
        Route.ImportSheet -> ImportView()
        is Route.ImportPicked -> ImportView(picked = route.source)
        is Route.Review -> ReviewView(route.result)
        is Route.Zawgyi -> ZawgyiView(route.result)
        Route.Listen -> ListenView()
        is Route.SongPage -> SongView(route.song)
        is Route.Stage -> StageView(route.song)
        is Route.Warmup -> WarmupView(route.song)
        Route.HandsFree -> HandsFreeView()
        is Route.PrintPdf -> PrintPdfView(route.song)
        is Route.Report -> ReportView(route.song)
        is Route.Jam -> JamView(route.song)
        is Route.Chords -> ChordGuideView(route.focus, route.songChords)
        Route.Keys -> KeysView()
        Route.Strum -> StrumTrainerView()
        is Route.Practice -> PracticeView(route.chord)
        is Route.SaveImage -> SaveImageView(route.chord, route.song)
        is Route.Shared -> SharedSheetView(route.song)
        Route.Popular -> PopularView()
        is Route.Lesson -> LessonView(route.fretID)
        Route.Complete -> CompleteView()
        Route.Streak -> StreakView()
        Route.Tiers -> TiersView()
        Route.Game -> GameView()
        Route.Settings -> SettingsView()
        Route.Help -> HelpView()
        Route.Feedback -> FeedbackView()
        Route.About -> AboutView()
        Route.Terms -> TermsView()
        Route.Privacy -> PrivacyView()
        Route.DeleteAccount -> DeleteAccountView()
        Route.AdChoices -> AdChoicesView()
    }
}

@Composable
fun ModalView(modal: Modal) {
    when (modal) {
        Modal.Pro -> ProView()
        is Modal.Permission -> PermissionPrimerView(modal.kind)
        is Modal.Reward -> RewardAdView(modal.kind)
        Modal.Interstitial -> InterstitialAdView()
        Modal.AdConsent -> AdConsentView()
    }
}

/** A stack of screens: the root plus pushed routes, with slide transitions and system-back popping. */
@Composable
fun NavStack(path: List<Route>, onPop: () -> Unit, root: @Composable () -> Unit) {
    BackHandler(enabled = path.isNotEmpty(), onBack = onPop)
    val depth = path.size
    AnimatedContent(
        targetState = depth to path.lastOrNull(),
        transitionSpec = {
            if (targetState.first > initialState.first) {
                (slideInHorizontally(tween(260)) { it } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(200)))
            } else {
                (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(200))) togetherWith (slideOutHorizontally(tween(260)) { it } + fadeOut(tween(200)))
            }
        },
        label = "nav",
    ) { (_, top) ->
        Box(Modifier.fillMaxSize()) { if (top == null) root() else RouteView(top) }
    }
}

@Composable
fun RootView() {
    val app = LocalApp.current
    Box(Modifier.fillMaxSize().background(OC.background)) {
        AnimatedContent(targetState = app.phase, transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) }, label = "phase") { phase ->
            when (phase) {
                AppPhase.Splash -> SplashView()
                AppPhase.Onboarding -> OnboardingView()
                AppPhase.Welcome -> NavStack(path = app.authPath, onPop = { app.pop() }) { WelcomeView() }
                AppPhase.Main -> MainTabView()
            }
        }
        val modal = app.modal
        AnimatedVisibility(
            visible = modal != null,
            enter = slideInVertically(tween(300)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            val shown = remember { modal }
            Box(Modifier.fillMaxSize().background(OC.background)) {
                BackHandler { app.modal = null }
                (modal ?: shown)?.let { ModalView(it) }
            }
        }
    }
}

@Composable
fun MainTabView() {
    val app = LocalApp.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        // Each tab keeps its own stack; only the current one is composed (state lives in AppState, so it survives).
        NavStack(path = app.paths[app.tab] ?: emptyList(), onPop = { app.pop() }) {
            when (app.tab) {
                MainTab.Library -> LibraryView()
                MainTab.Explore -> ExploreView()
                MainTab.Setlists -> SetlistsView()
                MainTab.Learn -> LearnView()
                MainTab.Tuner -> TunerView()
            }
        }
        AnimatedVisibility(
            visible = app.isTabBarVisible,
            enter = slideInVertically(tween(250)) { it } + fadeIn(),
            exit = slideOutVertically(tween(250)) { it } + fadeOut(),
        ) { FloatingTabBar() }
    }
}

private val MainTab.icon: ImageVector
    get() = when (this) {
        MainTab.Library -> Icons.Outlined.LibraryMusic
        MainTab.Explore -> Icons.Outlined.Search
        MainTab.Setlists -> Icons.Outlined.FormatListBulleted
        MainTab.Learn -> Icons.Outlined.School
        MainTab.Tuner -> Icons.Outlined.Tune
    }

/** Floating pill tab bar: 66dp high, 14dp from the edges, radius 33, white 62% glass; active tab = gold 28% pill. */
@Composable
fun FloatingTabBar() {
    val app = LocalApp.current
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(bottom = 4.dp)
            .fillMaxWidth()
            .height(66.dp)
            .shadow(20.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.2f))
            .clip(CircleShape)
            .background(OC.surface.copy(alpha = if (com.onezcodes.ochords.design.isDark) 0.92f else 0.62f))
            .background(OC.glass)
            .border(1.dp, OC.glassBorder, CircleShape)
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTab.entries.forEach { tab -> TabItem(tab, app.tab == tab) { if (app.tab == tab) app.popToRoot() else app.tab = tab } }
    }
}

@Composable
private fun RowScope.TabItem(tab: MainTab, on: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .clip(CircleShape)
            .then(if (on) Modifier.background(OC.gold.copy(alpha = 0.28f)).border(1.dp, OC.gold.copy(alpha = 0.35f), CircleShape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { selected = on },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = null, tint = if (on) OC.ink else OC.muted, modifier = Modifier.size(22.dp))
        Text(tab.title, style = outfit(11, if (on) FontWeight.Bold else FontWeight.SemiBold), color = if (on) OC.ink else OC.muted)
    }
}

/** Space at the bottom of tab-root scroll views so content clears the floating tab bar. */
val tabBarInset = 84.dp
