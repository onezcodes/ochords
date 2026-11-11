package com.onezcodes.ochords.features.importsheet

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onezcodes.ochords.app.LocalApp
import com.onezcodes.ochords.app.Modal
import com.onezcodes.ochords.app.PermissionKind
import com.onezcodes.ochords.app.RewardKind
import com.onezcodes.ochords.app.Route
import com.onezcodes.ochords.design.Eyebrow
import com.onezcodes.ochords.design.GlassButton
import com.onezcodes.ochords.design.GoldButton
import com.onezcodes.ochords.design.Heavy
import com.onezcodes.ochords.design.InkButton
import com.onezcodes.ochords.design.NightBackground
import com.onezcodes.ochords.design.OC
import com.onezcodes.ochords.design.OCPage
import com.onezcodes.ochords.design.OCSwitch
import com.onezcodes.ochords.design.PillTone
import com.onezcodes.ochords.design.PlaceholderBox
import com.onezcodes.ochords.design.SectionTag
import com.onezcodes.ochords.design.Segmented
import com.onezcodes.ochords.design.StatusPill
import com.onezcodes.ochords.design.Toast
import com.onezcodes.ochords.design.ToastHost
import com.onezcodes.ochords.design.TopBar
import com.onezcodes.ochords.design.WarmBackground
import com.onezcodes.ochords.design.glass
import com.onezcodes.ochords.design.hex
import com.onezcodes.ochords.design.mono
import com.onezcodes.ochords.design.myanmar
import com.onezcodes.ochords.design.nightGlass
import com.onezcodes.ochords.design.outfit
import com.onezcodes.ochords.design.plainClickable
import com.onezcodes.ochords.design.sectionColors
import com.onezcodes.ochords.features.shared.LyricLineEditorSheet
import com.onezcodes.ochords.features.shared.WrapFlow
import com.onezcodes.ochords.model.ChordLine
import com.onezcodes.ochords.model.SectionKind
import com.onezcodes.ochords.model.Song
import com.onezcodes.ochords.model.SongSection
import com.onezcodes.ochords.services.AdService
import com.onezcodes.ochords.services.ChordSheetParser
import com.onezcodes.ochords.services.OnDeviceScanService
import com.onezcodes.ochords.services.ScanImage
import com.onezcodes.ochords.services.ScanLineRef
import com.onezcodes.ochords.services.ScanPDF
import com.onezcodes.ochords.services.ScanRegion
import com.onezcodes.ochords.services.ScanResult
import com.onezcodes.ochords.services.ScanSource
import com.onezcodes.ochords.services.ScanStage
import com.onezcodes.ochords.services.Zawgyi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Import group: 2 Scan & read sheet · 36b Scan failed · 2b/2c Zawgyi · 3 Review import ·
// 40a–d Listen · 49c–e Rewarded ads.

// MARK: - Shared helpers (group-private)

/** One-time permission primers for this session (camera → first scan, mic → first listen). */
private object ImportSession {
    var askedCamera = false
    var askedMic = false
}

/** Fixed colours used on the always-dark Import / Listen / ad screens. */
private object ImportNight {
    val text = OC.nightText
    val muted = OC.nightMuted
    val soft = hex(0xBDB9B3)
    val teal = hex(0x5EEAD4)
    val tealBg = hex(0x14B8A6, 0.18f)
    val goldText = hex(0xFFC940)
    val red = hex(0xFCA5A5)
    val redBg = hex(0xF87171, 0.08f)
    val blue = hex(0x93C5FD)
    val blueBg = hex(0x3B82F6, 0.18f)
    val outline = hex(0x4A4743)

    fun confidenceColor(c: Int): Color = if (c >= 80) teal else if (c >= 70) goldText else red
}

private val warningIcon = Icons.Outlined.WarningAmber

/** A drawn stand-in for the photographed chord sheet (paper with chord and lyric lines). */
@Composable
private fun ImportSheetArt(modifier: Modifier = Modifier) {
    Canvas(modifier.background(hex(0xFBF8F1))) {
        val s = size.width / 300f
        val dark = hex(0x28221A, 0.55f)
        val light = hex(0x28221A, 0.3f)
        val gold = hex(0x8F5E00, 0.55f)
        val left = 18 * s
        val inner = size.width - 36 * s
        fun bar(x: Float, y: Float, w: Float, h: Float, color: Color) = drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(h / 2))
        var y = 16 * s
        bar(left, y, inner * 0.45f, 5 * s, dark); y += 5 * s
        for (block in 0 until 6) {
            y += 14 * s
            bar(left, y, inner * 0.18f, 4 * s, dark); y += 4 * s + 5 * s
            val lines = if (block % 3 == 0) 1 else 3
            for (l in 0 until lines) {
                var x = left
                repeat(4) { bar(x, y, 4 * s * 3.5f, 4 * s, gold); x += 4 * s * 3.5f + 14 * s }
                y += 4 * s + 5 * s
                bar(left, y, inner * (if (l == 2) 0.55f else 0.85f), 3 * s, light); y += 3 * s + 5 * s
            }
        }
    }
}

// MARK: - Camera

/** Back camera for sheet photos: live preview, photo capture, torch (CameraX). */
private class ImportCamera {
    enum class Status { Waiting, Running, Unavailable, Denied }

    var status by mutableStateOf(Status.Waiting)
    var hasTorch by mutableStateOf(false)
        private set
    var torchOn by mutableStateOf(false)
        private set

    val preview: Preview = Preview.Builder().build()
    private val output = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var capturing = false

    /** Starts the preview if access is granted. Waits (no prompt) while access is undecided. */
    fun start(context: Context, owner: LifecycleOwner) {
        if (!hasCameraPermission(context)) return
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = runCatching { future.get() }.getOrNull()
            if (p == null) { status = Status.Unavailable; return@addListener }
            provider = p
            val hasBack = runCatching { p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
            if (!hasBack) { status = Status.Unavailable; return@addListener }
            val bound = runCatching {
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, output)
            }.getOrNull()
            if (bound == null) { status = Status.Unavailable; return@addListener }
            camera = bound
            hasTorch = bound.cameraInfo.hasFlashUnit()
            status = Status.Running
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        setTorch(false)
        provider?.unbindAll()
        camera = null
        if (status == Status.Running) status = Status.Waiting
    }

    fun setTorch(on: Boolean) {
        torchOn = on && hasTorch
        camera?.cameraControl?.enableTorch(torchOn)
    }

    /** JPEG data of a full-resolution photo (upright), or null if the camera isn't running. */
    suspend fun capture(context: Context): ByteArray? {
        if (status != Status.Running || capturing) return null
        capturing = true
        try {
            val shot = suspendCancellableCoroutine<Pair<ByteArray, Int>?> { cont ->
                output.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val shot = image.use { proxy ->
                            val rotation = proxy.imageInfo.rotationDegrees
                            proxy.planes.firstOrNull()?.buffer?.let { b -> ByteArray(b.remaining()).also { b.get(it) } to rotation }
                        }
                        cont.resume(shot)
                    }
                    override fun onError(exception: ImageCaptureException) { cont.resume(null) }
                })
            } ?: return null
            return withContext(Dispatchers.Default) { uprightJpeg(shot.first, shot.second) }
        } finally {
            capturing = false
        }
    }

    companion object {
        fun hasCameraPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        /** The captured JPEG the right way up: CameraX usually writes the rotation into its EXIF; if not, turn the pixels. */
        private fun uprightJpeg(bytes: ByteArray, rotation: Int): ByteArray {
            if (rotation == 0) return bytes
            val exif = runCatching { ExifInterface(bytes.inputStream()).rotationDegrees }.getOrDefault(0)
            if (exif != 0) return bytes
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            val turned = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
            val out = ByteArrayOutputStream()
            return if (turned.compress(Bitmap.CompressFormat.JPEG, 92, out)) out.toByteArray() else bytes
        }
    }
}

// MARK: - 2 · Scan & read sheet (+ 36b Scan failed)

private enum class ImportPhase { Viewfinder, Crop, Reading, Done, Failed }

@Composable
fun ImportView(picked: ScanSource? = null) {
    val app = LocalApp.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val camera = remember { ImportCamera() }
    var phase by remember { mutableStateOf(ImportPhase.Viewfinder) }
    var stage by remember { mutableStateOf<ScanStage?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var source by remember { mutableStateOf<ScanSource?>(null) }
    var pickedImage by remember { mutableStateOf<Bitmap?>(null) }
    var cropToPage by remember { mutableStateOf(true) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    var startedPicked by remember { mutableStateOf(false) }
    /** A picked photo waiting to be cropped (upright, at reading size). */
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    /** Whether this screen has already put up the system camera prompt. */
    var askedSystem by remember { mutableStateOf(false) }

    // MARK: Actions

    fun run(src: ScanSource) {
        if (!app.isPro && app.freeScansLeft <= 0) { app.modal = Modal.Reward(RewardKind.Scan); return }
        scanJob?.cancel()
        source = src
        phase = ImportPhase.Reading; progress = 0.04f; result = null; stage = null
        val scanner = app.scanner
        if (scanner is OnDeviceScanService) scanner.cropToPage = cropToPage
        scanJob = scope.launch {
            try {
                val r = scanner.scan(src) { s ->
                    stage = s
                    progress = floatArrayOf(0.15f, 0.55f, 0.8f, 0.92f)[s.ordinal]
                }
                ensureActive()
                if (!app.isPro) app.freeScansLeft = max(0, app.freeScansLeft - 1)
                progress = 1f; result = r; phase = ImportPhase.Done
            } catch (_: CancellationException) {
            } catch (_: Exception) {
                phase = ImportPhase.Failed
            }
        }
    }

    /** Reads a picked photo or PDF (showing it behind the progress). A photo is cropped first. */
    fun read(src: ScanSource) {
        when (src) {
            is ScanSource.Image -> scope.launch {
                val upright = withContext(Dispatchers.Default) { ScanImage.prepare(src.data) }
                if (upright == null) { toast = Toast("Couldn’t open that photo", warningIcon); return@launch }
                cropping = upright
                phase = ImportPhase.Crop
            }
            is ScanSource.Pdf -> {
                run(src)
                scope.launch {
                    pickedImage = withContext(Dispatchers.IO) { runCatching { ScanPDF.render(context, src.data, maxSide = 1200, pages = 1).firstOrNull() }.getOrNull() }
                }
            }
        }
    }

    /** The sheet as cropped: read it as it is (the automatic page crop would trim it again). */
    fun cropped(sheet: Bitmap) {
        cropping = null
        cropToPage = false
        pickedImage = sheet
        scope.launch {
            val data = withContext(Dispatchers.Default) { ScanImage.jpeg(sheet, maxSide = max(sheet.width, sheet.height), quality = 92) } ?: return@launch
            run(ScanSource.Image(data))
        }
    }

    /** Back to where the photo came from: the Library (picked there) or the camera. */
    fun cancelCrop() {
        cropping = null
        if (picked != null) app.pop() else phase = ImportPhase.Viewfinder
    }

    fun retake() {
        scanJob?.cancel()
        phase = ImportPhase.Viewfinder; progress = 0f; stage = null; result = null; pickedImage = null; source = null
    }

    fun toggleCrop() {
        val src = source ?: return
        cropToPage = !cropToPage
        toast = Toast(if (cropToPage) "Cropped to the page edges" else "Using the whole photo", Icons.Outlined.Crop)
        // Re-reading the same photo doesn't use up another free scan.
        if (phase == ImportPhase.Done && !app.isPro) app.freeScansLeft += 1
        run(src)
    }

    fun continueToReview() {
        val r = result ?: return
        if (r.detectedZawgyi && !app.alwaysConvertZawgyi) app.replaceTop(Route.Zawgyi(r)) else app.replaceTop(Route.Review(r))
    }

    fun typeItIn() {
        val blank = Song(id = UUID.randomUUID().toString(), title = "", artist = "", key = "C", sections = listOf(
            SongSection(kind = SectionKind.Verse, label = "VERSE 1", lines = listOf("[C]")),
        ))
        app.push(Route.Review(ScanResult(song = blank, detectedZawgyi = false, lowConfidence = emptyList())))
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val data = withContext(Dispatchers.IO) { readBytes(context, uri) }
            if (data == null) toast = Toast("Couldn’t open that photo", warningIcon) else read(ScanSource.Image(data))
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val data = withContext(Dispatchers.IO) { readBytes(context, uri) }
            if (data == null) { toast = Toast("Couldn’t open that file", warningIcon); return@launch }
            val isPdf = context.contentResolver.getType(uri) == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")
            read(if (isPdf) ScanSource.Pdf(data) else ScanSource.Image(data))
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.start(context, lifecycleOwner) else camera.status = ImportCamera.Status.Denied
    }

    fun startCamera() {
        when {
            ImportCamera.hasCameraPermission(context) -> camera.start(context, lifecycleOwner)
            !askedSystem -> { askedSystem = true; permission.launch(Manifest.permission.CAMERA) }
            else -> camera.status = ImportCamera.Status.Denied
        }
    }

    fun shutter() {
        if (camera.status != ImportCamera.Status.Running) { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)); return }
        scope.launch {
            val data = camera.capture(context)
            if (data == null) { toast = Toast("Couldn’t take the photo", warningIcon); return@launch }
            pickedImage = withContext(Dispatchers.Default) { ScanImage.prepare(data, maxSide = 1600) }
            run(ScanSource.Image(data))
        }
    }

    fun openSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    LaunchedEffect(Unit) {
        if (picked != null && !startedPicked) {
            startedPicked = true
            read(picked)
            return@LaunchedEffect
        }
        if (!ImportCamera.hasCameraPermission(context) && !ImportSession.askedCamera) {
            ImportSession.askedCamera = true
            app.modal = Modal.Permission(PermissionKind.Camera)
        } else {
            startCamera()
        }
    }
    // The camera primer is a full-screen modal; start once it closes (access granted or not).
    val modal = app.modal
    var modalWasUp by remember { mutableStateOf(false) }
    LaunchedEffect(modal) {
        if (modal != null) modalWasUp = true
        else if (modalWasUp) { modalWasUp = false; if (phase == ImportPhase.Viewfinder) startCamera() }
    }
    var lastPhase by remember { mutableStateOf(phase) }
    LaunchedEffect(phase) {
        if (phase == lastPhase) return@LaunchedEffect
        lastPhase = phase
        if (phase == ImportPhase.Viewfinder) startCamera() else camera.stop()
    }
    DisposableEffect(Unit) { onDispose { scanJob?.cancel(); camera.stop() } }

    val canToggleCrop = phase != ImportPhase.Viewfinder && source is ScanSource.Image
    val screen = when { phase == ImportPhase.Failed -> 0; phase == ImportPhase.Crop && cropping != null -> 1; else -> 2 }

    ToastHost(toast, onDismiss = { toast = null }, bottomPadding = 40.dp) {
        Crossfade(targetState = screen, label = "import") { which ->
            Box(Modifier.fillMaxSize()) {
                if (which == 0) WarmBackground() else NightBackground()
                Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    when (which) {
                        0 -> ImportFailedView(
                            photo = pickedImage,
                            onClose = { app.pop() },
                            onRetake = ::retake,
                            onTypeIn = ::typeItIn,
                            onChooseAnother = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        )
                        1 -> cropping?.let { ImportCropView(image = it, onCancel = ::cancelCrop, onDone = ::cropped) }
                        else -> Column(Modifier.fillMaxSize()) {
                            // Dark camera / reading screen
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Cancel", style = outfit(16, FontWeight.SemiBold), color = OC.gold,
                                    modifier = Modifier.heightIn(min = 44.dp).plainClickable { scanJob?.cancel(); app.pop() }
                                        .padding(horizontal = 10.dp).wrapContentHeight(Alignment.CenterVertically))
                                Spacer(Modifier.weight(1f))
                                Text(if (phase == ImportPhase.Viewfinder) "Scan sheet" else "Reading sheet", style = outfit(16, FontWeight.Bold), color = OC.paper)
                                Spacer(Modifier.weight(1f))
                                Box(
                                    Modifier.size(44.dp).alpha(if (canToggleCrop) 1f else 0f).plainClickable(enabled = canToggleCrop) { toggleCrop() }
                                        .semantics { contentDescription = if (cropToPage) "Use the whole photo" else "Crop to the page" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(if (cropToPage) Icons.Outlined.Crop else Icons.Outlined.CropFree, contentDescription = null, tint = OC.paper, modifier = Modifier.size(22.dp))
                                }
                            }
                            if (phase == ImportPhase.Viewfinder) {
                                ImportViewfinder(
                                    camera = camera,
                                    onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    onPickFile = { filePicker.launch(arrayOf("application/pdf", "image/*")) },
                                    onShutter = ::shutter,
                                    onOpenSettings = ::openSettings,
                                )
                            } else {
                                ImportReading(
                                    phase = phase, stage = stage, progress = progress, result = result, source = source,
                                    pickedImage = pickedImage, cropToPage = cropToPage,
                                    onRetake = ::retake, onReview = ::continueToReview,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun readBytes(context: Context, uri: Uri): ByteArray? =
    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()

/** "N of 10 free scans left today" — tapping opens the rewarded-ad offer. */
@Composable
private fun FreeScansLink(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    Text(
        "${app.freeScansLeft} of 10 free scans left today",
        style = outfit(12, FontWeight.SemiBold), color = OC.nightMuted,
        modifier = modifier.heightIn(min = 28.dp).plainClickable { app.modal = Modal.Reward(RewardKind.Scan) }.wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
private fun ColumnScope.ImportViewfinder(
    camera: ImportCamera,
    onPickPhoto: () -> Unit,
    onPickFile: () -> Unit,
    onShutter: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val app = LocalApp.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp).clip(shape)
            .background(Brush.linearGradient(listOf(hex(0x3A332A), hex(0x14120F)))),
        contentAlignment = Alignment.Center,
    ) {
        when (camera.status) {
            ImportCamera.Status.Running -> {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            camera.preview.setSurfaceProvider(surfaceProvider)
                        }
                    },
                    modifier = Modifier.fillMaxSize().clip(shape).semantics { contentDescription = "Camera preview" },
                )
                // Guide
                Box(Modifier.fillMaxSize()) {
                    ImportGuideFrame(Modifier.fillMaxSize().padding(28.dp))
                    Box(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).height(32.dp).clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Fit the whole page inside the gold frame", style = outfit(13, FontWeight.SemiBold), color = OC.paper) }
                }
            }
            ImportCamera.Status.Waiting -> CircularProgressIndicator(color = OC.paper, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            ImportCamera.Status.Unavailable -> CameraMessage(
                icon = Icons.Outlined.NoPhotography, title = "No camera here",
                text = "Choose a photo or PDF of the sheet instead.", action = "Choose a photo", onAction = onPickPhoto,
            )
            ImportCamera.Status.Denied -> CameraMessage(
                icon = Icons.Outlined.VideocamOff, title = "Camera is off for O Chords",
                text = "Turn it on in Settings to scan paper sheets, or choose a photo instead.", action = "Open Settings", onAction = onOpenSettings,
            )
        }
    }

    if (!app.isPro) FreeScansLink(Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp))

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(top = 14.dp, bottom = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var menu by remember { mutableStateOf(false) }
        Box {
            Box(
                Modifier.size(52.dp).nightGlass(26.dp).plainClickable { menu = true }.semantics { contentDescription = "Choose a photo or PDF" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = OC.paper, modifier = Modifier.size(22.dp)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = OC.surface, shape = RoundedCornerShape(14.dp)) {
                DropdownMenuItem(
                    text = { Text("Photo library", style = outfit(15), color = OC.ink) },
                    leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = OC.ink, modifier = Modifier.size(20.dp)) },
                    onClick = { menu = false; onPickPhoto() },
                )
                DropdownMenuItem(
                    text = { Text("Files — PDF or image", style = outfit(15), color = OC.ink) },
                    leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = OC.ink, modifier = Modifier.size(20.dp)) },
                    onClick = { menu = false; onPickFile() },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.size(74.dp).border(3.dp, Color.White, CircleShape).padding(5.dp)
                .plainClickable(onClick = onShutter)
                .semantics { contentDescription = if (camera.status == ImportCamera.Status.Running) "Take photo" else "Choose a photo" },
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White)) }
        Spacer(Modifier.weight(1f))
        val torchReady = camera.hasTorch && camera.status == ImportCamera.Status.Running
        Box(
            Modifier.size(52.dp).alpha(if (torchReady) 1f else 0f).clip(CircleShape)
                .background(if (camera.torchOn) OC.gold.copy(alpha = 0.3f) else OC.nightGlass)
                .border(1.dp, if (camera.torchOn) OC.gold else OC.nightBorder, CircleShape)
                .plainClickable(enabled = torchReady) { camera.setTorch(!camera.torchOn) }
                .semantics { contentDescription = "Flash, ${if (camera.torchOn) "On" else "Off"}" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (camera.torchOn) Icons.Filled.FlashOn else Icons.Outlined.FlashOff, contentDescription = null,
                tint = if (camera.torchOn) OC.gold else OC.paper, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CameraMessage(icon: ImageVector, title: String, text: String, action: String, onAction: () -> Unit) {
    Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = OC.gold, modifier = Modifier.size(34.dp))
        Text(title, style = outfit(18, FontWeight.Bold), color = OC.paper, textAlign = TextAlign.Center)
        Text(text, style = outfit(14), color = OC.nightMuted, textAlign = TextAlign.Center)
        GoldButton(action, Modifier.width(200.dp).padding(top = 6.dp), height = 44.dp, onClick = onAction)
    }
}

@Composable
private fun ColumnScope.ImportReading(
    phase: ImportPhase,
    stage: ScanStage?,
    progress: Float,
    result: ScanResult?,
    source: ScanSource?,
    pickedImage: Bitmap?,
    cropToPage: Boolean,
    onRetake: () -> Unit,
    onReview: () -> Unit,
) {
    val app = LocalApp.current
    val thumbnail = remember(result?.thumbnail) { result?.thumbnail?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    val shown = animateFloatAsState(progress, tween(500), label = "progress").value

    val headline = result?.let { r -> "Found ${r.song.sectionCount} section${if (r.song.sectionCount == 1) "" else "s"}" } ?: when (stage) {
        ScanStage.Page, null -> "Finding the page…"
        ScanStage.Text -> "Reading the sheet…"
        ScanStage.Chords -> "Finding chords…"
        ScanStage.Song -> "Building the song…"
    }

    fun stepText(s: ScanStage): String = when (s) {
        ScanStage.Page -> when {
            source is ScanSource.Pdf -> "Opened the PDF"
            result == null -> if (cropToPage) "Finding the page edges" else "Using the whole photo"
            result.croppedToPage -> "Found the page and straightened it"
            else -> "Using the whole photo"
        }
        ScanStage.Text -> result?.let { r -> "Read ${r.linesRead} line${if (r.linesRead == 1) "" else "s"} of text" + (if (r.readOnServer) ", Myanmar included" else "") } ?: "Reading text"
        ScanStage.Chords -> result?.let { r ->
            val chords = r.song.usedChords
            "Read chords — " + chords.take(6).joinToString(" · ") + (if (chords.size > 6) " …" else "")
        } ?: "Finding chords above the lyrics"
        ScanStage.Song -> result?.let { r ->
            "Arrangement — " + r.song.sections.filter { it.kind != SectionKind.Flow }.map { it.label.capitalizedWords() }.take(5).joinToString(" · ")
        } ?: "Laying out sections"
    }

    fun lyricsToTypeText(n: Int): String {
        val lines = "$n lyric line${if (n == 1) "" else "s"} to type in"
        if (result?.readOnServer == true) return "$lines — they weren’t clear enough to read"
        return lines + (if (app.isSignedIn) " — Myanmar reading isn’t available right now" else " — sign in to read Myanmar lyrics")
    }

    fun stepState(s: ScanStage): StepState {
        if (phase == ImportPhase.Done) return StepState.Done
        val current = stage ?: return StepState.Pending
        return if (s.ordinal < current.ordinal) StepState.Done else if (s == current) StepState.Active else StepState.Pending
    }

    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    ImportReadingSheet(
        progress = shown, image = thumbnail ?: pickedImage, regions = result?.regions ?: emptyList(), key = result?.song?.key,
        modifier = Modifier.weight(1f, fill = false).padding(top = 6.dp).padding(horizontal = 24.dp),
    )

    Column(
        Modifier.widthIn(max = 342.dp).fillMaxWidth().padding(horizontal = 24.dp).padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(headline, style = outfit(20, FontWeight.Bold), color = OC.paper)
            Spacer(Modifier.weight(1f))
            Text(
                result?.let { "${it.song.usedChords.size} chords · ${if (it.song.isMyanmar) "Myanmar" else "English"}" } ?: "${(shown * 100).toInt()}%",
                style = outfit(13), color = ImportNight.soft,
            )
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(OC.nightGlass).border(1.dp, OC.nightBorder, CircleShape)) {
            Box(Modifier.fillMaxWidth(shown.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(OC.gold))
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ImportStepRow(stepText(ScanStage.Page), stepState(ScanStage.Page))
            ImportStepRow(stepText(ScanStage.Text), stepState(ScanStage.Text))
            ImportStepRow(stepText(ScanStage.Chords), stepState(ScanStage.Chords))
            ImportStepRow(stepText(ScanStage.Song), stepState(ScanStage.Song))
            if (result?.detectedZawgyi == true) ImportStepRow("Typed in Zawgyi — converted to Unicode", StepState.Done)
            val n = result?.needsLyrics?.size ?: 0
            if (n > 0) ImportStepRow(lyricsToTypeText(n), StepState.Todo)
        }
    }
    }

    Spacer(Modifier.height(8.dp))

    if (!app.isPro) FreeScansLink(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))

    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val shape = RoundedCornerShape(OC.rButton)
        Box(
            Modifier.width(120.dp).height(52.dp).clip(shape).background(OC.nightGlass).border(1.dp, ImportNight.outline, shape).plainClickable(onClick = onRetake),
            contentAlignment = Alignment.Center,
        ) { Text("Retake", style = outfit(16, FontWeight.SemiBold), color = OC.paper) }
        GoldButton("Review import", Modifier.weight(1f).alpha(if (phase == ImportPhase.Done) 1f else 0.5f), height = 52.dp, enabled = phase == ImportPhase.Done, onClick = onReview)
    }
}

/** "verse 1" → "Verse 1" (the iOS `capitalized`). */
private fun String.capitalizedWords(): String = lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/** Gold corner brackets marking where the page should sit. */
@Composable
private fun ImportGuideFrame(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val l = 28.dp.toPx()
        val p = Path().apply {
            moveTo(0f, l); lineTo(0f, 0f); lineTo(l, 0f)
            moveTo(w - l, 0f); lineTo(w, 0f); lineTo(w, l)
            moveTo(w, h - l); lineTo(w, h); lineTo(w - l, h)
            moveTo(l, h); lineTo(0f, h); lineTo(0f, h - l)
        }
        drawPath(p, OC.gold, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawRoundRect(
            OC.gold.copy(alpha = 0.35f), cornerRadius = CornerRadius(10.dp.toPx()),
            style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
        )
    }
}

/** The sheet being read: a gold scan line while reading, then the sections found, outlined on the page. */
@Composable
private fun ImportReadingSheet(progress: Float, image: Bitmap?, regions: List<ScanRegion>, key: String?, modifier: Modifier = Modifier) {
    fun colors(kind: SectionKind): Pair<Color, Color> = when (kind) {
        SectionKind.Verse -> hex(0x1D4ED8) to hex(0x93C5FD, 0.2f)
        SectionKind.Chorus -> hex(0xC28C00) to hex(0xFDBA74, 0.24f)
        SectionKind.Bridge -> hex(0x6D28D9) to hex(0xC4B5FD, 0.22f)
        SectionKind.Music, SectionKind.Intro, SectionKind.Flow -> hex(0x0F766E) to hex(0x5EEAD4, 0.18f)
    }
    val ratio = if (image != null) image.width.toFloat() / image.height.toFloat() else 300f / 424f
    val label = if (regions.isEmpty()) "Photo of the chord sheet, ${(progress * 100).toInt()} percent read" else "Photo of the chord sheet with ${regions.size} sections outlined"
    Box(
        modifier.widthIn(max = 300.dp).aspectRatio(ratio).clip(RoundedCornerShape(8.dp)).semantics { contentDescription = label }
            .drawWithContent {
                drawContent()
                regions.forEach { r ->
                    val (border, fill) = colors(r.kind)
                    val topLeft = Offset((r.x * size.width).toFloat() - 4.dp.toPx(), (r.y * size.height).toFloat() - 3.dp.toPx())
                    val s = Size((r.width * size.width).toFloat() + 8.dp.toPx(), (r.height * size.height).toFloat() + 6.dp.toPx())
                    drawRoundRect(fill, topLeft, s, CornerRadius(6.dp.toPx()))
                    drawRoundRect(border, topLeft, s, CornerRadius(6.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
                if (progress < 1f) {
                    val y = size.height * progress
                    drawRect(OC.gold.copy(alpha = 0.35f), Offset(0f, y - 4.dp.toPx()), Size(size.width, 11.dp.toPx()))
                    drawRect(OC.gold, Offset(0f, y), Size(size.width, 3.dp.toPx()))
                }
            },
    ) {
        if (image != null) {
            val bitmap = remember(image) { image.asImageBitmap() }
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().alpha(0.85f))
        } else {
            ImportSheetArt(Modifier.fillMaxSize().alpha(0.55f))
        }
        if (key != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(OC.gold).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text("Key $key", style = outfit(11, FontWeight.Bold), color = OC.inkFixed)
            }
        }
    }
}

private enum class StepState { Done, Active, Pending, Todo }

@Composable
private fun ImportStepRow(text: String, state: StepState) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            when (state) {
                StepState.Done -> Icon(Icons.Rounded.Check, contentDescription = null, tint = ImportNight.teal, modifier = Modifier.size(15.dp))
                StepState.Active -> {
                    val spin by rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
                    Canvas(Modifier.size(15.dp).rotate(spin)) {
                        drawArc(OC.gold, -90f, 270f, useCenter = false, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                StepState.Pending -> Box(Modifier.size(14.dp).border(2.dp, OC.faint, CircleShape))
                StepState.Todo -> Icon(Icons.Outlined.Edit, contentDescription = null, tint = ImportNight.goldText, modifier = Modifier.size(14.dp))
            }
        }
        Text(text, style = outfit(14), color = if (state == StepState.Pending) OC.faint else OC.paper)
    }
}

/** 36b · Scan failed. */
@Composable
private fun ImportFailedView(photo: Bitmap?, onClose: () -> Unit, onRetake: () -> Unit, onTypeIn: () -> Unit, onChooseAnother: () -> Unit) {
    val tips = listOf(
        "Too blurry" to "Hold still for a second, or rest your phone on the table.",
        "Too dark" to "Move near a window or turn on the flash.",
        "Page is cut off" to "Fit the whole page inside the gold frame.",
    )
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).plainClickable(onClick = onClose).semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = OC.ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("Scan sheet", style = outfit(16, FontWeight.Bold), color = OC.ink)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp))
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FailedPhoto(photo)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow("Scan failed")
                Text("We couldn’t read this sheet", style = outfit(26, FontWeight.Bold, (-0.6).sp), color = OC.ink)
                Text("We didn’t find any chords. Try one of these and scan again.", style = outfit(15), color = OC.muted)
            }
            tips.forEach { (title, body) ->
                Row(Modifier.fillMaxWidth().glass(16.dp).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(OC.redTint), contentAlignment = Alignment.Center) {
                        Text("!", style = outfit(14, FontWeight.Heavy), color = OC.red)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = outfit(15, FontWeight.Bold), color = OC.ink)
                        Text(body, style = outfit(13), color = OC.muted)
                    }
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldButton("Retake photo", leading = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(20.dp)) }, onClick = onRetake)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Type it in", Modifier.weight(1f), height = 48.dp, onClick = onTypeIn)
                GlassButton("Choose another", Modifier.weight(1f), height = 48.dp, onClick = onChooseAnother)
            }
        }
    }
}

@Composable
private fun FailedPhoto(photo: Bitmap?) {
    Box(
        Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(hex(0x3A332A), hex(0x1E1B17))))
            .semantics { contentDescription = "Your photo, no chords found" },
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(2.5.dp).alpha(0.8f))
        } else {
            // A drawn sheet, tilted and soft, like the blurred photo would be.
            Column(
                Modifier.rotate(-7f).blur(2.5.dp).width(170.dp).height(150.dp).clip(RoundedCornerShape(6.dp))
                    .background(Brush.verticalGradient(listOf(hex(0xD8D0BF), hex(0xBFB6A3)))).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                listOf(0.7f, 0.9f, 0.55f, 0.85f, 0.6f, 0.8f).forEach { w ->
                    Box(Modifier.width(138.dp * w).height(5.dp).clip(RoundedCornerShape(3.dp)).background(hex(0x28221A, 0.45f)))
                }
            }
        }
        Canvas(Modifier.fillMaxSize().padding(22.dp)) {
            drawRoundRect(OC.gold.copy(alpha = 0.7f), cornerRadius = CornerRadius(14.dp.toPx()),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
        }
        Box(Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(10.dp)).background(hex(0xB91C1C, 0.92f)).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text("0 CHORDS FOUND", style = outfit(12, FontWeight.Heavy, 1.sp), color = Color.White)
        }
    }
}

// MARK: - 2b / 2c · Zawgyi → Unicode

private enum class ZawgyiMode { Unicode, Scanned }

@Composable
fun ZawgyiView(result: ScanResult) {
    val app = LocalApp.current
    var view by remember { mutableStateOf(ZawgyiMode.Unicode) }
    val uni = view == ZawgyiMode.Unicode

    // The scanned (Zawgyi) text is shown in a Unicode font, so it looks broken — which is the point.
    val lines = result.zawgyi?.preview ?: emptyList()
    val wordsConverted = result.zawgyi?.wordsConverted ?: 0

    /** The review step, with the song as scanned (unconverted) or converted. */
    fun review(converted: Boolean): Route {
        val scanned = result.zawgyi?.scannedSong
        if (converted || scanned == null) return Route.Review(result)
        return Route.Review(result.copy(song = scanned, needsLyrics = emptyList(), zawgyi = null))
    }

    OCPage {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title = "Myanmar text", backLabel = "Back to scan", onBack = { app.pop() })
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow("Zawgyi detected")
                    Text("We fixed the Myanmar text", style = outfit(28, FontWeight.Bold, (-0.7).sp), color = OC.ink)
                    Text(
                        "This sheet was typed in Zawgyi, an older Myanmar font. We converted it to Unicode so the lyrics show, search and share correctly on every phone.",
                        style = outfit(15).copy(lineHeight = 21.sp), color = OC.muted,
                    )
                }

                Segmented(listOf(ZawgyiMode.Unicode, ZawgyiMode.Scanned), view, label = { if (it == ZawgyiMode.Unicode) "Unicode (fixed)" else "As scanned" }) { view = it }

                val cardShape = RoundedCornerShape(OC.rCard)
                Column(
                    Modifier.fillMaxWidth().glass().then(if (!uni) Modifier.border(1.dp, OC.red.copy(alpha = 0.35f), cardShape) else Modifier).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (uni) "UNICODE PREVIEW" else "AS SCANNED", style = outfit(12, FontWeight.Bold, 1.sp), color = if (uni) OC.tealText else OC.red)
                        Spacer(Modifier.weight(1f))
                        StatusPill(if (uni) "Reads correctly" else "Breaks on most phones", tone = if (uni) PillTone.Teal else PillTone.Red)
                    }
                    lines.forEach { l ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(l.label, style = outfit(11, FontWeight.Bold), color = OC.muted)
                            Text(if (uni) l.converted else l.scanned, style = myanmar(18), color = if (uni) OC.ink else OC.red)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZawgyiStat("$wordsConverted", if (wordsConverted == 1) "word converted" else "words converted", OC.ink, Modifier.weight(1f))
                    ZawgyiStat("${((result.zawgyi?.clean ?: 1.0) * 100).roundToInt()}%", "converted cleanly", OC.teal, Modifier.weight(1f))
                    ZawgyiStat("${result.lowConfidence.size + result.needsLyrics.size}", "to check", OC.goldText, Modifier.weight(1f))
                }

                Row(
                    Modifier.fillMaxWidth().glass(16.dp).padding(horizontal = 14.dp, vertical = 12.dp).semantics { contentDescription = "Always convert automatically" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Always convert automatically", style = outfit(15, FontWeight.Bold), color = OC.ink)
                        Text("Skip this step next time", style = outfit(12), color = OC.muted)
                    }
                    OCSwitch(app.alwaysConvertZawgyi) { app.alwaysConvertZawgyi = it }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GoldButton("Continue with Unicode") { app.push(review(converted = true)) }
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).plainClickable { app.push(review(converted = false)) }, contentAlignment = Alignment.Center) {
                    Text("Keep as scanned", style = outfit(14, FontWeight.Bold), color = OC.muted)
                }
            }
        }
    }
}

@Composable
private fun ZawgyiStat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.glass(16.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = outfit(20, FontWeight.Heavy), color = color)
        Text(label, style = outfit(12), color = OC.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// MARK: - 3 · Review import

@Composable
fun ReviewView(result: ScanResult) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(result.song.title) }
    var artist by remember { mutableStateOf(result.song.artist) }
    var key by remember { mutableStateOf(result.song.key) }
    var sections by remember { mutableStateOf(result.song.sections) }
    var checks by remember { mutableStateOf(result.lowConfidence) }
    var confirmed by remember { mutableStateOf(setOf<String>()) }
    /** Lines whose lyrics the scan couldn't read, still to be typed in. */
    var needs by remember { mutableStateOf(result.needsLyrics) }
    var editing by remember { mutableStateOf<ScanLineRef?>(null) }
    var isPublic by remember { mutableStateOf(true) }
    var changing by remember { mutableStateOf<String?>(null) }
    var changeText by remember { mutableStateOf("") }
    var flashSection by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    /** Where the scroll content and each section card sit, for "Type" to scroll to the first missing line. */
    val coords = remember { HashMap<String, LayoutCoordinates>() }

    val openChecks = checks.count { it !in confirmed } + (if (needs.isEmpty()) 0 else 1)
    val unresolvedChords = checks.filter { it !in confirmed }.toSet()
    val thumbnail = remember(result.thumbnail) { result.thumbnail?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }

    LaunchedEffect(flashSection) { if (flashSection != null) { delay(1400); flashSection = null } }

    // MARK: Actions

    fun occurrences(chord: String): Int = sections.sumOf { s -> s.bars.count { it == chord } + s.lines.flatMap { ChordLine.chords(it) }.count { it == chord } }

    fun applyChange() {
        val old = changing ?: return
        val new = changeText.trim()
        changing = null
        if (new.isEmpty() || new == old) return
        sections = sections.map { s -> s.copy(bars = s.bars.map { if (it == old) new else it }, lines = s.lines.map { it.replace("[$old]", "[$new]") }) }
        checks = checks.map { if (it == old) new else it }
        confirmed = confirmed + new
    }

    fun line(ref: ScanLineRef): String? = sections.firstOrNull { it.id == ref.sectionID }?.lines?.getOrNull(ref.line)

    fun saveLine(text: String, ref: ScanLineRef) {
        val si = sections.indexOfFirst { it.id == ref.sectionID }
        if (si < 0 || ref.line !in sections[si].lines.indices) return
        sections = sections.toMutableList().apply { set(si, this[si].copy(lines = this[si].lines.toMutableList().apply { set(ref.line, text) })) }
        if (!ChordLine.parse(text).all { it.text.isEmpty() }) needs = needs.filter { it != ref }
        editing = null
    }

    fun deleteLine(ref: ScanLineRef) {
        val si = sections.indexOfFirst { it.id == ref.sectionID }
        if (si < 0 || ref.line !in sections[si].lines.indices) return
        sections = sections.toMutableList().apply { set(si, this[si].copy(lines = this[si].lines.toMutableList().apply { removeAt(ref.line) })) }
        // Later lines in this section move up one.
        needs = needs.mapNotNull { n ->
            if (n.sectionID != ref.sectionID) n
            else if (n.line == ref.line) null
            else if (n.line > ref.line) ScanLineRef(n.sectionID, n.line - 1) else n
        }
        editing = null
    }

    fun save() {
        val t = title.trim(); val a = artist.trim(); val k = key.trim()
        // Drop lines left completely empty and sections with nothing in them.
        val kept = sections.mapNotNull { s ->
            val ls = s.lines.filter { it.isNotBlank() }
            if (ls.isEmpty() && s.bars.isEmpty() && s.flow.isEmpty()) null else s.copy(lines = ls)
        }
        // Typed on a Zawgyi keyboard? Store Unicode, like everything else.
        val song = result.song.copy(
            title = Zawgyi.normalised(t), artist = Zawgyi.normalised(a), key = k.ifEmpty { "C" }, sections = kept,
            isMyanmar = result.song.isMyanmar || ChordSheetParser.isMyanmar(Zawgyi.normalised(t) + kept.flatMap { it.lines }.joinToString("")),
            isPublic = isPublic, addedAt = Instant.now(),
        )
        app.addToLibrary(song)
        result.reading?.let { app.keepScan(it, result.song, song.id) }
        app.popToRoot()
        app.push(Route.SongPage(song))
    }

    fun scrollTo(sectionID: String) {
        val content = coords["content"] ?: return
        val card = coords[sectionID] ?: return
        val y = content.localPositionOf(card, Offset.Zero).y.roundToInt()
        scope.launch { scrollState.animateScrollTo(max(0, y)) }
    }

    OCPage {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            TopBar(title = "Review import", backLabel = "Back to scan", onBack = { app.pop() })
            Column(
                Modifier.weight(1f).verticalScroll(scrollState).padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 20.dp)
                    .onGloballyPositioned { coords["content"] = it },
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Header: original sheet beside title / artist / key.
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val shape = RoundedCornerShape(16.dp)
                    Box(Modifier.width(84.dp).height(119.dp).clip(shape).border(1.dp, OC.border, shape).semantics { contentDescription = "Original sheet" }) {
                        if (thumbnail != null) Image(thumbnail.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else ImportSheetArt(Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewField("Title", title, { title = it }, myanmar(16, FontWeight.SemiBold), OC.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReviewField("Artist", artist, { artist = it }, myanmar(15), OC.ink, Modifier.weight(1f))
                            ReviewField("Key", key, { key = it }, mono(15), OC.goldText, Modifier.weight(1f))
                        }
                    }
                }

                // Arrangement
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReviewLabel("ARRANGEMENT")
                    WrapFlow(spacing = 6.dp, lineSpacing = 6.dp) {
                        arrangementChips(sections).forEach { (text, kind) ->
                            val (bg, fg) = sectionColors(kind)
                            Box(Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(text, style = outfit(13, FontWeight.Bold), color = fg)
                            }
                        }
                    }
                }

                // Checks
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReviewLabel(if (openChecks == 0) "ALL CHECKED" else "CHECK $openChecks THING${if (openChecks == 1) "" else "S"}")
                    checks.forEach { chord ->
                        if (chord in confirmed) {
                            Row(Modifier.fillMaxWidth().glass().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OC.teal, modifier = Modifier.size(20.dp))
                                Text("$chord confirmed", style = outfit(14, FontWeight.SemiBold), color = OC.ink)
                                Spacer(Modifier.weight(1f))
                                Text("Undo", style = outfit(14, FontWeight.Bold), color = OC.goldText,
                                    modifier = Modifier.heightIn(min = 44.dp).plainClickable { confirmed = confirmed - chord }.wrapContentHeight(Alignment.CenterVertically))
                            }
                        } else {
                            ChordCheck(
                                chord = chord, places = occurrences(chord), note = result.chordNotes[chord] ?: "The scan wasn’t sure about this chord",
                                onConfirm = { confirmed = confirmed + chord }, onChange = { changeText = chord; changing = chord },
                            )
                        }
                    }
                    needs.firstOrNull()?.let { first ->
                        Row(Modifier.fillMaxWidth().glass().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(14.dp)).background(hex(0xFEF3C7)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.TextFields, contentDescription = null, tint = hex(0x92400E), modifier = Modifier.size(18.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Type ${needs.size} lyric line${if (needs.size == 1) "" else "s"}", style = outfit(14, FontWeight.Bold), color = OC.ink)
                                Text(
                                    if (result.readOnServer) "Chords are in place. These lines weren’t clear enough to read."
                                    else "Chords are in place. Myanmar lyrics couldn’t be read this time — type them in.",
                                    style = outfit(13), color = OC.muted,
                                )
                            }
                            Text("Type", style = outfit(14, FontWeight.Bold), color = OC.goldText,
                                modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp).plainClickable {
                                    scrollTo(first.sectionID)
                                    flashSection = first.sectionID
                                    editing = first
                                }.wrapContentHeight(Alignment.CenterVertically))
                        }
                    }
                }

                // Who can see it
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReviewLabel("WHO CAN SEE IT")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VisibilityOption(isPublic, Icons.Outlined.Public, "Public", "Anyone can find it in Explore", OC.gold, OC.goldTint, Modifier.weight(1f)) { isPublic = true }
                        VisibilityOption(!isPublic, Icons.Outlined.Lock, "Only me", "Stays in your library", OC.ink, OC.line, Modifier.weight(1f)) { isPublic = false }
                    }
                }

                // Sections
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReviewLabel("SECTIONS")
                    sections.filter { it.kind != SectionKind.Flow }.forEach { s ->
                        val cardShape = RoundedCornerShape(OC.rCard)
                        Column(
                            Modifier.fillMaxWidth().onGloballyPositioned { coords[s.id] = it }.glass()
                                .then(if (flashSection == s.id) Modifier.border(2.dp, OC.gold, cardShape) else Modifier).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionTag(s.label, s.kind)
                                s.note?.let { Text(it, style = outfit(12), color = OC.muted) }
                            }
                            if (s.bars.isNotEmpty()) {
                                WrapFlow(spacing = 6.dp, lineSpacing = 6.dp) { s.bars.forEach { c -> ChordChip(c, big = true, low = c in unresolvedChords) } }
                            }
                            s.lines.forEachIndexed { i, line ->
                                val ref = ScanLineRef(s.id, i)
                                val missing = ref in needs
                                Column(
                                    Modifier.fillMaxWidth()
                                        .then(if (missing) Modifier.dashedBorder(OC.gold.copy(alpha = 0.7f), 1.5.dp, 10.dp, 5.dp, 4.dp) else Modifier)
                                        .plainClickable { editing = ref }
                                        .semantics { contentDescription = if (missing) "${s.label.capitalizedWords()} line ${i + 1}, lyrics missing. Edit this line" else "${s.label.capitalizedWords()} line ${i + 1}. Edit this line" }
                                        .padding(if (missing) 8.dp else 0.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    WrapFlow(spacing = 5.dp, lineSpacing = 4.dp) {
                                        ChordLine.parse(line).forEach { seg ->
                                            Column {
                                                val c = seg.chord
                                                if (c != null) ChordChip(c, big = false, low = c in unresolvedChords) else Spacer(Modifier.width(1.dp).height(20.dp))
                                                Text(seg.text.ifEmpty { " " }, style = myanmar(15), color = OC.ink)
                                            }
                                        }
                                    }
                                    if (missing) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = OC.goldText, modifier = Modifier.size(13.dp))
                                            Text("Tap to type the lyrics", style = outfit(12, FontWeight.SemiBold), color = OC.goldText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GoldButton(if (isPublic) "Save & share to O Chords" else "Save to my library", enabled = title.isNotBlank()) { save() }
                Text(if (isPublic) "Shared under your name. You can make it private any time." else "Only you will see this song.", style = outfit(12), color = OC.muted)
            }
        }
    }

    // Change chord
    if (changing != null) {
        Dialog(onDismissRequest = { changing = null }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(OC.rCard)).background(OC.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Change chord", style = outfit(18, FontWeight.Bold), color = OC.ink)
                Text("Replace ${changing ?: ""} everywhere in this song.", style = outfit(14), color = OC.muted)
                ReviewField("Chord", changeText, { changeText = it }, mono(15), OC.goldText, showLabel = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Cancel", Modifier.weight(1f), height = 44.dp) { changing = null }
                    InkButton("Apply", Modifier.weight(1f), height = 44.dp) { applyChange() }
                }
            }
        }
    }

    // Line editor
    val ref = editing
    val editingLine = ref?.let { line(it) }
    if (ref != null && editingLine != null) {
        LyricLineEditorSheet(
            sectionLabel = sections.firstOrNull { it.id == ref.sectionID }?.label?.capitalizedWords() ?: "",
            line = editingLine,
            missingLyrics = ref in needs,
            onDismiss = { editing = null },
            onSave = { saveLine(it, ref) },
            onDelete = { deleteLine(ref) },
        )
    }
}

/** Short arrangement chips: V1 · Cho · Br · Cho ×2 … */
private fun arrangementChips(sections: List<SongSection>): List<Pair<String, SectionKind>> {
    fun short(label: String, note: String?): String {
        val up = label.uppercase()
        var out = if (up.startsWith("VERSE")) "V" + up.drop(5).trim() else label.capitalizedWords().replace("Fade Out", "fade")
        if (note != null && note.contains("×2")) out += " ×2"
        return out
    }
    val out = ArrayList<Pair<String, SectionKind>>()
    for (s in sections) {
        if (s.kind == SectionKind.Flow) s.flow.forEach { out.add(short(it.label, null) to it.kind) }
        else out.add(short(s.label, s.note) to s.kind)
    }
    return out
}

@Composable
private fun ReviewLabel(text: String) {
    Text(text, style = outfit(13, FontWeight.Bold, 0.6.sp), color = OC.muted)
}

@Composable
private fun ReviewField(label: String, value: String, onChange: (String) -> Unit, font: TextStyle, color: Color, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (showLabel) Text(label, style = outfit(12, FontWeight.SemiBold), color = OC.muted)
        val style = font.copy(color = color, lineHeight = TextUnit.Unspecified)
        BasicTextField(
            value = value, onValueChange = onChange, textStyle = style, singleLine = true, cursorBrush = SolidColor(OC.gold),
            keyboardOptions = KeyboardOptions(capitalization = if (label == "Key" || label == "Chord") KeyboardCapitalization.None else KeyboardCapitalization.Sentences, autoCorrectEnabled = label != "Key" && label != "Chord"),
            modifier = Modifier.fillMaxWidth().height(38.dp).glass(14.dp).padding(horizontal = 10.dp).semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text("[$label]", style = style, color = OC.faint, maxLines = 1)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun ChordCheck(chord: String, places: Int, note: String, onConfirm: () -> Unit, onChange: () -> Unit) {
    val parts = chord.split("/", limit = 2)
    val superscript = note.startsWith("Superscript") && parts.size > 1
    Column(Modifier.fillMaxWidth().glass().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.clip(CircleShape).background(OC.goldTint).padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (superscript) {
                    Text(buildAnnotatedString {
                        append(parts[0])
                        withStyle(SpanStyle(fontSize = 10.sp, baselineShift = BaselineShift.Superscript)) { append(parts[1]) }
                        append(" → $chord")
                    }, style = mono(15), color = OC.goldText)
                } else {
                    Text(chord, style = mono(15), color = OC.goldText)
                }
            }
            Text("$note ($places place${if (places == 1) "" else "s"}).", style = outfit(14), color = OC.ink, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InkButton("Looks right", Modifier.weight(1f), height = 44.dp, onClick = onConfirm)
            GlassButton("Change", Modifier.weight(1f), height = 44.dp, onClick = onChange)
        }
    }
}

@Composable
private fun ChordChip(c: String, big: Boolean, low: Boolean) {
    val shape = RoundedCornerShape(if (low) 6.dp else 8.dp)
    Box(
        Modifier
            .then(if (low) Modifier.background(OC.redTint, shape).dashedBorder(OC.red.copy(alpha = 0.6f), 1.dp, 6.dp, 3.dp, 2.dp) else if (big) Modifier.background(OC.goldTint, shape) else Modifier)
            .heightIn(min = 20.dp)
            .padding(horizontal = if (low || big) 6.dp else 0.dp, vertical = if (big) 6.dp else 0.dp)
            .semantics { contentDescription = if (low) "$c, low confidence" else c },
        contentAlignment = Alignment.Center,
    ) { Text(c, style = mono(if (big) 15 else 13), color = if (low) OC.red else OC.goldText) }
}

@Composable
private fun VisibilityOption(on: Boolean, icon: ImageVector, title: String, sub: String, onBorder: Color, onFill: Color, modifier: Modifier = Modifier, action: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.heightIn(min = 64.dp).clip(shape).background(if (on) onFill else OC.glass).border(2.dp, if (on) onBorder else OC.border, shape)
            .plainClickable(onClick = action).semantics { contentDescription = if (on) "$title, selected" else title }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = OC.ink, modifier = Modifier.size(16.dp))
            Text(title, style = outfit(14, FontWeight.Heavy), color = OC.ink)
        }
        Text(sub, style = outfit(12), color = OC.muted, maxLines = 2)
    }
}

/** A dashed rounded outline (the "type the lyrics" and low-confidence markers). */
private fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp, dash: Dp, gap: Dp): Modifier = drawWithContent {
    drawContent()
    val inset = width.toPx() / 2
    drawRoundRect(
        color, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(radius.toPx()),
        style = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
    )
}

// MARK: - 40a–d · Listen


// MARK: - 49c–e · Rewarded ad

private enum class AdStage { Offer, Playing, Granted }

@Composable
fun RewardAdView(kind: RewardKind) {
    val app = LocalApp.current
    var stage by remember { mutableStateOf(AdStage.Offer) }
    var elapsed by remember { mutableFloatStateOf(0f) }
    val adLength = 5f

    fun close() { app.modal = null }

    fun grant() {
        when (kind) {
            RewardKind.Scan -> app.freeScansLeft += 1
            RewardKind.Strings -> app.lives = 6
        }
        stage = AdStage.Granted
    }

    LaunchedEffect(stage) {
        if (stage != AdStage.Playing) return@LaunchedEffect
        if (AdService.isConfigured) {
            // A real rewarded ad from AdMob; the reward only when they watched it through.
            val activity = app.activity
            if (activity != null && app.ads.showRewarded(activity)) grant() else close()
            return@LaunchedEffect
        }
        elapsed = 0f
        while (elapsed < adLength) {
            delay(100)
            elapsed = min(adLength, elapsed + 0.1f)
        }
        grant()
    }

    Crossfade(targetState = stage == AdStage.Offer, label = "ad") { offer ->
        if (offer) {
            if (kind == RewardKind.Scan) ScanOffer(onWatch = { stage = AdStage.Playing }, onClose = ::close)
            else StringsOffer(onWatch = { stage = AdStage.Playing }, onClose = { close(); app.pop() })
        } else {
            AdPlaying(kind = kind, granted = stage == AdStage.Granted, elapsed = elapsed, adLength = adLength, onClose = ::close)
        }
    }
}

/** 49c · Out of free scans. */
@Composable
private fun ScanOffer(onWatch: () -> Unit, onClose: () -> Unit) {
    val app = LocalApp.current
    Box(Modifier.fillMaxSize()) {
        WarmBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).plainClickable(onClick = onClose).semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = OC.ink, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("Scan sheet", style = outfit(16, FontWeight.Bold), color = OC.ink)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(44.dp))
            }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 10.dp).blur(1.dp).alpha(0.6f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) { Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(20.dp)).background(OC.glass)) }
            }
        }
        Box(Modifier.fillMaxSize().background(hex(0x171614, 0.35f)).plainClickable(role = null, onClick = onClose))

        val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().shadow(25.dp, sheetShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(sheetShape).background(OC.surface.copy(alpha = 0.97f))
                .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(5.dp).clip(CircleShape).background(OC.ink.copy(alpha = 0.18f)))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow("10 of 10 free scans used today")
                Text("Want one more scan?", style = outfit(24, FontWeight.Bold, (-0.5).sp), color = OC.ink)
                Text("Your free scans reset at midnight. You can also get one now.", style = outfit(14), color = OC.muted)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(OC.goldTint).border(2.dp, OC.gold, RoundedCornerShape(18.dp))
                    .plainClickable(onClick = onWatch).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(44.dp).shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = hex(0xFFAA00, 0.35f), spotColor = hex(0xFFAA00, 0.35f))
                        .clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(OC.goldTop, OC.goldBottom))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Watch a short ad", style = outfit(16, FontWeight.Bold), color = OC.ink)
                    Text("About 30 seconds · +1 scan now", style = outfit(13), color = OC.muted)
                }
                Text("2 left today", style = outfit(12, FontWeight.Heavy), color = OC.goldText)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(OC.inkFixed).plainClickable { app.modal = Modal.Pro }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Text("PRO", style = outfit(13, FontWeight.Heavy), color = OC.gold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Unlimited scans, no ads", style = outfit(16, FontWeight.Bold), color = OC.paper)
                    Text("7-day free trial", style = outfit(13), color = ImportNight.soft)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = OC.faint, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).plainClickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Text("Wait until tomorrow", style = outfit(14, FontWeight.Bold), color = OC.muted)
            }
        }
    }
}

/** 49e · Out of strings. */
@Composable
private fun StringsOffer(onWatch: () -> Unit, onClose: () -> Unit) {
    val app = LocalApp.current
    Box(Modifier.fillMaxSize()) {
        WarmBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).plainClickable(onClick = onClose).semantics { contentDescription = "Leave lesson" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = OC.ink, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("Take 4 of 5", style = outfit(13, FontWeight.Bold), color = OC.muted)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(44.dp))
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(Modifier.glass(24.dp).padding(horizontal = 26.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(-8f, 6f, -4f, 9f, -7f, 5f).forEach { deg ->
                        Box(Modifier.rotate(deg).width(4.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(OC.red))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("All six strings snapped", style = outfit(28, FontWeight.Bold, (-0.7).sp), color = OC.ink, textAlign = TextAlign.Center)
                    Text("You were one take away. Restring now to keep your progress in this lesson.", style = outfit(15).copy(lineHeight = 21.sp), color = OC.muted, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GoldButton("Watch an ad to restring", leading = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = OC.inkFixed, modifier = Modifier.size(16.dp)) }, onClick = onWatch)
                Box(Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(OC.rButton)).background(OC.inkFixed).plainClickable { app.modal = Modal.Pro }, contentAlignment = Alignment.Center) {
                    Text("Unlimited strings with Pro", style = outfit(16, FontWeight.Bold), color = OC.paper)
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).plainClickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Text("Strings come back in 30 min", style = outfit(14, FontWeight.Bold), color = OC.muted)
                }
            }
        }
    }
}

/** 49d · Ad playing → reward granted. */
@Composable
private fun AdPlaying(kind: RewardKind, granted: Boolean, elapsed: Float, adLength: Float, onClose: () -> Unit) {
    val left = max(0, ceil(adLength - elapsed).toInt())
    val reward = if (kind == RewardKind.Scan) "+1 scan" else "6 new strings"
    Box(Modifier.fillMaxSize()) {
        NightBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.height(18.dp).border(1.dp, hex(0xFFC940, 0.7f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        Text("Ad", style = outfit(10, FontWeight.Heavy, 0.5.sp), color = ImportNight.goldText)
                    }
                    Text("[Advertiser]", style = outfit(13), color = ImportNight.muted)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.height(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (granted) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = ImportNight.teal, modifier = Modifier.size(14.dp))
                        Text("Reward earned", style = outfit(13, FontWeight.Bold), color = ImportNight.text)
                    } else {
                        Text("Reward in", style = outfit(13, FontWeight.Bold), color = ImportNight.text)
                        Text("0:" + left.toString().padStart(2, '0'), style = mono(13), color = OC.gold)
                    }
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
                PlaceholderBox("[Ad creative from Google]", height = min(420f, maxHeight.value).dp, dark = true)
            }

            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxWidth(if (granted) 1f else (elapsed / adLength).coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(if (granted) ImportNight.teal else OC.gold))
            }

            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(hex(0x14B8A6, 0.16f)).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(if (granted) Icons.Filled.CheckCircle else Icons.Rounded.Check, contentDescription = null, tint = ImportNight.teal, modifier = Modifier.size(if (granted) 18.dp else 14.dp))
                    Text(buildAnnotatedString {
                        if (granted) {
                            append("Reward granted: ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(reward) }
                            append(if (kind == RewardKind.Scan) ". You can scan another sheet now." else ". Back to your lesson.")
                        } else {
                            append("Reward: ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(reward) }
                            append(if (kind == RewardKind.Scan) ". You’ll be back on the scan screen after the ad." else ". You’ll be back in your lesson after the ad.")
                        }
                    }, style = outfit(14, FontWeight.SemiBold), color = ImportNight.text, modifier = Modifier.weight(1f))
                }
                if (granted) {
                    GoldButton(if (kind == RewardKind.Scan) "Back to scan" else "Back to lesson", onClick = onClose)
                } else {
                    val shape = RoundedCornerShape(OC.rButton)
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(shape).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.14f), shape).plainClickable(onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) { Text("Close ad and skip reward", style = outfit(16, FontWeight.Bold), color = ImportNight.muted) }
                }
            }
        }
    }
}
