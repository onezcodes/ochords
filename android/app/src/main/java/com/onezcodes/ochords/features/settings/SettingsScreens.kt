package com.onezcodes.ochords.features.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import com.onezcodes.ochords.R
import com.onezcodes.ochords.app.LocalApp
import com.onezcodes.ochords.app.MainTab
import com.onezcodes.ochords.app.Modal
import com.onezcodes.ochords.app.PermissionKind
import com.onezcodes.ochords.app.Route
import com.onezcodes.ochords.design.AppIconTile
import com.onezcodes.ochords.design.Appearance
import com.onezcodes.ochords.design.BottomBar
import com.onezcodes.ochords.design.ChoiceCard
import com.onezcodes.ochords.design.Eyebrow
import com.onezcodes.ochords.design.GlassButton
import com.onezcodes.ochords.design.GlassList
import com.onezcodes.ochords.design.GoldButton
import com.onezcodes.ochords.design.GroupHeader
import com.onezcodes.ochords.design.GuestAccountCard
import com.onezcodes.ochords.design.Hairline
import com.onezcodes.ochords.design.Heavy
import com.onezcodes.ochords.design.InkButton
import com.onezcodes.ochords.design.ListRow
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.OCPage
import com.onezcodes.ochords.design.RadioDot
import com.onezcodes.ochords.design.Toast
import com.onezcodes.ochords.design.ToastHost
import com.onezcodes.ochords.design.ToggleRow
import com.onezcodes.ochords.design.TopBar
import com.onezcodes.ochords.design.glass
import com.onezcodes.ochords.design.hex
import com.onezcodes.ochords.design.inkGlass
import com.onezcodes.ochords.design.isDark
import com.onezcodes.ochords.design.mono
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.design.plainClickable
import com.onezcodes.ochords.services.StoreService
import com.onezcodes.ochords.services.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat

// MARK: - Shared helpers (file-private)

private data class SettingsAppInfo(val version: String, val build: String)

/** App version and build from the package manager (the iOS `CFBundleShortVersionString` / `CFBundleVersion`). */
@Composable
private fun rememberAppInfo(): SettingsAppInfo {
    val context = LocalContext.current
    return remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            SettingsAppInfo(info.versionName ?: "1.0", PackageInfoCompat.getLongVersionCode(info).toString())
        }.getOrDefault(SettingsAppInfo("1.0", "1"))
    }
}

/** Opens a web page in the browser. */
private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Google Play's subscription management for O Chords Pro (the App Store's "Manage subscription" on iOS). */
private fun openSubscriptions(context: Context) {
    openUrl(context, "https://play.google.com/store/account/subscriptions?sku=${StoreService.productID}&package=${context.packageName}")
}

/** True when a legal page is shown in a sheet (e.g. from the Pro paywall) — hides cross-links. */
private val LocalSettingsLegalInSheet = compositionLocalOf { false }

/** Closes the sheet a legal page is shown in. */
private val LocalSettingsLegalClose = compositionLocalOf<() -> Unit> { {} }

/** SwiftUI `lineSpacing(n)`: extra points between lines. */
private fun TextStyle.lineSpacing(extra: Int): TextStyle = copy(lineHeight = (fontSize.value * 1.2f + extra).sp)

/** Tappable list row (wraps `ListRow`). */
@Composable
private fun SettingsLinkRow(title: String, value: String? = null, tint: Color = OC.ink, action: () -> Unit) {
    ListRow(title, value = value, tint = tint, onClick = action)
}

/** Rows of a glass list with hairlines between them. */
@Composable
private fun SettingsRows(vararg rows: @Composable () -> Unit) {
    rows.forEachIndexed { i, row ->
        if (i > 0) Hairline()
        row()
    }
}

/** "── TITLE" header over a glass list. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupHeader(title)
        GlassList(content = content)
    }
}

/** Close (X) header with centred title and optional trailing view. */
@Composable
private fun SettingsCloseBar(title: String = "", onClose: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).plainClickable(onClick = onClose).semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = OC.ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = outfit(16, FontWeight.Bold), color = OC.ink)
        Spacer(Modifier.weight(1f))
        Box(Modifier.widthIn(min = 44.dp), contentAlignment = Alignment.CenterEnd) { trailing() }
    }
}

/** Primary button that looks disabled (grey) until `enabled`, with a spinner + busy label while working. */
@Composable
private fun SettingsBusyButton(title: String, enabled: Boolean, busy: Boolean, onColor: Color = OC.gold, onText: Color = OC.inkFixed, onClick: () -> Unit) {
    val shape = RoundedCornerShape(OC.rButton)
    val on = enabled || busy
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (on) onColor else OC.border).plainClickable(enabled = enabled && !busy, onClick = onClick).heightIn(min = 54.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = onText, strokeWidth = 2.dp)
        Text(title, style = outfit(16, FontWeight.Bold), color = if (on) onText else OC.faint)
    }
}

/** Plain text button (gold or ink) with a 44dp hit area. */
@Composable
private fun SettingsTextButton(text: String, color: Color = OC.goldText, modifier: Modifier = Modifier, minHeight: Dp = 44.dp, style: TextStyle = outfit(14, FontWeight.Bold), onClick: () -> Unit) {
    Box(modifier.heightIn(min = minHeight).plainClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, style = style, color = color)
    }
}

// MARK: - Settings

/** First letter of the display name, skipping placeholder brackets ("[Your name]" → "Y"). */
private fun settingsInitial(name: String?): String = name?.firstOrNull { it.isLetter() }?.uppercase() ?: "Y"

@Composable
fun SettingsView() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val info = rememberAppInfo()
    var showSignOut by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Toast?>(null) }

    // Local-only demo preferences
    var reminders by remember { mutableStateOf(true) }
    var community by remember { mutableStateOf(true) }
    var offlineSongs by remember { mutableStateOf(false) }
    var cellular by remember { mutableStateOf(false) }

    /** A guest using the real backend has no account to show. */
    val isGuest = !app.isSignedIn && !app.usesDemoData
    val dark = isDark

    BackHandler(enabled = showSignOut) { showSignOut = false }

    OCPage {
        ToastHost(toast, onDismiss = { toast = null }) {
            Column(Modifier.fillMaxSize()) {
                TopBar(title = "Settings", backLabel = "Back to profile", onBack = { app.pop() })
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 32.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // Profile card
                    if (isGuest) GuestAccountCard() else {
                        val p = app.profile
                        Row(
                            Modifier.fillMaxWidth().glass().plainClickable { app.push(Route.Profile) }.semantics { contentDescription = "Edit profile" }.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(52.dp).clip(CircleShape).background(hex(0x1E3A8A)), contentAlignment = Alignment.Center) {
                                Text(settingsInitial(p?.user?.name), style = outfit(20, FontWeight.Heavy), color = Color.White)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(p?.user?.name ?: "[Your name]", style = outfit(17, FontWeight.Bold), color = OC.ink)
                                Text("${p?.email ?: "[email]"} · ${p?.user?.tier?.title ?: "Chord Keeper"}", style = outfit(13), color = OC.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = OC.faint, modifier = Modifier.size(18.dp))
                        }
                    }

                    if (!isGuest) SettingsGroup("Account") {
                        SettingsRows(
                            { SettingsLinkRow("Edit profile") { app.push(Route.Profile) } },
                            { SettingsLinkRow("Email", value = app.profile?.email ?: "[email]") { toast = Toast("Email changes come with the server", Icons.Filled.Email) } },
                            { SettingsLinkRow("Password", value = "Change") { toast = Toast("Reset link sent to your email", Icons.Filled.Email) } },
                            { SettingsLinkRow("Sign-in methods", value = "Google") { toast = Toast("Signed in with Google", Icons.Filled.AccountCircle) } },
                        )
                    }

                    SettingsGroup("Subscription") {
                        SettingsRows(
                            { SettingsLinkRow("O Chords Pro", value = if (app.isPro) "Pro" else "Free plan") { app.modal = Modal.Pro } },
                            {
                                SettingsLinkRow("Restore purchases") {
                                    scope.launch {
                                        app.restorePurchases()
                                        toast = if (app.isPro) Toast("Pro is active") else Toast("No purchases to restore", Icons.Filled.Info)
                                    }
                                }
                            },
                        )
                    }

                    SettingsGroup("Playing") {
                        SettingsRows(
                            { SettingsLinkRow("Chord names", value = if (app.sharpNames) "C♯" else "D♭") { app.push(Route.Profile) } },
                            { SettingsLinkRow("Default tuning", value = "Standard") { app.switchTo(MainTab.Tuner) } },
                            { SettingsLinkRow("App language", value = app.language) { toast = Toast("More languages coming soon", Icons.Filled.Public) } },
                            {
                                ToggleRow("Dark mode", isOn = app.appearance == Appearance.Dark || (app.appearance == Appearance.System && dark), subtitle = "Easier on stage at night") {
                                    app.appearance = if (it) Appearance.Dark else Appearance.Light
                                }
                            },
                            { ToggleRow("Left-handed", isOn = app.leftHanded, subtitle = "Mirror chord diagrams") { app.leftHanded = it } },
                        )
                    }

                    SettingsGroup("Scanning & practice") {
                        SettingsRows(
                            { ToggleRow("Always convert Zawgyi", isOn = app.alwaysConvertZawgyi, subtitle = "Fix Zawgyi lyrics to Unicode automatically") { app.alwaysConvertZawgyi = it } },
                            { ToggleRow("Always skip warm up", isOn = app.alwaysSkipWarmup, subtitle = "Go straight to the song") { app.alwaysSkipWarmup = it } },
                        )
                    }

                    SettingsGroup("Notifications") {
                        SettingsRows(
                            { ToggleRow("Practice reminders", isOn = reminders, subtitle = "Every day at 8:00 PM") { reminders = it } },
                            { ToggleRow("Community activity", isOn = community, subtitle = "Saves, fixes and new followers") { community = it } },
                            { SettingsLinkRow("Notification permission", value = "Set up") { app.modal = Modal.Permission(PermissionKind.Notifications) } },
                        )
                    }

                    SettingsGroup("Storage") {
                        SettingsRows(
                            {
                                ToggleRow("Offline songs", isOn = offlineSongs, subtitle = "Pro · keeps saved songs on this phone") { v ->
                                    if (v && !app.isPro) app.modal = Modal.Pro else offlineSongs = v
                                }
                            },
                            { ToggleRow("Download on cellular", isOn = cellular) { cellular = it } },
                            { SettingsLinkRow("Clear cache", value = "[size]") { toast = Toast("Cache cleared", Icons.Filled.Delete) } },
                        )
                    }

                    SettingsGroup("Ads") {
                        SettingsRows(
                            { SettingsLinkRow("Ad choices", value = if (app.personalisedAds) "Ads that fit me" else "General ads") { app.push(Route.AdChoices) } },
                            { SettingsLinkRow("Remove ads", value = if (app.isPro) "Removed" else "Pro") { app.modal = Modal.Pro } },
                        )
                    }

                    SettingsGroup("Support") {
                        SettingsRows(
                            { SettingsLinkRow("Help center") { app.push(Route.Help) } },
                            { SettingsLinkRow("Send feedback") { app.push(Route.Feedback) } },
                            { SettingsLinkRow("Rate O Chords") { toast = Toast("Thanks for the love!", Icons.Filled.Star) } },
                        )
                    }

                    SettingsGroup("About") {
                        SettingsRows(
                            { SettingsLinkRow("About O Chords") { app.push(Route.About) } },
                            { SettingsLinkRow("Terms & Conditions") { app.push(Route.Terms) } },
                            { SettingsLinkRow("Privacy Policy") { app.push(Route.Privacy) } },
                        )
                    }

                    SettingsGroup("Demo") {
                        ToggleRow("Simulate offline (demo)", isOn = app.isOffline, subtitle = "Shows the offline states across the app") { app.isOffline = it }
                    }

                    // Footer
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!isGuest) {
                            GlassButton("Sign out", height = 52.dp) { showSignOut = true }
                            SettingsTextButton("Delete account", color = OC.red, modifier = Modifier.fillMaxWidth()) { app.push(Route.DeleteAccount) }
                        }
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("O Chords ${info.version} (${info.build})", style = outfit(12), color = OC.faint)
                            Column(
                                Modifier.padding(top = 6.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, hex(0xECEAE6), RoundedCornerShape(16.dp))
                                    .plainClickable { app.push(Route.About) }.semantics { contentDescription = "Powered by Onez Codes" }
                                    .padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("POWERED BY", style = outfit(10, FontWeight.SemiBold, letterSpacing = 3.sp), color = OC.faint)
                                Image(painterResource(R.drawable.onez_logo), contentDescription = null, modifier = Modifier.width(128.dp), contentScale = ContentScale.FillWidth)
                            }
                        }
                    }
                }
            }
        }

        // Sign-out sheet
        AnimatedVisibility(showSignOut, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
            Box(Modifier.fillMaxSize().background(hex(0x171614, 0.45f)).plainClickable(role = null) { showSignOut = false })
        }
        AnimatedVisibility(
            showSignOut, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(250)) { it }, exit = slideOutVertically(tween(250)) { it },
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(OC.surface)
                    .semantics { contentDescription = "Sign out" }
                    .padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 12.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Sign out of O Chords?", style = outfit(20, FontWeight.Bold), color = OC.ink)
                Text("Your library, uploads and groove are saved to your account. Offline songs will be removed from this phone.",
                    style = outfit(14).lineSpacing(3), color = OC.muted)
                InkButton("Sign out", height = 52.dp) { showSignOut = false; app.signOut() }
                GlassButton("Cancel", height = 48.dp) { showSignOut = false }
            }
        }
    }
}

// MARK: - Help center

private data class HelpFAQ(val topic: Int, val q: String, val a: String)

private val helpTopics: List<Pair<String, ImageVector>> = listOf(
    "Scanning" to Icons.Outlined.CameraAlt,
    "Playing" to Icons.Outlined.MusicNote,
    "Sharing" to Icons.Outlined.Share,
    "Tiers & points" to Icons.Outlined.Shield,
    "Pro & billing" to Icons.Outlined.CreditCard,
    "Account" to Icons.Outlined.Person,
)

private val helpFaqs = listOf(
    HelpFAQ(0, "Why did the scan get some chords wrong?", "Blurry photos and small handwriting are the usual cause. Hold the phone flat, fill the frame with the sheet and use good light. You can fix any chord on the Review screen before saving."),
    HelpFAQ(0, "Can it read Myanmar lyrics?", "Yes — Myanmar and English lyrics are both supported. Words the app isn’t sure about are highlighted so you can check them."),
    HelpFAQ(1, "How do I change the key?", "Open a song and use − and + next to KEY. Every chord changes together, and the original sheet stays the same."),
    HelpFAQ(1, "What does a capo do to the chords?", "A capo raises the pitch. O Chords can suggest a capo so you can play easier shapes in the same key."),
    HelpFAQ(2, "Who can see my uploads?", "Only you, until you choose Public. You can switch any upload back to Only me from your profile."),
    HelpFAQ(3, "How do I reach the next tier?", "Earn O Points by learning (lesson beats, your groove, positions) and contributing (public sheets, accepted fixes, saves). Pro doesn’t change your tier."),
    HelpFAQ(4, "How do I cancel Pro?", "Go to Google Play › Payments & subscriptions › Subscriptions › O Chords, at least 24 hours before it renews."),
    HelpFAQ(5, "How do I delete my account?", "Settings › Delete account. You can choose to keep your public sheets up without your name."),
)

@Composable
fun HelpView() {
    val app = LocalApp.current
    var query by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<String?>(helpFaqs.firstOrNull()?.q) }

    val trimmed = query.trim().lowercase()
    val results = if (trimmed.isEmpty()) helpFaqs.filter { it.topic == topic } else helpFaqs.filter { (it.q + " " + it.a).lowercase().contains(trimmed) }
    LaunchedEffect(query) { open = results.firstOrNull()?.q }

    OCPage {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(title = "Help center", backLabel = "Back to settings")
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 32.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("How can we help?", style = outfit(30, FontWeight.SemiBold, letterSpacing = (-1.2).sp), color = OC.ink)

                // Search
                Row(Modifier.fillMaxWidth().height(48.dp).glass(OC.rButton).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = OC.muted, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        query, { query = it }, Modifier.weight(1f).semantics { contentDescription = "Search help" }, singleLine = true,
                        textStyle = outfit(16).copy(color = OC.ink), cursorBrush = SolidColor(OC.gold),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text("Search questions", style = outfit(16), color = OC.faint)
                                inner()
                            }
                        },
                    )
                    if (query.isNotEmpty()) {
                        Box(Modifier.width(32.dp).height(44.dp).plainClickable { query = "" }.semantics { contentDescription = "Clear search" }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Cancel, contentDescription = null, tint = OC.faint, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Topic grid
                if (trimmed.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        helpTopics.withIndex().chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (i, t) ->
                                    val on = i == topic
                                    val shape = RoundedCornerShape(OC.rCard)
                                    Column(
                                        Modifier.weight(1f).height(76.dp).clip(shape).background(if (on) OC.goldTint else OC.glass).border(1.dp, if (on) OC.gold else OC.border, shape)
                                            .plainClickable { topic = i; open = helpFaqs.firstOrNull { it.topic == i }?.q }
                                            .semantics { contentDescription = if (on) "${t.first}, selected" else t.first }
                                            .padding(10.dp),
                                    ) {
                                        Icon(t.second, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.weight(1f))
                                        Text(t.first, style = outfit(13, FontWeight.Bold), color = OC.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                // FAQ list
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupHeader(if (trimmed.isEmpty()) helpTopics[topic].first else "${results.size} results")
                    if (results.isEmpty()) {
                        Text("No answers match. Ask us directly below.", style = outfit(14), color = OC.muted, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        GlassList {
                            results.forEach { f ->
                                val isOpen = open == f.q
                                Column(Modifier.animateContentSize()) {
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 54.dp).plainClickable { open = if (isOpen) null else f.q }
                                            .semantics { contentDescription = "${f.q}, ${if (isOpen) "Expanded" else "Collapsed"}" }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(f.q, style = outfit(15, FontWeight.SemiBold).lineSpacing(2), color = OC.ink, modifier = Modifier.weight(1f))
                                        Icon(if (isOpen) Icons.Filled.Remove else Icons.Filled.Add, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(18.dp))
                                    }
                                    if (isOpen) {
                                        Text(f.a, style = outfit(14).lineSpacing(4), color = OC.bodyText, modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Still stuck
                Column(Modifier.fillMaxWidth().inkGlass().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Still stuck?", style = outfit(16, FontWeight.Bold), color = OC.paper)
                    Text("Message the Onez Codes support team. We usually reply within [n] hours.", style = outfit(14).lineSpacing(3), color = hex(0xBDB9B3))
                    GoldButton("Contact support", height = 48.dp) { app.push(Route.Feedback) }
                }
            }
        }
    }
}

// MARK: - Feedback

private data class FeedbackKind(val label: String, val sub: String, val placeholder: String)

private val feedbackKinds = listOf(
    FeedbackKind("Something’s broken", "A bug or crash", "What happened, and what did you expect?"),
    FeedbackKind("Wrong chords", "In a song or scan", "Which song and which line?"),
    FeedbackKind("Idea", "A feature you want", "What would make O Chords better for you?"),
    FeedbackKind("Other", "Anything else", "Tell us anything."),
)

@Composable
fun FeedbackView() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val info = rememberAppInfo()
    var kind by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf("") }
    var deviceInfo by remember { mutableStateOf(true) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) photo = uri }

    val canSend = text.trim().length >= 5 && !sending

    fun send() {
        if (!canSend) return
        focusManager.clearFocus()
        sending = true
        val kindLabel = feedbackKinds[kind].label
        val message = text + (if (deviceInfo) "\n\n[O Chords ${info.version} (${info.build}) · Android ${Build.VERSION.RELEASE}]" else "")
        scope.launch {
            runCatching { app.backend.sendFeedback(kindLabel, message) }
            sending = false
            sent = true
        }
    }

    OCPage {
        Column(Modifier.fillMaxSize().imePadding()) {
            SettingsCloseBar(title = "Send feedback", onClose = { app.pop() })
            AnimatedContent(sent, Modifier.weight(1f), transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "feedback") { isSent ->
                if (isSent) {
                    // Sent
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(top = 96.dp, bottom = 12.dp).navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier.size(76.dp).shadow(10.dp, CircleShape, ambientColor = hex(0xFFAA00, 0.35f), spotColor = hex(0xFFAA00, 0.35f))
                                .clip(CircleShape).background(Brush.verticalGradient(listOf(OC.goldTop, OC.goldBottom))),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Check, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(34.dp)) }
                        Text("Thanks — we got it", style = outfit(28, FontWeight.SemiBold, letterSpacing = (-1).sp), color = OC.ink, textAlign = TextAlign.Center)
                        Text("The Onez Codes team reads every message. If you reported a chord mistake that we fix, you’ll earn O Points.",
                            style = outfit(15).lineSpacing(3), color = OC.muted, textAlign = TextAlign.Center)
                        Spacer(Modifier.weight(1f))
                        InkButton("Done") { app.pop() }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("What is it about?", style = outfit(13, FontWeight.SemiBold), color = OC.ink)
                                feedbackKinds.withIndex().chunked(2).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { (i, k) ->
                                            ChoiceCard(isOn = i == kind, modifier = Modifier.weight(1f), onClick = { kind = i }) {
                                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(k.label, style = outfit(15, FontWeight.Bold), color = OC.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(k.sub, style = outfit(12), color = OC.muted)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Tell us more", style = outfit(13, FontWeight.SemiBold), color = OC.ink)
                                Box(Modifier.fillMaxWidth().height(150.dp).glass()) {
                                    BasicTextField(
                                        text, { text = it.take(1000) },
                                        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp).semantics { contentDescription = "Tell us more" },
                                        textStyle = outfit(15).copy(color = OC.ink), cursorBrush = SolidColor(OC.gold),
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                        decorationBox = { inner ->
                                            if (text.isEmpty()) Text(feedbackKinds[kind].placeholder, style = outfit(15), color = OC.faint)
                                            inner()
                                        },
                                    )
                                }
                                Text("${text.length} / 1000", style = outfit(12, FontWeight.Medium), color = OC.muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                            }

                            // Screenshot (the photo picker; iOS filters to screenshots)
                            val dash = OC.switchOff
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(OC.rButton)).background(OC.surface)
                                    .drawBehind {
                                        drawRoundRect(dash, style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                                            cornerRadius = CornerRadius(OC.rButton.toPx()))
                                    }
                                    .plainClickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                    .heightIn(min = 52.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(if (photo == null) Icons.Outlined.Image else Icons.Filled.CheckCircle, contentDescription = null, tint = if (photo == null) OC.ink else OC.teal, modifier = Modifier.size(18.dp))
                                Text(if (photo == null) "Attach a screenshot" else "Screenshot attached", style = outfit(14, FontWeight.Bold), color = OC.ink)
                            }

                            Box(Modifier.fillMaxWidth().glass()) {
                                ToggleRow("Include device info", isOn = deviceInfo, subtitle = "App version, Android version, phone model") { deviceInfo = it }
                            }

                            SettingsTextButton("Looking for an answer? Try the Help center ›") { app.push(Route.Help) }
                        }

                        BottomBar {
                            SettingsBusyButton(if (sending) "Sending…" else "Send", enabled = canSend, busy = sending) { send() }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - About

@Composable
fun AboutView() {
    val app = LocalApp.current
    val context = LocalContext.current
    val info = rememberAppInfo()
    var toast by remember { mutableStateOf<Toast?>(null) }

    OCPage {
        ToastHost(toast, onDismiss = { toast = null }) {
            Column(Modifier.fillMaxSize()) {
                TopBar(title = "About", backLabel = "Back to settings")
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 16.dp, bottom = 32.dp).navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIconTile(84.dp)
                        Text("O Chords", style = outfit(26, FontWeight.SemiBold, letterSpacing = (-0.8).sp), color = OC.ink)
                        Text("Version ${info.version} (${info.build})", style = mono(13, FontWeight.SemiBold), color = OC.muted)
                    }
                    Text("O Chords turns any chord sheet — handwritten, printed or screenshotted — into a clean song you can transpose, practise and share. Built for Myanmar players first, open to everyone.",
                        style = outfit(16).lineSpacing(5), color = OC.ink, modifier = Modifier.fillMaxWidth())

                    // Onez card (always on white)
                    val shape = RoundedCornerShape(OC.rCard)
                    Column(
                        Modifier.fillMaxWidth().shadow(15.dp, shape, ambientColor = Color.Black.copy(alpha = 0.07f), spotColor = Color.Black.copy(alpha = 0.07f))
                            .clip(shape).background(Color.White).border(1.dp, hex(0xECEAE6), shape)
                            .plainClickable { openUrl(context, "https://www.onezcodes.com/") }.semantics { contentDescription = "Onez Codes — Tech solutions · Software house. Opens onezcodes.com" }
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("POWERED BY", style = outfit(11, FontWeight.SemiBold, letterSpacing = 3.sp), color = hex(0x8E8A84))
                        Image(painterResource(R.drawable.onez_logo), contentDescription = null, modifier = Modifier.width(250.dp), contentScale = ContentScale.FillWidth)
                        Text("Designed and built by Onez Codes, a software house for web and mobile apps.", style = outfit(14).lineSpacing(3), color = hex(0x6B6660), textAlign = TextAlign.Center)
                        Text("onezcodes.com ›", style = outfit(14, FontWeight.Bold), color = hex(0x8F5E00))
                    }

                    GlassList {
                        SettingsRows(
                            { SettingsLinkRow("Help center") { app.push(Route.Help) } },
                            { SettingsLinkRow("Send feedback") { app.push(Route.Feedback) } },
                            { SettingsLinkRow("Terms & Conditions") { app.push(Route.Terms) } },
                            { SettingsLinkRow("Privacy Policy") { app.push(Route.Privacy) } },
                            { SettingsLinkRow("Open-source licences") { toast = Toast("No third-party packages — all native", Icons.Filled.Code) } },
                        )
                    }
                    Text("Songs and lyrics belong to their writers and rights holders. Chord sheets are shared by the O Chords community.\n© [year] Onez Codes",
                        style = outfit(12).lineSpacing(3), color = OC.faint, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// MARK: - Legal (Terms & Privacy)

@Composable
private fun SettingsLegalPage(
    title: String, heading: String, crossLink: Pair<String, Route>, notice: String, sections: List<Pair<String, String>>,
    intro: @Composable ColumnScope.() -> Unit = {},
) {
    val app = LocalApp.current
    val inSheet = LocalSettingsLegalInSheet.current
    val close = LocalSettingsLegalClose.current

    OCPage {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                TopBar(title = title, backLabel = if (inSheet) "Close" else "Back to settings", onBack = if (inSheet) close else null) {
                    if (!inSheet) {
                        SettingsTextButton(crossLink.first, style = outfit(13, FontWeight.Bold), modifier = Modifier.padding(horizontal = 8.dp)) { app.push(crossLink.second) }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(OC.border))
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 18.dp, bottom = 36.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow("Last updated [DATE]")
                    Text(heading, style = outfit(28, FontWeight.SemiBold, letterSpacing = (-1).sp), color = OC.ink)
                }
                intro()
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Draft template. ") }
                        append(notice)
                    },
                    style = outfit(13).lineSpacing(3), color = OC.ink,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(OC.rCard)).background(OC.goldTint).padding(horizontal = 14.dp, vertical = 12.dp),
                )
                sections.forEach { (h, p) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(h, style = outfit(17, FontWeight.Bold), color = OC.ink)
                        Text(p, style = outfit(15).lineSpacing(5), color = OC.bodyText)
                    }
                }
            }
        }
    }
}

private val termsSections = listOf(
    "1. Who we are" to "O Chords is made and run by Onez Codes ([legal entity name], [registered address]). By creating an account or using the app you agree to these terms.",
    "2. Your account" to "You must be at least [minimum age] to create an account. Keep your sign-in details private — you are responsible for activity on your account.",
    "3. Content you upload" to "You keep ownership of the chord sheets, arrangements and photos you upload. When you make a sheet public, you let O Chords show, store and adapt its format (for example transposing) for other users. Only upload material you have the right to share.",
    "4. Community rules" to "No spam, harassment, hate, or misleading content. Don’t copy other people’s sheets and claim them as your own. We may hide or remove content that breaks these rules.",
    "5. Copyright and takedowns" to "Lyrics and songs belong to their writers and rights holders. If you believe a sheet infringes your rights, contact [copyright contact email] and we will review and remove it where appropriate.",
    "6. O Chords Pro" to "Pro is billed through your Google Play account. It renews automatically until you cancel in Google Play at least 24 hours before renewal. Refunds follow Google Play’s policies.",
    "7. Tiers and O Points" to "O Points and tiers reward learning and contributing. They have no cash value, cannot be bought or transferred, and we may adjust how they are earned.",
    "8. Ending your account" to "You can delete your account at any time from Settings. We may suspend accounts that seriously or repeatedly break these terms.",
    "9. Our responsibility" to "We work to keep the app accurate and available, but chord sheets come from the community and may contain mistakes. The app is provided as-is to the extent allowed by law in [governing jurisdiction].",
    "10. Changes and contact" to "If we change these terms we will tell you in the app before the change takes effect. Questions: [support email].",
)

@Composable
fun TermsView() {
    SettingsLegalPage(
        title = "Terms & Conditions",
        heading = "Terms of using O Chords",
        crossLink = "Privacy" to Route.Privacy,
        notice = "Plain-language outline for Onez Codes to complete and have reviewed by a lawyer before launch.",
        sections = termsSections,
    )
}

private val privacySections = listOf(
    "What we collect" to "Account details (name, email, sign-in method), the songs, setlists and photos you save, your learning progress, and basic app usage and device information to keep the app working.",
    "Camera and photos" to "We only access images you choose to scan. Chords and English are read on your phone. When you're signed in, the photo is also sent to our server, which uses Google Cloud Vision to read Myanmar lyrics; it is not stored on our servers. Only the song you save is kept.",
    "Microphone" to "The tuner, chord check and Listen analyse audio on your phone and never record or store it. To name a song, Listen sends only an audio fingerprint — not a recording — to our song-recognition provider ([provider]).",
    "Ads" to "The free plan shows ads from Google AdMob. With your permission (our Ad choices screen) Google may use an advertising ID and app activity to choose ads; otherwise ads are not personalised. We never share your songs, lyrics or recordings with advertisers. Pro has no ads. Change this any time in Settings › Ad choices.",
    "What others can see" to "Songs are private until you choose Public. Public sheets show your display name, tier and uploads. Your email is never shown.",
    "How we use your data" to "To run your account, sync your library across devices, rank popular songs, calculate your groove and O Points, and fix problems.",
    "Who we share with" to "Service providers that host and run the app for us ([hosting provider], [analytics provider]) under contract, and authorities only when the law requires it. We do not sell personal data.",
    "Your choices" to "Make any upload private, turn off notifications, download a copy of your data, or delete your account from Settings.",
    "How long we keep it" to "Until you delete it or your account. Backups are cleared within [n] days. If you choose, public sheets can stay up without your name.",
    "Contact" to "Questions or requests: [privacy email]. Onez Codes, [registered address].",
)

private val privacyPromises = listOf(
    "No selling" to "We never sell your data",
    "Mic stays local" to "Audio is never recorded or stored",
    "You decide" to "Private by default until you share",
)

@Composable
fun PrivacyView() {
    SettingsLegalPage(
        title = "Privacy Policy",
        heading = "How O Chords handles your data",
        crossLink = "Terms" to Route.Terms,
        notice = "To be completed by Onez Codes and reviewed for the laws where O Chords is offered.",
        sections = privacySections,
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            privacyPromises.forEach { (t, s) ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().glass().padding(10.dp).semantics { contentDescription = "$t. $s" },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(t, style = outfit(13, FontWeight.Heavy), color = OC.ink)
                    Text(s, style = outfit(12).lineSpacing(1), color = OC.muted)
                }
            }
        }
    }
}

// MARK: - Delete account

private enum class DeletePhase { Form, Deleting, Done }

private val deleteOptions = listOf(
    "Keep them up, without my name" to "Other players keep the sheets they saved. Shown as “Former member”.",
    "Delete them too" to "Your sheets disappear from Explore and other people’s libraries.",
)

@Composable
fun DeleteAccountView() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var keepSheets by remember { mutableStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(DeletePhase.Form) }
    var toast by remember { mutableStateOf<Toast?>(null) }

    val ready = typed.trim().uppercase() == "DELETE"

    val losses = run {
        val p = app.profile
        val groove = p?.let { "${it.grooveDays}-day" } ?: "8-day"
        val points = NumberFormat.getIntegerInstance().format(p?.points ?: 1240)
        val tier = p?.user?.tier?.title ?: "Chord Keeper"
        listOf(
            "Your profile, library, setlists and saved songs",
            "Learning progress, your $groove groove and $points O Points",
            "Your $tier tier and badges",
        )
    }

    fun confirm() {
        if (!ready) return
        focusManager.clearFocus()
        phase = DeletePhase.Deleting
        val reason = deleteOptions[keepSheets].first
        scope.launch {
            runCatching { app.backend.deleteAccount(reason) }
            phase = DeletePhase.Done
        }
    }

    OCPage {
        ToastHost(toast, onDismiss = { toast = null }) {
            Column(Modifier.fillMaxSize().imePadding()) {
                AnimatedContent(phase == DeletePhase.Done, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "delete") { done ->
                    if (done) {
                        Column(Modifier.fillMaxSize()) {
                            TopBar(title = "Delete account", backLabel = "Close", onBack = { app.signOut() })
                            Column(
                                Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(top = 76.dp, bottom = 12.dp).navigationBarsPadding(),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text("Your account is scheduled for deletion", style = outfit(28, FontWeight.SemiBold, letterSpacing = (-1).sp), color = OC.ink, textAlign = TextAlign.Center)
                                Text("Everything will be removed within [n] days. Log back in before then if you change your mind.",
                                    style = outfit(15).lineSpacing(3), color = OC.muted, textAlign = TextAlign.Center)
                                Spacer(Modifier.weight(1f))
                                InkButton("Close") { app.signOut() }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            TopBar(title = "Delete account", backLabel = "Back to settings")
                            Column(
                                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text("This can’t be undone", style = outfit(28, FontWeight.SemiBold, letterSpacing = (-1).sp), color = OC.ink)
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(OC.rCard)).background(OC.redTint).padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text("Deleted permanently", style = outfit(14, FontWeight.Bold), color = if (isDark) hex(0xFCA5A5) else hex(0x991B1B))
                                    losses.forEach { Text("•  $it", style = outfit(14), color = OC.ink) }
                                }

                                Text(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Have Pro? ") }
                                        append("Deleting your account doesn’t cancel the Google Play subscription. Cancel it first in Google Play › Subscriptions.")
                                    },
                                    style = outfit(14).lineSpacing(3), color = OC.ink,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(OC.rCard)).background(OC.goldTint).padding(14.dp),
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Your public sheets", style = outfit(13, FontWeight.SemiBold), color = OC.ink)
                                    deleteOptions.forEachIndexed { i, (t, s) ->
                                        val on = i == keepSheets
                                        val shape = RoundedCornerShape(OC.rCard)
                                        Row(
                                            Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(shape).background(OC.surface).border(2.dp, if (on) OC.ink else OC.border, shape)
                                                .plainClickable(role = Role.RadioButton) { keepSheets = i }
                                                .semantics { contentDescription = if (on) "$t, selected" else t }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            RadioDot(on, color = OC.ink)
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                Text(t, style = outfit(15, FontWeight.Bold), color = OC.ink)
                                                Text(s, style = outfit(12), color = OC.muted)
                                            }
                                        }
                                    }
                                }

                                SettingsTextButton("Download a copy of my data first ›") { toast = Toast("We’ll email a copy of your data", Icons.Filled.Email) }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Type DELETE to confirm", style = outfit(13, FontWeight.SemiBold), color = OC.ink)
                                    val shape = RoundedCornerShape(OC.rButton)
                                    Box(
                                        Modifier.fillMaxWidth().height(50.dp).clip(shape).background(OC.surface).border(1.dp, if (ready) OC.red else OC.switchOff, shape).padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        BasicTextField(
                                            typed, { typed = it }, Modifier.fillMaxWidth().semantics { contentDescription = "Type DELETE to confirm" }, singleLine = true,
                                            textStyle = mono(16, FontWeight.SemiBold).copy(color = OC.ink, letterSpacing = 2.sp), cursorBrush = SolidColor(OC.gold),
                                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                                            decorationBox = { inner ->
                                                if (typed.isEmpty()) Text("DELETE", style = mono(16, FontWeight.SemiBold).copy(letterSpacing = 2.sp), color = OC.faint)
                                                inner()
                                            },
                                        )
                                    }
                                }
                            }

                            BottomBar {
                                SettingsBusyButton(
                                    if (phase == DeletePhase.Deleting) "Deleting…" else "Delete my account",
                                    enabled = ready, busy = phase == DeletePhase.Deleting, onColor = OC.red, onText = Color.White,
                                ) { confirm() }
                                SettingsTextButton("Keep my account", color = OC.ink, modifier = Modifier.fillMaxWidth(), style = outfit(15, FontWeight.Bold)) { app.pop() }
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Pro paywall

private enum class LegalSheet { Terms, Privacy }

private class ProPlan(val name: String, val price: String, val best: Boolean, val fine: String, val product: StoreService.Plan?)

private val proRows = listOf(
    Triple("No ads", "Ads shown", "✓"),
    Triple("Scan chord sheets", "10 a day", "Unlimited"),
    Triple("Offline songs & setlists", "—", "✓"),
    Triple("Jam rooms", "Join", "Host"),
    Triple("Mic chord check", "5 a day", "Unlimited"),
    Triple("Rest notes", "By tier", "+2 / week"),
    Triple("Advanced lessons", "Preview", "Full"),
    Triple("Print & PDF export", "—", "✓"),
)

@Composable
fun ProView() {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf(0) }
    var purchasing by remember { mutableStateOf(false) }
    var legal by remember { mutableStateOf<LegalSheet?>(null) }
    var shownLegal by remember { mutableStateOf(LegalSheet.Terms) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    var showAllFeatures by remember { mutableStateOf(false) }

    // Google Play's prices when loaded; the design's placeholders until then (and in the demo build).
    val store = app.store
    val yearlyPlan = if (app.usesDemoData) null else store.yearly
    val monthlyPlan = if (app.usesDemoData) null else store.monthly
    /** Whether the 7-day trial is still available to this player (Play's free-trial offer). */
    val trialAvailable = app.usesDemoData || (yearlyPlan?.hasTrial ?: true)
    val saving = (if (app.usesDemoData) null else store.yearlySavingPercent)?.let { "save $it%" } ?: "save [n]%"
    val yearly = yearlyPlan?.price ?: "[price]"
    val monthly = monthlyPlan?.price ?: "[price]"
    val trial = if (trialAvailable) "Free for 7 days, then " else ""
    val plans = listOf(
        ProPlan("Yearly", "$yearly / year · $saving", best = true, fine = "$trial$yearly per year.", product = yearlyPlan),
        ProPlan("Monthly", "$monthly / month", best = false, fine = "$trial$monthly per month.", product = monthlyPlan),
    )

    LaunchedEffect(Unit) { app.store.loadProducts() }
    BackHandler(enabled = legal != null) { legal = null }

    /** Buys the chosen plan through Google Play (the demo build just switches Pro on). */
    fun startTrial() {
        if (purchasing || app.isPro) return
        val product = plans[plan].product
        if (product == null && !app.usesDemoData) {
            toast = Toast("Google Play isn’t available right now. Try again in a moment.", Icons.Filled.WifiOff)
            scope.launch { app.store.loadProducts() }
            return
        }
        purchasing = true
        scope.launch {
            try {
                when (app.purchasePro(product)) {
                    StoreService.PurchaseOutcome.Purchased -> {
                        toast = Toast("Welcome to O Chords Pro", Icons.Filled.WorkspacePremium)
                        delay(900)
                        app.modal = null
                    }
                    StoreService.PurchaseOutcome.Pending -> toast = Toast("Waiting for approval — Pro starts once it’s confirmed", Icons.Filled.HourglassEmpty)
                    StoreService.PurchaseOutcome.Cancelled -> {}
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                toast = Toast(e.userMessage, Icons.Filled.Warning)
            } finally { purchasing = false }
        }
    }

    fun restore() {
        scope.launch {
            toast = Toast("Checking Google Play…", Icons.Filled.Refresh)
            app.restorePurchases()
            if (app.usesDemoData) delay(1000)
            toast = if (app.isPro) Toast("Pro is active") else Toast("No purchases to restore", Icons.Filled.Info)
        }
    }

    OCPage {
        ToastHost(toast, onDismiss = { toast = null }) {
            Column(Modifier.fillMaxSize()) {
                SettingsCloseBar(onClose = { app.modal = null }) {
                    SettingsTextButton("Restore", color = OC.muted, style = outfit(14, FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 10.dp)) { restore() }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Eyebrow("O Chords Pro")
                        Text("Play anywhere. Jam with anyone.", style = outfit(32, FontWeight.SemiBold, letterSpacing = (-1.3).sp), color = OC.ink)
                        Text("No ads, offline songs, hosting jam rooms and unlimited chord checks — for players who use O Chords every week.",
                            style = outfit(15).lineSpacing(3), color = OC.muted)
                    }

                    // Comparison
                    Column(Modifier.fillMaxWidth().glass().animateContentSize()) {
                        Row(Modifier.fillMaxWidth().height(38.dp).background(OC.softFill).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            Text("FREE", style = outfit(12, FontWeight.Bold, letterSpacing = 1.sp), color = OC.muted, modifier = Modifier.width(76.dp), textAlign = TextAlign.Center)
                            Box(Modifier.width(76.dp).height(38.dp).background(OC.gold), contentAlignment = Alignment.Center) {
                                Text("PRO", style = outfit(12, FontWeight.Bold, letterSpacing = 1.sp), color = OC.inkFixed)
                            }
                        }
                        (if (showAllFeatures) proRows else proRows.take(3)).forEach { (f, free, pro) ->
                            Hairline()
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 46.dp).height(IntrinsicSize.Min).padding(horizontal = 12.dp)
                                    .semantics { contentDescription = "$f: free, ${if (free == "—") "not included" else free}; Pro, ${if (pro == "✓") "included" else pro}" },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(f, style = outfit(13, FontWeight.SemiBold), color = OC.ink, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
                                Text(free, style = outfit(13), color = OC.muted, modifier = Modifier.width(76.dp), textAlign = TextAlign.Center)
                                Box(Modifier.width(76.dp).fillMaxHeight().background(OC.goldTint), contentAlignment = Alignment.Center) {
                                    Text(pro, style = outfit(13, FontWeight.Heavy), color = OC.ink, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        Hairline()
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).plainClickable { showAllFeatures = !showAllFeatures },
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (showAllFeatures) "Show less" else "Compare all ${proRows.size} features", style = outfit(13, FontWeight.Bold), color = OC.goldText)
                            Icon(if (showAllFeatures) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Plan picker
                    Column(Modifier.semantics { contentDescription = "Plan" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        plans.forEachIndexed { i, p ->
                            ChoiceCard(isOn = i == plan, minHeight = 64.dp, onClick = { plan = i }) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioDot(i == plan)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        Text(p.name, style = outfit(16, FontWeight.Bold), color = OC.ink)
                                        Text(p.price, style = outfit(13), color = OC.muted)
                                    }
                                    if (p.best) {
                                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(OC.inkGlass).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text("BEST VALUE", style = outfit(11, FontWeight.Heavy, letterSpacing = 0.8.sp), color = OC.gold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text("Tiers stay earned, not bought. Pro never raises your tier — learning and contributing still do.",
                        style = outfit(13).lineSpacing(3), color = OC.ink,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(OC.rCard)).background(OC.softFill).padding(horizontal = 14.dp, vertical = 12.dp))
                }

                // Bottom bar
                BottomBar {
                    val title = when {
                        app.isPro -> "You’re on Pro"
                        purchasing -> if (trialAvailable) "Starting trial…" else "Buying…"
                        else -> if (trialAvailable) "Start 7-day free trial" else "Go Pro"
                    }
                    GoldButton(
                        title, enabled = !purchasing && !app.isPro,
                        leading = if (purchasing) ({ CircularProgressIndicator(Modifier.size(18.dp), color = OC.inkFixed, strokeWidth = 2.dp) }) else null,
                    ) { startTrial() }
                    // Fine print with inline, underlined links: Google Play (manage the subscription), Terms · Privacy (opened in a sheet).
                    val link = SpanStyle(color = OC.goldText, textDecoration = TextDecoration.Underline)
                    Text(
                        buildAnnotatedString {
                            append("${plans[plan].fine} Cancel anytime in ")
                            withLink(LinkAnnotation.Clickable("play", TextLinkStyles(link)) { openSubscriptions(context) }) { append("Google Play") }
                            append(" at least 24 hours before renewal. ")
                            withLink(LinkAnnotation.Clickable("terms", TextLinkStyles(link)) { shownLegal = LegalSheet.Terms; legal = LegalSheet.Terms }) { append("Terms") }
                            append(" · ")
                            withLink(LinkAnnotation.Clickable("privacy", TextLinkStyles(link)) { shownLegal = LegalSheet.Privacy; legal = LegalSheet.Privacy }) { append("Privacy") }
                        },
                        style = outfit(11).lineSpacing(2), color = OC.ink.copy(alpha = 0.8f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Legal sheet
        AnimatedVisibility(legal != null, enter = slideInVertically(tween(300)) { it } + fadeIn(tween(200)), exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(OC.background)) {
                CompositionLocalProvider(LocalSettingsLegalInSheet provides true, LocalSettingsLegalClose provides { legal = null }) {
                    when (shownLegal) {
                        LegalSheet.Terms -> TermsView()
                        LegalSheet.Privacy -> PrivacyView()
                    }
                }
            }
        }
    }
}

// MARK: - Ad choices

@Composable
fun AdChoicesView() {
    val app = LocalApp.current
    val context = LocalContext.current
    var rewardAds by remember { mutableStateOf(true) }
    var toast by remember { mutableStateOf<Toast?>(null) }

    OCPage {
        ToastHost(toast, onDismiss = { toast = null }) {
            Column(Modifier.fillMaxSize()) {
                TopBar(title = "Ad choices", backLabel = "Back to settings")
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 32.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Go Pro card
                    Row(Modifier.fillMaxWidth().inkGlass().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (app.isPro) "Ads are off" else "Remove all ads", style = outfit(17, FontWeight.Bold), color = OC.paper)
                            Text(if (app.isPro) "You’re on O Chords Pro — no ads anywhere." else "O Chords Pro has no ads, plus offline songs and hosting jam rooms.",
                                style = outfit(13).lineSpacing(2), color = hex(0xBDB9B3))
                        }
                        if (!app.isPro) GoldButton("Go Pro", height = 44.dp, fullWidth = false) { app.modal = Modal.Pro }
                    }

                    GlassList {
                        SettingsRows(
                            {
                                ToggleRow("Ads that fit me", isOn = app.personalisedAds, modifier = Modifier.heightIn(min = 64.dp),
                                    subtitle = "Uses app activity to choose ads. Off = general ads only.") { app.personalisedAds = it }
                            },
                            {
                                ToggleRow("Offer reward ads", isOn = rewardAds, modifier = Modifier.heightIn(min = 64.dp),
                                    subtitle = "Watch an optional ad for an extra scan or to restring in a lesson.") { rewardAds = it }
                            },
                        )
                    }

                    GlassList {
                        SettingsRows(
                            { SettingsLinkRow("Manage ad partners") { toast = Toast("Google AdMob is our only ad partner", Icons.Filled.Info) } },
                            // iOS's tracking permission row: on Android the equivalent is Google's ad settings for the account.
                            { SettingsLinkRow("Google ad settings", value = if (app.personalisedAds) "Allowed" else "Off") { openUrl(context, "https://adssettings.google.com/") } },
                            { SettingsLinkRow("How ads use data") { app.push(Route.Privacy) } },
                        )
                    }

                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(OC.tealTint).padding(14.dp)
                            .semantics { contentDescription = "Never ads here: Song view, Stage mode, Warm up, Tuner, Listen, Jam rooms, during a lesson" },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("NEVER ADS HERE", style = outfit(13, FontWeight.Heavy), color = OC.tealText)
                        Text("Song view · Stage mode · Warm up · Tuner · Listen · Jam rooms · during a lesson", style = outfit(13).lineSpacing(3), color = OC.ink)
                    }
                }
            }
        }
    }
}
