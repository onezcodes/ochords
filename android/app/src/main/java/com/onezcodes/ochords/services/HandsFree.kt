package com.onezcodes.ochords.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Size
import android.view.InputDevice
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

// Hands-free page turns in Stage: a Bluetooth pedal (it's a keyboard to Android), a nod at the front camera, or a
// spoken word. Each watcher runs only while Stage (or the Hands-free screen) is open. Start and stop on the main
// thread; callbacks arrive on the main thread.

/** The hands-free modes Stage offers (the choice is remembered by the screen). */
enum class HandsFreeMode(val key: String, val title: String, val subtitle: String) {
    Pedal("pedal", "Foot pedal", "Bluetooth page-turner"),
    Nod("nod", "Head nod", "Front camera"),
    Auto("auto", "Auto-scroll", "Synced to tempo"),
    Voice("voice", "Voice", "“Next”, “chorus”…");

    companion object {
        fun from(key: String?): HandsFreeMode = entries.firstOrNull { it.key == key } ?: Pedal
    }
}

// MARK: - Foot pedal

/** What a page-turn pedal is: Android sees it as an external keyboard sending arrow / page keys. */
object PedalStatus {
    /** The connected pedal's (external keyboard's) name, or null when none is connected. */
    fun connectedName(): String? {
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            if (device.isVirtual || device.sources and InputDevice.SOURCE_KEYBOARD != InputDevice.SOURCE_KEYBOARD) continue
            if (device.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC && device.keyboardType != InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC) continue
            if (!device.isExternal) continue
            return device.name.takeIf { it.isNotBlank() } ?: "Pedal"
        }
        return null
    }
}

/**
 * Turns a pedal's key events into page turns: right / down / page down = next, left / up / page up = back, holding
 * the right pedal (0.6 s) starts or stops the scroll, space toggles it too. Keep one per screen and feed it every
 * `KeyEvent` (from Compose: `Modifier.onKeyEvent { pedal.handle(it.nativeKeyEvent) != null || pedal.handles(it.nativeKeyEvent.keyCode) }`).
 */
class PedalKeys {
    enum class Action { Next, Back, ToggleScroll }

    /** When the right pedal went down, to tell a hold (start / stop) from a press (next). */
    private var pedalDown: Long? = null

    /** The action this event completes, or null. */
    fun handle(event: KeyEvent): Action? = handle(event.keyCode, event.action, event.repeatCount, event.eventTime)

    fun handle(keyCode: Int, action: Int, repeatCount: Int, eventTime: Long): Action? {
        if (keyCode in nextKeys) {
            when (action) {
                KeyEvent.ACTION_DOWN -> {
                    if (repeatCount == 0) { pedalDown = eventTime; return null }
                    // Held: start or stop the scroll, once per hold.
                    val down = pedalDown ?: return null
                    if (eventTime - down > holdMillis) { pedalDown = null; return Action.ToggleScroll }
                    return null
                }
                KeyEvent.ACTION_UP -> {
                    val pressed = pedalDown != null
                    pedalDown = null
                    return if (pressed) Action.Next else null
                }
                else -> return null
            }
        }
        if (keyCode in backKeys) return if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) Action.Back else null
        if (keyCode == KeyEvent.KEYCODE_SPACE) return if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) Action.ToggleScroll else null
        return null
    }

    /** Whether the key is a pedal key (so the event should be consumed). */
    fun handles(keyCode: Int): Boolean = keyCode in nextKeys || keyCode in backKeys || keyCode == KeyEvent.KEYCODE_SPACE

    companion object {
        const val holdMillis = 600L
        val nextKeys = setOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN)
        val backKeys = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP)
    }
}

// MARK: - Head nod

/**
 * Tells a nod down and a tilt up from the face's pitch: a quick turn away from where the head usually rests, and
 * back. Pure logic, so it can be tested on recorded angles.
 */
class NodGesture {
    enum class Move { Down, Up }

    /**
     * Which way of turning (+1 or −1 pitch) is a nod down — learnt from the player's first nod in setup, as the
     * camera's sign depends on how the phone is held.
     */
    var downSign = 1.0

    companion object {
        /** How far (radians, ≈ 13°) the head must turn, and how soon it must come back (s). */
        const val threshold = 0.22
        const val returnWithin = 1.0
        /** No second move this soon after one (s). */
        const val refractory = 0.6
    }

    private var rest: Double? = null
    private var away: Pair<Double, Double>? = null // sign, since
    private var lastMove = Double.NEGATIVE_INFINITY

    /** The face's pitch (radians) at `time` (s); returns a move when one completes (head back near rest). */
    fun hear(pitch: Double, time: Double): Move? {
        val rest = this.rest ?: run { this.rest = pitch; return null }
        val offset = pitch - rest
        val a = away
        if (a != null) {
            if (abs(offset) < threshold / 2) {
                away = null
                if (time - a.second > returnWithin || time - lastMove < refractory) return null
                lastMove = time
                return if (a.first == downSign) Move.Down else Move.Up
            }
            // Held away (looking at the floor, not nodding): that's the new resting place.
            if (time - a.second > returnWithin) { away = null; this.rest = pitch }
            return null
        }
        if (abs(offset) >= threshold) {
            away = (if (offset > 0) 1.0 else -1.0) to time
        } else {
            // Drift slowly with the player's posture.
            this.rest = rest + (pitch - rest) * 0.05
        }
        return null
    }

    /** The sign of the move just made, for learning [downSign] ("nod down once"). */
    fun learnDown(pitch: Double, time: Double): Double? {
        val rest = this.rest ?: run { this.rest = pitch; return null }
        val offset = pitch - rest
        if (abs(offset) >= threshold) return if (offset > 0) 1.0 else -1.0
        this.rest = rest + (pitch - rest) * 0.05
        return null
    }
}

/**
 * The front camera, watching the player's head for nods: CameraX frames at ≈ 15 fps, ML Kit face detection reading
 * the head's pitch (Euler X). Frames are analysed on the phone and never kept. Needs the CAMERA permission (the
 * screen asks); bound to `lifecycleOwner`, so it stops with the screen. Call [close] when done with it.
 */
class HeadNodWatcher(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    downSign: Double,
) {
    var onMove: ((NodGesture.Move) -> Unit)? = null
    var onFace: ((Boolean) -> Unit)? = null
    /** Set while learning which way is down; called once with the sign. */
    var onLearnt: ((Double) -> Unit)? = null

    /** A face is in view (null until the camera has looked), for Compose. */
    var faceFound: Boolean? by mutableStateOf(null)
        private set

    companion object {
        fun hasCamera(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        /** 15 frames a second is plenty for a nod, and easy on the battery. */
        private const val frameNanos = 1_000_000_000L / 15
    }

    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "ochords.nod") }
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
    )
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var wanted = false
    /** Only touched on `executor`. */
    private var gesture = NodGesture().apply { this.downSign = downSign }
    private var learning = false
    private var sawFace: Boolean? = null
    private var lastFrameAt = 0L

    /** Main thread. */
    fun start() {
        wanted = true
        val p = provider
        if (p != null) { bind(p); return }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val got = try { future.get() } catch (_: Exception) { null } ?: return@addListener
            provider = got
            if (wanted) bind(got)
        }, ContextCompat.getMainExecutor(context))
    }

    /** Main thread. */
    fun stop() {
        wanted = false
        analysis?.let { provider?.unbind(it) }
    }

    /** The next clear turn of the head is a nod down. */
    fun learnDown() { executor.execute { learning = true; gesture = NodGesture() } }

    /** Stops and frees the camera executor and detector. */
    fun close() {
        stop()
        analysis?.clearAnalyzer()
        executor.shutdown()
        detector.close()
    }

    private fun bind(p: ProcessCameraProvider) {
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) return
        val a = analysis ?: ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor) { image -> analyse(image) }; analysis = it }
        try {
            p.unbind(a)
            p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, a)
        } catch (_: Exception) {
            // No front camera or it's in use: the screen shows "looking for your face" until it's free.
        }
    }

    /** On `executor`, for each camera frame. */
    @OptIn(ExperimentalGetImage::class)
    private fun analyse(image: ImageProxy) {
        val now = System.nanoTime()
        val media = image.image
        if (media == null || now - lastFrameAt < frameNanos) { image.close(); return }
        lastFrameAt = now
        val time = image.imageInfo.timestamp / 1e9
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener(executor) { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() }
                val found = face != null
                if (found != sawFace) {
                    sawFace = found
                    main.post { faceFound = found; onFace?.invoke(found) }
                }
                if (face == null) return@addOnSuccessListener
                val pitch = Math.toRadians(face.headEulerAngleX.toDouble())
                if (learning) {
                    gesture.learnDown(pitch, time)?.let { sign ->
                        learning = false
                        gesture = NodGesture().apply { downSign = sign }
                        main.post { onLearnt?.invoke(sign) }
                    }
                    return@addOnSuccessListener
                }
                gesture.hear(pitch, time)?.let { move -> main.post { onMove?.invoke(move) } }
            }
            .addOnCompleteListener(executor) { image.close() }
    }
}

// MARK: - Voice

/**
 * Spoken page turns: "next", "back", "chorus", "stop", "play". English words, understood on the phone when it can
 * (`EXTRA_PREFER_OFFLINE`). Uses Android's [SpeechRecognizer], which takes the microphone itself — so it's off while
 * Follow me listens. Start and stop on the main thread.
 */
class VoiceCommands(private val context: Context) {
    enum class Command(val word: String) { Next("next"), Back("back"), Chorus("chorus"), Stop("stop"), Play("play") }

    var onCommand: ((Command) -> Unit)? = null
    /** The word just understood, capitalised ("Next"), to show that it works. */
    var onHeard: ((String) -> Unit)? = null

    var isRunning: Boolean by mutableStateOf(false)
        private set

    private var recognizer: SpeechRecognizer? = null
    private val main = Handler(Looper.getMainLooper())
    /** Words of the current result already acted on. */
    private var handled = 0

    companion object {
        /** Other words people say for the same thing. */
        private val words: Map<String, Command> = mapOf(
            "next" to Command.Next, "forward" to Command.Next, "down" to Command.Next,
            "back" to Command.Back, "previous" to Command.Back, "up" to Command.Back,
            "chorus" to Command.Chorus,
            "stop" to Command.Stop, "pause" to Command.Stop,
            "play" to Command.Play, "start" to Command.Play, "go" to Command.Play,
        )

        fun available(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
        fun hasPermission(context: Context): Boolean = MicInput.hasPermission(context)
    }

    /** Throws [MicInput.Failure] without permission or a recogniser. */
    fun start() {
        if (isRunning) return
        if (!hasPermission(context)) throw MicInput.Failure(MicInput.Failure.Kind.NoMicrophoneAccess)
        if (!available(context)) throw MicInput.Failure(MicInput.Failure.Kind.AudioUnavailable)
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(listener)
        recognizer = r
        isRunning = true
        listen()
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        main.removeCallbacksAndMessages(null)
        recognizer?.let { runCatching { it.cancel() }; it.destroy() }
        recognizer = null
    }

    /** One recognition session; a new one starts when it ends (they end after a pause in speech). */
    private fun listen() {
        val r = recognizer ?: return
        if (!isRunning) return
        handled = 0
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { r.startListening(intent) }
    }

    private fun received(results: Bundle?, ended: Boolean, retryAfter: Long = 0) {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
        if (text != null) {
            val heard = text.lowercase().split(Regex("\\s+")).map { it.trim { c -> !c.isLetterOrDigit() } }.filter { it.isNotEmpty() }
            for (word in heard.drop(handled)) {
                val command = words[word] ?: continue
                onHeard?.invoke(command.word.replaceFirstChar { it.uppercase() })
                onCommand?.invoke(command)
            }
            handled = maxOf(handled, heard.size)
        }
        if (ended && isRunning) {
            if (retryAfter > 0) main.postDelayed({ listen() }, retryAfter) else listen()
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) = received(partialResults, ended = false)
        override fun onResults(results: Bundle?) = received(results, ended = true)
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stop()
                // Nothing said for a while, or a busy service: listen again shortly.
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_NETWORK -> received(null, ended = true, retryAfter = 800)
                else -> received(null, ended = true, retryAfter = 200)
            }
        }
    }
}
