package com.onezcodes.ochords.features.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.WarmBackground
import com.onezcodes.ochords.design.glass
import com.onezcodes.ochords.design.mono
import com.onezcodes.ochords.design.myanmar
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.design.plainClickable
import com.onezcodes.ochords.design.Heavy
import com.onezcodes.ochords.model.ChordLine
import com.onezcodes.ochords.services.ChordSheetParser
import com.onezcodes.ochords.services.Zawgyi

/**
 * Edits one line of a song. By default just the words: the chords stay with their words
 * (`ChordSheetParser.reword`), or — for a scanned line whose lyrics couldn't be read — are lined up over the words
 * typed (`ChordSheetParser.placeChords`). "Edit chords too" switches to the line as text with chords in [brackets].
 */
@Composable
fun LyricLineEditor(
    sectionLabel: String,
    line: String,
    /** The scan couldn't read this line's lyrics (only its chords are known). */
    missingLyrics: Boolean = false,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(line) { mutableStateOf(if (missingLyrics) "" else ChordSheetParser.words(line)) }
    var rawMode by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val chords = remember(line) { ChordLine.chords(line) }

    /** What will be saved. Text typed on a Zawgyi keyboard is converted to Unicode first. */
    val preview: String = run {
        val t = Zawgyi.normalised(text.trim())
        when {
            rawMode -> t
            missingLyrics -> if (t.isEmpty()) line else ChordSheetParser.placeChords(line, t)
            else -> ChordSheetParser.reword(line, t)
        }
    }

    Box(modifier.fillMaxWidth()) {
        WarmBackground(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Cancel", style = outfit(16, FontWeight.SemiBold), color = OC.muted,
                    modifier = Modifier.heightIn(min = 44.dp).plainClickable(onClick = onCancel).wrapContentCenter())
                Spacer(Modifier.weight(1f))
                Text(sectionLabel, style = outfit(16, FontWeight.Bold), color = OC.ink)
                Spacer(Modifier.weight(1f))
                Text("Done", style = outfit(16, FontWeight.Bold), color = OC.goldText,
                    modifier = Modifier.heightIn(min = 44.dp).plainClickable { onSave(preview) }.wrapContentCenter())
            }

            if (rawMode) {
                Text("Put chords in [brackets] right before the word, e.g. [Am]words", style = outfit(13), color = OC.muted)
            } else {
                Text(if (missingLyrics) "Type the lyrics for this line" else "Edit the words — the chords stay with them",
                    style = outfit(13, FontWeight.Bold), color = OC.muted)
                if (chords.isNotEmpty()) {
                    Row(
                        Modifier.semantics { contentDescription = "Chords: ${chords.joinToString(", ")}" },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        chords.forEach { c ->
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(OC.goldTint).padding(horizontal = 7.dp, vertical = 4.dp)) {
                                Text(c, style = mono(13), color = OC.goldText)
                            }
                        }
                    }
                }
            }

            val style = (if (rawMode) mono(15) else myanmar(18)).copy(color = OC.ink)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = style,
                cursorBrush = SolidColor(OC.gold),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = !rawMode),
                modifier = Modifier.fillMaxWidth().glass(14.dp).padding(12.dp).focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text(if (rawMode) "Line" else "Lyrics", style = style, color = OC.faint)
                        inner()
                    }
                },
            )

            if (!rawMode && text.isNotBlank() && chords.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("PREVIEW", style = outfit(11, FontWeight.Heavy, 1.sp), color = OC.muted)
                    WrapFlow(spacing = 5.dp, lineSpacing = 4.dp) {
                        ChordLine.parse(preview).forEach { seg ->
                            Column {
                                Text(seg.chord ?: " ", style = mono(13), color = OC.goldText)
                                Text(seg.text.ifEmpty { " " }, style = myanmar(15), color = OC.ink)
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (rawMode) "Just edit the words" else "Edit chords too",
                    style = outfit(14, FontWeight.Bold), color = OC.goldText,
                    modifier = Modifier.heightIn(min = 44.dp).plainClickable {
                        val current = preview
                        rawMode = !rawMode
                        text = if (rawMode) current else (if (missingLyrics) "" else ChordSheetParser.words(current))
                    }.wrapContentCenter(),
                )
                Spacer(Modifier.weight(1f))
                Text("Delete line", style = outfit(14, FontWeight.Bold), color = OC.red,
                    modifier = Modifier.heightIn(min = 44.dp).plainClickable(onClick = onDelete).wrapContentCenter())
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** The editor as a bottom sheet (Review after a scan; the song page's lyric editing). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricLineEditorSheet(
    sectionLabel: String,
    line: String,
    missingLyrics: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = OC.background,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        LyricLineEditor(
            sectionLabel = sectionLabel, line = line, missingLyrics = missingLyrics,
            modifier = Modifier.heightIn(min = 380.dp).navigationBarsPadding().imePadding(),
            onCancel = onDismiss, onSave = onSave, onDelete = onDelete,
        )
    }
}

/** Wrapping flow layout for chips and chord-over-lyric segments. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrapFlow(modifier: Modifier = Modifier, spacing: Dp = 6.dp, lineSpacing: Dp = 6.dp, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(lineSpacing),
        content = content,
    )
}

/** Centres a text button's label inside its 44dp tap height. */
private fun Modifier.wrapContentCenter(): Modifier = this.wrapContentHeight(Alignment.CenterVertically)
