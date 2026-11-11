package com.onezcodes.ochords.features.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onezcodes.ochords.R
import com.onezcodes.ochords.app.AppPhase
import com.onezcodes.ochords.app.AppState
import com.onezcodes.ochords.app.LocalApp
import com.onezcodes.ochords.app.Modal
import com.onezcodes.ochords.app.PermissionKind
import com.onezcodes.ochords.app.Route
import com.onezcodes.ochords.app.MainTab
import com.onezcodes.ochords.design.AppIconTile
import com.onezcodes.ochords.design.Eyebrow
import com.onezcodes.ochords.design.BottomBar
import com.onezcodes.ochords.design.GoldButton
import com.onezcodes.ochords.design.InkButton
import com.onezcodes.ochords.design.LocalDark
import com.onezcodes.ochords.design.NightBackground
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.OCPage
import com.onezcodes.ochords.design.Toast
import com.onezcodes.ochords.design.ToastHost
import com.onezcodes.ochords.design.TopBar
import com.onezcodes.ochords.design.Wordmark
import com.onezcodes.ochords.design.glass
import com.onezcodes.ochords.design.hex
import com.onezcodes.ochords.design.isDark
import com.onezcodes.ochords.design.mono
import com.onezcodes.ochords.design.myanmar
import com.onezcodes.ochords.design.nightGlass
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.design.plainClickable
import com.onezcodes.ochords.design.Heavy
import com.onezcodes.ochords.services.MockData
import com.onezcodes.ochords.services.ServiceException
import com.onezcodes.ochords.services.SocialSignIn
import com.onezcodes.ochords.services.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// MARK: - Splash

/** Launch screen: six-string mark + wordmark, tagline, "Powered by Onez Codes". Auto-advances after ~1.6s. */
@Composable
fun SplashView() {
    val app = LocalApp.current
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (appeared) 1f else 0.94f, tween(500, easing = EaseOut), label = "splashScale")
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, tween(500, easing = EaseOut), label = "splashAlpha")

    fun finish() { if (app.phase == AppPhase.Splash) app.finishSplash() }

    LaunchedEffect(Unit) {
        appeared = true
        // A returning player's saved session loads while the logo shows.
        val restored = app.scope.async { app.restoreSession() }
        delay(1600)
        restored.await()
        finish()
    }

    Column(
        Modifier.fillMaxSize().background(if (isDark) hex(0x0B0A09) else Color.White).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Column(Modifier.scale(scale).alpha(alpha), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.semantics { contentDescription = "O Chords" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OnboardingSplashMark(Modifier.size(64.dp))
                Text("Chords", style = outfit(52, FontWeight.Heavy, letterSpacing = (-0.5).sp), color = OC.ink)
            }
            Text("EVERY CHORD SHEET, READY TO PLAY", style = outfit(11, FontWeight.SemiBold, letterSpacing = 2.sp), color = OC.goldText)
        }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.plainClickable { finish() }.semantics { contentDescription = "Continue" }.padding(bottom = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("POWERED BY", style = outfit(11, FontWeight.SemiBold, letterSpacing = 3.sp), color = OC.faint)
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(6.dp)) {
                Image(painterResource(R.drawable.onez_logo), contentDescription = "Onez Codes", modifier = Modifier.width(168.dp), contentScale = ContentScale.FillWidth)
            }
        }
    }
}

/** The launch-screen mark: gold ring with six strings. */
@Composable
private fun OnboardingSplashMark(modifier: Modifier = Modifier) {
    val ink = OC.ink
    Canvas(modifier) {
        val s = size.width / 100f
        drawOval(OC.gold, topLeft = Offset(22 * s, 22 * s), size = Size(56 * s, 56 * s), style = Stroke(15 * s))
        val strings = listOf(35f to 2.6f, 41f to 2.3f, 47f to 2.0f, 53f to 1.8f, 59f to 1.6f, 65f to 1.4f)
        for ((x, w) in strings) {
            drawLine(ink, Offset(x * s, 12 * s), Offset(x * s, 88 * s), strokeWidth = w * s, cap = StrokeCap.Round)
        }
    }
}

// MARK: - Onboarding tour (Onboard · OnboardPlay · OnboardLearn)

private val tourPages = listOf(
    "Snap any chord sheet" to "Printed, handwritten or a screenshot — Myanmar or English. We turn it into a clean song in seconds.",
    "Play it your way" to "Change the key, add a capo, swap hard chords for easy ones, then go full-screen on stage.",
    "Learn and keep your groove" to "Short daily sessions on a guitar-neck path. Earn O Points and climb the tiers.",
)

@Composable
fun OnboardingView() {
    val app = LocalApp.current
    val pager = rememberPagerState { tourPages.size }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage

    OCPage {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Wordmark(28.dp, modifier = Modifier.padding(start = 12.dp))
                Spacer(Modifier.weight(1f))
                Box(Modifier.heightIn(min = 44.dp).plainClickable { app.finishOnboarding() }.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("Skip", style = outfit(14, FontWeight.Bold), color = OC.muted)
                }
            }

            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                OnboardingTourPage(index = i, title = tourPages[i].first, sub = tourPages[i].second)
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    Modifier.semantics { contentDescription = "${page + 1} of ${tourPages.size}" },
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tourPages.indices.forEach { i ->
                        val w by animateDpAsState(if (i == page) 24.dp else 8.dp, label = "dot")
                        Box(Modifier.width(w).height(8.dp).clip(CircleShape).background(if (i == page) OC.gold else OC.ink.copy(alpha = 0.15f)))
                    }
                }
                if (page < tourPages.size - 1) {
                    GoldButton("Next") { scope.launch { pager.animateScrollToPage(page + 1) } }
                } else {
                    GoldButton("Get started") { app.finishOnboarding() }
                }
            }
        }
    }
}

@Composable
private fun OnboardingTourPage(index: Int, title: String, sub: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(300.dp, 330.dp), contentAlignment = Alignment.Center) {
            when (index) {
                0 -> OnboardingScanArt()
                1 -> OnboardingPlayArt()
                else -> OnboardingGrooveArt()
            }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${index + 1} OF 3", style = outfit(12, FontWeight.Bold, letterSpacing = 1.8.sp), color = OC.goldText)
            Box(Modifier.heightIn(min = 70.dp), contentAlignment = Alignment.BottomCenter) {
                Text(title, style = outfit(32, FontWeight.Heavy, letterSpacing = (-1).sp), color = OC.ink, textAlign = TextAlign.Center)
            }
            Box(Modifier.heightIn(min = 72.dp), contentAlignment = Alignment.TopCenter) {
                Text(sub, style = outfit(16).lineSpacing(4), color = OC.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun OnboardingChordChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.shadow(12.dp, RoundedCornerShape(10.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(RoundedCornerShape(10.dp)).background(Color.White).height(36.dp).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = mono(15), color = hex(0x8F5E00)) }
}

/** Phone scanning a paper sheet, with floating chord chips. */
@Composable
private fun OnboardingScanArt() {
    Box(Modifier.size(300.dp, 330.dp)) {
        // Phone
        Box(
            Modifier.offset(70.dp, 10.dp).size(160.dp, 300.dp)
                .shadow(25.dp, RoundedCornerShape(30.dp), ambientColor = OC.inkFixed.copy(alpha = 0.3f), spotColor = OC.inkFixed.copy(alpha = 0.3f))
                .clip(RoundedCornerShape(30.dp)).background(OC.inkFixed).padding(10.dp),
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(hex(0x3A332A), hex(0x1E1B17))))) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val w = maxWidth
                    val h = maxHeight
                    // Paper
                    Column(
                        Modifier.offset(18.dp, 40.dp).rotate(-4f).size(w - 36.dp, h - 100.dp)
                            .clip(RoundedCornerShape(4.dp)).background(hex(0xE9E2D2)).padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0.7f, 0.9f, 0.55f, 0.85f, 0.6f, 0.8f, 0.5f).forEach { f ->
                            Box(Modifier.width((w - 56.dp) * f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(hex(0x28221A, 0.5f)))
                        }
                    }
                    // Scan glow + line
                    Box(Modifier.offset(10.dp, 88.dp).size(w - 20.dp, 34.dp).background(Brush.verticalGradient(listOf(OC.gold.copy(alpha = 0f), OC.gold.copy(alpha = 0.35f)))))
                    Box(Modifier.offset(10.dp, 120.dp).size(w - 20.dp, 2.dp).shadow(8.dp, ambientColor = OC.gold.copy(alpha = 0.6f), spotColor = OC.gold.copy(alpha = 0.6f)).background(OC.gold))
                    // Shutter
                    Box(Modifier.offset(w / 2 - 23.dp, h - 60.dp).size(46.dp).border(3.5.dp, Color.White, CircleShape))
                }
            }
        }

        OnboardingChordChip("C", Modifier.offset(10.dp, 60.dp).rotate(-8f))
        OnboardingChordChip("G/B", Modifier.offset(220.dp, 40.dp).rotate(6f))
        OnboardingChordChip("Am", Modifier.offset(0.dp, 200.dp).rotate(5f))
        OnboardingChordChip("F", Modifier.offset(240.dp, 220.dp).rotate(-6f))
    }
}

/** Transpose card, tool chips and a lyric line. */
@Composable
private fun OnboardingPlayArt() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.width(250.dp).glass(22.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("−", style = outfit(28, FontWeight.SemiBold), color = OC.ink)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SOUNDS IN", style = outfit(11, FontWeight.Bold), color = OC.muted)
                Text("D", style = mono(34), color = OC.goldText)
            }
            Spacer(Modifier.weight(1f))
            Text("+", style = outfit(28, FontWeight.SemiBold), color = OC.ink)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(OC.gold).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text("Capo 2", style = outfit(14, FontWeight.Bold), color = OC.inkFixed)
            }
            Box(Modifier.height(40.dp).glass(12.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text("Easy chords", style = outfit(14, FontWeight.Bold), color = OC.ink)
            }
            Box(Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(OC.inkFixed).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text("Stage", style = outfit(14, FontWeight.Bold), color = OC.paper)
            }
        }

        Column(
            Modifier.width(250.dp).clip(RoundedCornerShape(20.dp)).background(OC.inkFixed).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("C       G/B", style = mono(14), color = OC.gold)
            Text("ငါ မကြားချင် ခဲ့သော စကား", style = myanmar(15), color = OC.paper)
        }
    }
}

/** Weekly groove chart + fret path progress. */
@Composable
private fun OnboardingGrooveArt() {
    val bars = listOf(30, 52, 40, 70, 46, 84, 20)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.width(250.dp).glass(22.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("Your groove", style = outfit(15, FontWeight.Bold), color = OC.ink)
                Spacer(Modifier.weight(1f))
                Text("7 days", style = outfit(26, FontWeight.Heavy), color = OC.goldText)
            }
            Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                bars.forEachIndexed { i, h ->
                    Box(Modifier.width(18.dp).height(h.dp).clip(RoundedCornerShape(5.dp)).background(if (i == bars.lastIndex) OC.ink.copy(alpha = 0.12f) else OC.gold))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                    Text(d, style = outfit(11, FontWeight.Bold), color = OC.muted, modifier = Modifier.width(18.dp), textAlign = TextAlign.Center)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(2) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(hex(0x0F766E)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Box(Modifier.size(56.dp).clip(CircleShape).background(OC.gold.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(OC.gold), contentAlignment = Alignment.Center) {
                    Text("3", style = outfit(16, FontWeight.Heavy), color = OC.inkFixed)
                }
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(OC.ink.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Text("4", style = outfit(16, FontWeight.Heavy), color = OC.muted)
            }
        }
    }
}

// MARK: - Welcome (always dark)

private const val GOOGLE_NOT_SET_UP = "Google sign-in isn’t set up yet. Log in with your email for now."

/** Google's sheet, then the API (`auth/google`). The demo build fakes it. Returns a message to show, or null. */
private suspend fun googleSignIn(app: AppState): String? {
    return try {
        when {
            app.usesDemoData -> { delay(800); app.signedIn(MockData.profile); null }
            !SocialSignIn.googleAvailable -> GOOGLE_NOT_SET_UP
            else -> {
                val activity = app.activity ?: return "Google sign-in isn’t available right now. Try again."
                val token = SocialSignIn.google(activity)
                app.signedIn(app.backend.signInWithGoogle(token))
                null
            }
        }
    } catch (e: SocialSignIn.Failure) {
        if (e.cancelled) null else e.userMessage
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e.userMessage
    }
}

@Composable
fun WelcomeView() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var googleLoading by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    val subText = hex(0xBDB9B3)

    CompositionLocalProvider(LocalDark provides true) {
        Box(Modifier.fillMaxSize()) {
            NightBackground()
            ToastHost(toast, onDismiss = { toast = null }, bottomPadding = 40.dp) {
                OnboardingFillScroll(Modifier.statusBarsPadding().navigationBarsPadding(), contentModifier = Modifier.padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 20.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        // Header
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Wordmark(32.dp, color = Color.White)
                            Spacer(Modifier.weight(1f))
                            Row(Modifier.nightGlass(20.dp).padding(3.dp).semantics { contentDescription = "Language" }) {
                                WelcomeLangButton("မြန်မာ", myanmar(13, FontWeight.SemiBold), subText)
                                WelcomeLangButton("English", outfit(13, FontWeight.Bold), subText)
                            }
                        }

                        Column(Modifier.padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("C · G/B · Am · F", style = mono(18).copy(letterSpacing = 1.sp), color = OC.gold)
                            Text("Every chord sheet, ready to play.", style = outfit(40, FontWeight.Bold, letterSpacing = (-1.5).sp).copy(lineHeight = 42.sp), color = OC.nightText)
                        }

                        Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            WelcomeFeature(Icons.Outlined.CameraAlt, "Scan any sheet", "Handwritten or printed — Myanmar and English lyrics.", subText)
                            WelcomeFeature(Icons.Outlined.SwapVert, "Transpose, tune and learn", "Change key in a tap, with finger guides for every chord.", subText)
                            WelcomeFeature(Icons.Outlined.Public, "Share with the community", "Find sheets others uploaded, and share your own.", subText)
                        }

                        Spacer(Modifier.height(28.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.fillMaxWidth()) {
                        // Actions
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            // Sign in with Apple has no Android equivalent: Google takes the paper button.
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(OC.rButton)).background(OC.paper)
                                    .plainClickable(enabled = !googleLoading) {
                                        scope.launch {
                                            googleLoading = true
                                            try { googleSignIn(app)?.let { toast = Toast(it, Icons.Filled.Warning) } } finally { googleLoading = false }
                                        }
                                    }
                                    .heightIn(min = 54.dp).alpha(if (googleLoading) 0.8f else 1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (googleLoading) CircularProgressIndicator(Modifier.size(18.dp), color = hex(0x0F0E0D), strokeWidth = 2.dp)
                                Text("Continue with Google", style = outfit(16, FontWeight.Bold), color = hex(0x0F0E0D))
                            }

                            Box(
                                Modifier.fillMaxWidth().border(1.dp, hex(0x4A4743), RoundedCornerShape(OC.rButton)).plainClickable { app.push(Route.SignUp) }.heightIn(min = 54.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("Sign up with email", style = outfit(16, FontWeight.Bold), color = OC.paper) }

                            Box(Modifier.fillMaxWidth().plainClickable { app.push(Route.SignIn) }.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
                                Text("I already have an account · Log in", style = outfit(15, FontWeight.Bold), color = OC.paper)
                            }

                            Box(
                                Modifier.fillMaxWidth().plainClickable { app.continueAsGuest(); app.switchTo(MainTab.Explore) }.heightIn(min = 44.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("Browse shared chords first", style = outfit(15, FontWeight.Bold), color = hex(0xFFC940)) }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("POWERED BY", style = outfit(10, FontWeight.SemiBold, letterSpacing = 2.4.sp), color = OC.nightMuted)
                                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 8.dp, vertical = 5.dp)) {
                                    Image(painterResource(R.drawable.onez_logo), contentDescription = "Onez Codes", modifier = Modifier.width(92.dp), contentScale = ContentScale.FillWidth)
                                }
                            }

                            OnboardingLegalLine(
                                prefix = "By continuing you agree to the ", terms = "Terms", privacy = "Privacy Policy", suffix = ".",
                                baseColor = OC.faint, linkColor = hex(0xFFC940), bold = false,
                                style = outfit(12), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeLangButton(lang: String, font: TextStyle, offColor: Color) {
    val app = LocalApp.current
    val on = app.language == lang
    Box(
        Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 36.dp).clip(RoundedCornerShape(14.dp)).background(if (on) OC.paper else Color.Transparent)
            .plainClickable { app.language = lang }.semantics { contentDescription = if (on) "$lang, selected" else lang }.padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(lang, style = font, color = if (on) hex(0x0F0E0D) else offColor) }
}

@Composable
private fun WelcomeFeature(icon: ImageVector, title: String, sub: String, subColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).nightGlass(14.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = OC.gold, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = outfit(16, FontWeight.Bold), color = OC.nightText)
            Text(sub, style = outfit(14).lineSpacing(2), color = subColor)
        }
    }
}

/** Inline legal sentence where "Terms" and "Privacy Policy" open their screens. */
@Composable
private fun OnboardingLegalLine(
    prefix: String, terms: String, privacy: String, suffix: String, middle: String = " and ",
    baseColor: Color = OC.ink, linkColor: Color = OC.goldText, bold: Boolean = true,
    style: TextStyle, textAlign: TextAlign? = null, modifier: Modifier = Modifier,
) {
    val app = LocalApp.current
    val link = SpanStyle(
        color = linkColor, textDecoration = TextDecoration.Underline,
        fontWeight = if (bold) FontWeight.Bold else null, fontSize = if (bold) 14.sp else style.fontSize,
    )
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = baseColor)) { append(prefix) }
        withLink(LinkAnnotation.Clickable("terms", TextLinkStyles(link)) { app.push(Route.Terms) }) { append(terms) }
        withStyle(SpanStyle(color = baseColor)) { append(middle) }
        withLink(LinkAnnotation.Clickable("privacy", TextLinkStyles(link)) { app.push(Route.Privacy) }) { append(privacy) }
        withStyle(SpanStyle(color = baseColor)) { append(suffix) }
    }
    Text(text, style = style, textAlign = textAlign, modifier = modifier)
}

// MARK: - Sign in (log in → forgot → sent)

private enum class SignInMode { SignIn, Forgot, Sent }

@Composable
fun SignInView() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var mode by remember { mutableStateOf(SignInMode.SignIn) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var socialBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    fun logIn() {
        val e = email.trim()
        if (!e.contains("@")) { error = "Enter the email on your account."; emailFocus.requestFocus(); return }
        if (password.isEmpty()) { error = "Enter your password."; passwordFocus.requestFocus(); return }
        focusManager.clearFocus()
        error = null
        busy = true
        scope.launch {
            try {
                val profile = app.backend.signIn(e, password)
                app.signedIn(profile)
            } catch (f: ServiceException) {
                error = if (f.kind == ServiceException.Kind.Offline) "You’re offline. Check your connection and try again." else f.userMessage
            } catch (c: CancellationException) {
                throw c
            } catch (t: Exception) {
                error = "That email and password don’t match. Try again."
            } finally { busy = false }
        }
    }

    fun sendReset() {
        val e = email.trim()
        if (!e.contains("@")) { error = "Enter a valid email address."; return }
        focusManager.clearFocus()
        error = null
        busy = true
        scope.launch {
            try {
                app.backend.sendReset(e)
                mode = SignInMode.Sent
            } catch (c: CancellationException) {
                throw c
            } catch (t: Exception) {
                error = "Couldn’t send the link. Check your connection and try again."
            } finally { busy = false }
        }
    }

    fun social() {
        if (socialBusy) return
        focusManager.clearFocus()
        error = null
        socialBusy = true
        scope.launch {
            try { googleSignIn(app)?.let { error = it } } finally { socialBusy = false }
        }
    }

    fun backToLogIn() { error = null; mode = SignInMode.SignIn }
    fun goToSignUp() { if (app.authPath.dropLast(1).lastOrNull() == Route.SignUp) app.pop() else app.push(Route.SignUp) }

    OCPage {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            TopBar(onBack = { app.pop() })
            AnimatedContent(mode, Modifier.weight(1f), transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "signInMode") { m ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (m) {
                        SignInMode.SignIn -> {
                            // Log in
                            OnboardingHeading(eyebrow = "Welcome back", title = "Log in")
                            OnboardingField("Email") {
                                OnboardingInput(
                                    email, { email = it }, "you@example.com", Modifier.weight(1f).focusRequester(emailFocus),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                    keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                                )
                            }
                            OnboardingField("Password", trailingPadding = 4.dp) {
                                OnboardingInput(
                                    password, { password = it }, "Your password", Modifier.weight(1f).focusRequester(passwordFocus),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                    keyboardActions = KeyboardActions(onGo = { logIn() }),
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                )
                                OnboardingShowHide(showPassword) { showPassword = !showPassword }
                            }
                            Row(Modifier.fillMaxWidth().offset(y = (-6).dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { error?.let { OnboardingErrorText(it) } }
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.heightIn(min = 32.dp).plainClickable { error = null; mode = SignInMode.Forgot }, contentAlignment = Alignment.Center) {
                                    Text("Forgot password?", style = outfit(14, FontWeight.Bold), color = OC.goldText)
                                }
                            }
                            OnboardingBusyGoldButton("Log in", busy) { logIn() }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.weight(1f).height(1.dp).background(OC.border))
                                Text("or", style = outfit(12), color = OC.faint)
                                Box(Modifier.weight(1f).height(1.dp).background(OC.border))
                            }

                            // Sign in with Apple has no Android equivalent; Google keeps its outlined button.
                            Row(
                                Modifier.fillMaxWidth().border(1.dp, OC.ink, RoundedCornerShape(OC.rButton)).plainClickable(enabled = !socialBusy) { social() }.heightIn(min = 50.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (socialBusy) CircularProgressIndicator(Modifier.size(16.dp), color = OC.ink, strokeWidth = 2.dp)
                                Text("Continue with Google", style = outfit(15, FontWeight.Bold), color = OC.ink)
                            }
                        }
                        SignInMode.Forgot -> {
                            // Reset password
                            OnboardingHeading(eyebrow = "Reset password", title = "Forgot your password?", sub = "Enter the email on your account and we’ll send a reset link.")
                            OnboardingField("Email") {
                                OnboardingInput(
                                    email, { email = it }, "you@example.com", Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                    keyboardActions = KeyboardActions(onSend = { sendReset() }),
                                )
                            }
                            error?.let { OnboardingErrorText(it) }
                            OnboardingBusyGoldButton("Send reset link", busy) { sendReset() }
                            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).plainClickable { backToLogIn() }, contentAlignment = Alignment.Center) {
                                Text("Back to log in", style = outfit(14, FontWeight.Bold), color = OC.goldText)
                            }
                        }
                        SignInMode.Sent -> {
                            // Check your email
                            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(72.dp).clip(CircleShape).background(OC.goldTint), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Email, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(32.dp))
                                }
                                Text("Check your email", style = outfit(28, FontWeight.Bold, letterSpacing = (-1).sp), color = OC.ink, textAlign = TextAlign.Center)
                                Text("If an account exists for that address, a reset link is on its way. It expires in 30 minutes.",
                                    style = outfit(15).lineSpacing(3), color = OC.muted, textAlign = TextAlign.Center)
                                InkButton("Back to log in", Modifier.padding(top = 10.dp)) { backToLogIn() }
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Text("New to O Chords?", style = outfit(14), color = OC.muted)
                Box(Modifier.heightIn(min = 44.dp).plainClickable { goToSignUp() }, contentAlignment = Alignment.Center) {
                    Text("Create an account", style = outfit(14, FontWeight.Bold), color = OC.goldText)
                }
            }
        }
    }
}

// MARK: - Reset password (from the email link)

/**
 * Opened by `ochords://reset-password?token=…`. The email isn't in the link, so the player types it again:
 * the API checks both before changing the password and signs out every other device.
 */
@Composable
fun ResetPasswordView(token: String) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val passwordFocus = remember { FocusRequester() }

    fun submit() {
        val e = email.trim()
        if (!e.contains("@")) { error = "Enter a valid email address."; return }
        if (password.length < 8) { error = "Use at least 8 characters."; return }
        focusManager.clearFocus()
        error = null
        busy = true
        scope.launch {
            try {
                app.backend.resetPassword(e, token, password)
                done = true
            } catch (f: ServiceException) {
                error = if (f.kind == ServiceException.Kind.Offline) "You’re offline. Check your connection and try again."
                else "That link has expired or doesn’t match this email. Ask for a new one."
            } catch (c: CancellationException) {
                throw c
            } catch (t: Exception) {
                error = "That link has expired or doesn’t match this email. Ask for a new one."
            } finally { busy = false }
        }
    }

    OCPage {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            TopBar(onBack = { app.pop() })
            AnimatedContent(done, Modifier.weight(1f), transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "reset") { isDone ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    if (isDone) {
                        Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(72.dp).clip(CircleShape).background(OC.goldTint), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(32.dp))
                            }
                            Text("Password changed", style = outfit(28, FontWeight.Bold, letterSpacing = (-1).sp), color = OC.ink, textAlign = TextAlign.Center)
                            Text("Log in with your new password.", style = outfit(15).lineSpacing(3), color = OC.muted, textAlign = TextAlign.Center)
                            InkButton("Log in", Modifier.padding(top = 10.dp)) { app.authPath = listOf(Route.SignIn) }
                        }
                    } else {
                        OnboardingHeading(eyebrow = "Reset password", title = "Choose a new password",
                            sub = "Enter the email on your account and a new password. You’ll be logged out everywhere else.")
                        OnboardingField("Email") {
                            OnboardingInput(
                                email, { email = it }, "you@example.com", Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                            )
                        }
                        OnboardingField("New password", trailingPadding = 4.dp) {
                            OnboardingInput(
                                password, { password = it }, "At least 8 characters", Modifier.weight(1f).focusRequester(passwordFocus),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                                keyboardActions = KeyboardActions(onGo = { submit() }),
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            )
                            OnboardingShowHide(showPassword) { showPassword = !showPassword }
                        }
                        error?.let { OnboardingErrorText(it) }
                        OnboardingBusyGoldButton("Save new password", busy) { submit() }
                    }
                }
            }
        }
    }
}

// MARK: - Sign up

private val allInstruments = listOf("Guitar", "Ukulele", "Bass", "Keys", "Vocals")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignUpView() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var instruments by remember { mutableStateOf(setOf("Guitar")) }
    var agree by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    val rules = listOf(
        "At least 8 characters" to (password.length >= 8),
        "A number" to password.any { it.isDigit() },
        "An uppercase letter" to password.any { it.isUpperCase() },
    )
    val score = rules.count { it.second } + (if (password.length >= 12) 1 else 0)
    val ready = agree && rules.all { it.second }

    fun create() {
        val e = email.trim()
        if (!e.contains("@")) { error = "Enter a valid email address."; emailFocus.requestFocus(); return }
        focusManager.clearFocus()
        error = null
        busy = true
        scope.launch {
            try {
                val profile = app.backend.signUp(name.trim(), e, password)
                app.signedIn(profile)
            } catch (f: ServiceException) {
                error = when (f.kind) {
                    ServiceException.Kind.Offline -> "You’re offline. Check your connection and try again."
                    // e.g. "An account with this email already exists."
                    ServiceException.Kind.Server -> f.userMessage
                    else -> "Couldn’t create your account. Try again in a moment."
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Exception) {
                error = "Couldn’t create your account. Try again in a moment."
            } finally { busy = false }
        }
    }

    fun goToSignIn() { if (app.authPath.dropLast(1).lastOrNull() == Route.SignIn) app.pop() else app.push(Route.SignIn) }

    OCPage {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(onBack = { app.pop() })
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OnboardingHeading(eyebrow = "Join the community", title = "Create account")
                OnboardingField("Display name") {
                    OnboardingInput(
                        name, { name = it }, "How others see you", Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Words),
                        keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
                    )
                }
                OnboardingField("Email") {
                    OnboardingInput(
                        email, { email = it }, "you@example.com", Modifier.weight(1f).focusRequester(emailFocus),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                    )
                }
                OnboardingField("Password") {
                    OnboardingInput(
                        password, { password = it }, "At least 8 characters", Modifier.weight(1f).focusRequester(passwordFocus),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }

                // Password strength
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) { i ->
                            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (i < score) (if (score >= 3) OC.teal else OC.gold) else OC.ink.copy(alpha = 0.09f)))
                        }
                    }
                    rules.forEach { (rule, met) ->
                        Row(
                            Modifier.semantics { contentDescription = "$rule, ${if (met) "done" else "not yet"}" },
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (met) "✓" else "·", style = outfit(13, FontWeight.Heavy), color = if (met) OC.tealText else OC.muted, modifier = Modifier.width(16.dp), textAlign = TextAlign.Center)
                            Text(rule, style = outfit(13), color = if (met) OC.tealText else OC.muted)
                        }
                    }
                }

                // Instruments
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = OC.ink)) { append("I play ") }
                        withStyle(SpanStyle(color = OC.muted, fontWeight = FontWeight.Medium)) { append("(pick any)") }
                    }, style = outfit(13, FontWeight.SemiBold))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        allInstruments.forEach { inst ->
                            val on = inst in instruments
                            val shape = RoundedCornerShape(14.dp)
                            Box(
                                Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 38.dp).clip(shape).background(if (on) OC.goldTint else OC.glass)
                                    .border(1.dp, if (on) OC.gold else OC.border, shape)
                                    .plainClickable { instruments = if (on) instruments - inst else instruments + inst }
                                    .semantics { contentDescription = if (on) "$inst, selected" else inst }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(inst, style = outfit(14, FontWeight.SemiBold), color = OC.ink) }
                        }
                    }
                }

                // Agreement
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    val shape = RoundedCornerShape(8.dp)
                    Box(
                        Modifier.size(22.dp).clip(shape).background(if (agree) OC.gold else OC.glass).border(2.dp, if (agree) OC.gold else OC.faint, shape)
                            .plainClickable(role = Role.Checkbox) { agree = !agree }
                            .semantics { contentDescription = "I agree to the Terms and Privacy Policy" },
                        contentAlignment = Alignment.Center,
                    ) { if (agree) Icon(Icons.Filled.Check, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(14.dp)) }
                    OnboardingLegalLine(
                        prefix = "I agree to the ", terms = "Terms & Conditions", privacy = "Privacy Policy", suffix = ".",
                        middle = " and have read the ", style = outfit(14).lineSpacing(3),
                    )
                }

                error?.let { OnboardingErrorText(it) }
            }

            // Footer
            BottomBar {
                OnboardingGatedBusyButton("Create account", enabled = ready, busy = busy) { create() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Text("Already have an account?", style = outfit(14), color = OC.muted)
                    Box(Modifier.heightIn(min = 44.dp).plainClickable { goToSignIn() }, contentAlignment = Alignment.Center) {
                        Text("Log in", style = outfit(14, FontWeight.Bold), color = OC.goldText)
                    }
                }
            }
        }
    }
}

// MARK: - Ad consent (modal)

private data class AdOption(val title: String, val sub: String, val personalised: Boolean)

private val adOptions = listOf(
    AdOption("Ads that fit me", "Google may use app activity to show ads you’re more likely to care about, like guitar gear.", true),
    AdOption("General ads only", "Same number of ads, not based on your activity.", false),
)

@Composable
fun AdConsentView() {
    val app = LocalApp.current
    var personalised by remember { mutableStateOf(app.personalisedAds) }

    OCPage {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    AppIconTile(104.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow("Ads keep O Chords free")
                        Text("Choose how ads work for you", style = outfit(28, FontWeight.Bold, letterSpacing = (-0.7).sp), color = OC.ink, textAlign = TextAlign.Center)
                        Text("The free plan shows a few ads from Google. Never while you play, tune or take a lesson.",
                            style = outfit(15).lineSpacing(3), color = OC.muted, textAlign = TextAlign.Center)
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        adOptions.forEach { o ->
                            val on = personalised == o.personalised
                            val shape = RoundedCornerShape(16.dp)
                            Row(
                                Modifier.fillMaxWidth().clip(shape).background(if (on) OC.goldTint else OC.glass)
                                    .border(if (on) 2.dp else 1.dp, if (on) OC.gold else OC.border, shape)
                                    .plainClickable(role = Role.RadioButton) { personalised = o.personalised }
                                    .semantics { contentDescription = if (on) "${o.title}, selected" else o.title }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top,
                            ) {
                                Box(Modifier.padding(top = 1.dp).size(22.dp).border(2.dp, if (on) OC.ink else hex(0xC9C6C0), CircleShape), contentAlignment = Alignment.Center) {
                                    if (on) Box(Modifier.size(10.dp).clip(CircleShape).background(OC.ink))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(o.title, style = outfit(15, FontWeight.Bold), color = OC.ink)
                                    Text(o.sub, style = outfit(13).lineSpacing(2), color = OC.muted)
                                }
                            }
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GoldButton("Continue") {
                    app.personalisedAds = personalised
                    app.modal = null
                    // Android has no tracking prompt: AdMob starts straight away with the choice applied.
                    app.scope.launch {
                        app.ads.start()
                        app.ads.preloadInterstitial()
                    }
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).plainClickable { app.modal = Modal.Pro }, contentAlignment = Alignment.Center) {
                    Text("No ads at all with Pro", style = outfit(14, FontWeight.Bold), color = OC.ink)
                }
                Text("Change any time in Settings › Ad choices.", style = outfit(12).lineSpacing(2), color = OC.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

// MARK: - Permission primers (camera · microphone · notifications)

private class PrimerContent(val icon: ImageVector, val eyebrow: String, val title: String, val points: List<Pair<String, String>>, val cta: String, val fine: String)

private fun primerContent(kind: PermissionKind): PrimerContent = when (kind) {
    PermissionKind.Camera -> PrimerContent(
        Icons.Outlined.CameraAlt, "Camera", "Scan chord sheets with your camera",
        listOf(
            "Scan paper sheets" to "Point at a songbook or handwritten page — we read it in seconds.",
            "Only when you tap Scan" to "The camera never runs in the background.",
            "Photos stay private" to "Read to find chords and Myanmar lyrics, never stored, and only shared if you make a song public.",
        ),
        "Allow camera", "Next, Android will ask you to confirm. You can change this any time in Settings.",
    )
    PermissionKind.Microphone -> PrimerContent(
        Icons.Outlined.Mic, "Microphone", "Let O Chords hear your guitar",
        listOf(
            "Tune by ear" to "The tuner listens to each string and shows how far off it is.",
            "Check your chords" to "Hear which string buzzes or is muted.",
            "Never recorded" to "Sound is analysed on your phone and never uploaded.",
        ),
        "Allow microphone", "Next, Android will ask you to confirm. You can change this any time in Settings.",
    )
    PermissionKind.Notifications -> PrimerContent(
        Icons.Outlined.NotificationsNone, "Notifications", "Keep your groove going",
        listOf(
            "One daily reminder" to "At the time you choose — we’ll suggest 7:30 pm.",
            "Groove at risk" to "A nudge before your streak ends, not after.",
            "Community news" to "When someone saves or improves your sheet.",
        ),
        "Turn on notifications", "You can pick which ones you get in Settings › Notifications.",
    )
}

@Composable
fun PermissionPrimerView(kind: PermissionKind) {
    val app = LocalApp.current
    val c = remember(kind) { primerContent(kind) }
    var requesting by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requesting = false
        app.modal = null
    }

    fun allow() {
        requesting = true
        val permission = when (kind) {
            PermissionKind.Camera -> Manifest.permission.CAMERA
            PermissionKind.Microphone -> Manifest.permission.RECORD_AUDIO
            // Before Android 13 notifications need no runtime permission.
            PermissionKind.Notifications -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
        }
        if (permission == null) { requesting = false; app.modal = null } else launcher.launch(permission)
    }

    OCPage {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.End) {
                Box(Modifier.size(44.dp).plainClickable { app.modal = null }.semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = OC.ink, modifier = Modifier.size(22.dp))
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp).padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    val iconShape = RoundedCornerShape(34.dp)
                    Box(
                        Modifier.size(104.dp).shadow(20.dp, iconShape, ambientColor = hex(0xFFAA00, 0.18f), spotColor = hex(0xFFAA00, 0.18f))
                            .clip(iconShape).background(OC.goldTint.copy(alpha = 0.85f)).border(1.dp, OC.glassBorder, iconShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(c.icon, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(38.dp)) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Eyebrow(c.eyebrow)
                        Text(c.title, style = outfit(30, FontWeight.Bold, letterSpacing = (-0.8).sp), color = OC.ink, textAlign = TextAlign.Center)
                    }
                    Column(Modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        c.points.forEach { (title, sub) ->
                            Row(Modifier.semantics { contentDescription = "$title. $sub" }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.size(26.dp).clip(CircleShape).background(OC.tealTint), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = OC.tealText, modifier = Modifier.size(14.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(title, style = outfit(16, FontWeight.Bold), color = OC.ink)
                                    Text(sub, style = outfit(14).lineSpacing(2), color = OC.muted)
                                }
                            }
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OnboardingBusyGoldButton(c.cta, requesting) { allow() }
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).plainClickable { app.modal = null }, contentAlignment = Alignment.Center) {
                    Text("Not now", style = outfit(15, FontWeight.Bold), color = OC.muted)
                }
                Text(c.fine, style = outfit(12).lineSpacing(2), color = OC.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

// MARK: - Shared helpers (onboarding group)

/** SwiftUI `lineSpacing(n)`: extra points between lines. */
private fun TextStyle.lineSpacing(extra: Int): TextStyle = copy(lineHeight = (fontSize.value * 1.2f + extra).sp)

/** Eyebrow + large title (+ optional sub) used on the auth screens. */
@Composable
private fun OnboardingHeading(eyebrow: String, title: String, sub: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow(eyebrow)
        Text(title, style = outfit(34, FontWeight.Bold, letterSpacing = (-1.4).sp), color = OC.ink)
        if (sub != null) Text(sub, style = outfit(15).lineSpacing(3), color = OC.muted)
    }
}

/** Labelled 50dp glass input. */
@Composable
private fun OnboardingField(label: String, trailingPadding: Dp = 14.dp, input: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = outfit(13, FontWeight.SemiBold), color = OC.ink)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).glass(14.dp).padding(start = 14.dp, end = trailingPadding).semantics { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically, content = input,
        )
    }
}

/** The text field inside an `OnboardingField`: placeholder in the design's muted grey. */
@Composable
private fun OnboardingInput(
    value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    BasicTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, singleLine = true,
        textStyle = outfit(16).copy(color = OC.ink), cursorBrush = SolidColor(OC.gold),
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, visualTransformation = visualTransformation,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = outfit(16), color = OC.faint)
                inner()
            }
        },
    )
}

@Composable
private fun OnboardingShowHide(shown: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).plainClickable(onClick = onToggle).semantics { contentDescription = if (shown) "Hide password" else "Show password" }.padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(if (shown) "Hide" else "Show", style = outfit(13, FontWeight.Bold), color = OC.goldText) }
}

@Composable
private fun OnboardingErrorText(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Error, contentDescription = null, tint = OC.red, modifier = Modifier.size(16.dp))
        Text(text, style = outfit(13, FontWeight.SemiBold), color = OC.red)
    }
}

/** Gold button whose label swaps to a spinner while busy. */
@Composable
private fun OnboardingBusyGoldButton(title: String, busy: Boolean, onClick: () -> Unit) {
    GoldButton(
        text = if (busy) "" else title, enabled = !busy,
        leading = if (busy) ({ CircularProgressIndicator(Modifier.size(20.dp), color = OC.inkFixed, strokeWidth = 2.dp) }) else null,
        onClick = onClick,
    )
}

/** Primary button that looks disabled (grey) until `enabled`; spinner while busy. */
@Composable
private fun OnboardingGatedBusyButton(title: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(OC.rButton)
    Box(
        Modifier.fillMaxWidth().clip(shape).background(if (enabled) OC.gold else OC.ink.copy(alpha = 0.08f))
            .plainClickable(enabled = enabled && !busy, onClick = onClick).heightIn(min = 54.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = outfit(16, FontWeight.Bold), color = if (enabled) OC.inkFixed else OC.faint, modifier = Modifier.alpha(if (busy) 0f else 1f))
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = OC.inkFixed, strokeWidth = 2.dp)
    }
}

/** Scroll view whose content is at least as tall as the viewport (so weighted spacers work, but small screens can scroll). */
@Composable
private fun OnboardingFillScroll(modifier: Modifier = Modifier, contentModifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).then(contentModifier), content = content)
    }
}
