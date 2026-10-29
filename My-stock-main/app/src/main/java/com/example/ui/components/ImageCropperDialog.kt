package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import kotlin.math.roundToInt
import com.example.ui.theme.SipTealPrimary
import com.example.util.ImageStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CropAspectRatio(val label: String, val ratio: Float?) {
    SQUARE_1_1("1:1", 1.0f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_3_2("3:2", 3f / 2f),
    RATIO_3_4("3:4", 3f / 4f),
    FREE("Free", null)
}

private enum class CropHandle { NONE, MOVE, L, R, T, B, TL, TR, BL, BR }

private fun initialCropRect(bounds: Rect, ratio: Float?): Rect {
    val maxW = bounds.width * 0.8f
    val maxH = bounds.height * 0.8f
    val w: Float
    val h: Float
    if (ratio == null) {
        w = maxW; h = maxH
    } else if (maxW / maxH > ratio) {
        h = maxH; w = h * ratio
    } else {
        w = maxW; h = w / ratio
    }
    val cx = bounds.center.x
    val cy = bounds.center.y
    return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
}

private fun hitTestHandle(rect: Rect, p: Offset, slop: Float): CropHandle {
    val nearL = kotlin.math.abs(p.x - rect.left) <= slop
    val nearR = kotlin.math.abs(p.x - rect.right) <= slop
    val nearT = kotlin.math.abs(p.y - rect.top) <= slop
    val nearB = kotlin.math.abs(p.y - rect.bottom) <= slop
    val inX = p.x >= rect.left - slop && p.x <= rect.right + slop
    val inY = p.y >= rect.top - slop && p.y <= rect.bottom + slop
    return when {
        nearL && nearT -> CropHandle.TL
        nearR && nearT -> CropHandle.TR
        nearL && nearB -> CropHandle.BL
        nearR && nearB -> CropHandle.BR
        nearL && inY -> CropHandle.L
        nearR && inY -> CropHandle.R
        nearT && inX -> CropHandle.T
        nearB && inX -> CropHandle.B
        rect.contains(p) -> CropHandle.MOVE
        else -> CropHandle.NONE
    }
}

private fun moveCrop(rect: Rect, dx: Float, dy: Float, bounds: Rect): Rect {
    val nx = (rect.left + dx).coerceIn(bounds.left, bounds.right - rect.width)
    val ny = (rect.top + dy).coerceIn(bounds.top, bounds.bottom - rect.height)
    return Rect(nx, ny, nx + rect.width, ny + rect.height)
}

private fun resizeCrop(
    rect: Rect,
    handle: CropHandle,
    dx: Float,
    dy: Float,
    bounds: Rect,
    ratio: Float?,
    minSize: Float
): Rect {
    var l = rect.left
    var t = rect.top
    var r = rect.right
    var b = rect.bottom
    val movesL = handle == CropHandle.L || handle == CropHandle.TL || handle == CropHandle.BL
    val movesR = handle == CropHandle.R || handle == CropHandle.TR || handle == CropHandle.BR
    val movesT = handle == CropHandle.T || handle == CropHandle.TL || handle == CropHandle.TR
    val movesB = handle == CropHandle.B || handle == CropHandle.BL || handle == CropHandle.BR
    if (movesL) l = (l + dx).coerceIn(bounds.left, r - minSize)
    if (movesR) r = (r + dx).coerceIn(l + minSize, bounds.right)
    if (movesT) t = (t + dy).coerceIn(bounds.top, b - minSize)
    if (movesB) b = (b + dy).coerceIn(t + minSize, bounds.bottom)

    if (ratio == null) return Rect(l, t, r, b)

    // Locked ratio: derive the other dimension and anchor on the opposite side / centre.
    val horizontalOnly = (movesL || movesR) && !movesT && !movesB
    val verticalOnly = (movesT || movesB) && !movesL && !movesR
    var w = r - l
    var h = b - t
    if (verticalOnly) w = h * ratio else h = w / ratio

    val newL: Float
    val newT: Float
    newL = when {
        verticalOnly -> rect.center.x - w / 2f
        movesL -> rect.right - w
        else -> rect.left
    }
    newT = when {
        horizontalOnly -> rect.center.y - h / 2f
        movesT -> rect.bottom - h
        else -> rect.top
    }
    val out = Rect(newL, newT, newL + w, newT + h)
    val inside = out.left >= bounds.left - 0.5f && out.right <= bounds.right + 0.5f &&
        out.top >= bounds.top - 0.5f && out.bottom <= bounds.bottom + 0.5f
    return if (inside && w >= minSize && h >= minSize) out else rect
}

@Composable
fun ImageCropperDialog(
    imageUri: Uri,
    imageStorageManager: ImageStorageManager,
    onCropped: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedAspect by remember { mutableStateOf(CropAspectRatio.SQUARE_1_1) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var resetTick by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val cropStateHolder = remember { CropHolder() }

    LaunchedEffect(imageUri) {
        isLoading = true
        sourceBitmap = imageStorageManager.loadBitmapFromUri(imageUri)
        isLoading = false
    }

    // Rotated copy used for both preview and final crop so what you see is what you get.
    val displayBitmap = remember(sourceBitmap, rotationDegrees) {
        val src = sourceBitmap
        if (src == null) null
        else if (rotationDegrees % 360f == 0f) src
        else {
            val m = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }
    }

    // Same dialog shell as the Add / Edit Drink dialog (default width, 22dp corners).
    Dialog(onDismissRequest = onCancel) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("image_cropper_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Crop Photo",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Row {
                        IconButton(onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f }) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°")
                        }
                        IconButton(onClick = { rotationDegrees = 0f; resetTick++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                    }
                }

                // Aspect ratio chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CropAspectRatio.entries.forEach { aspect ->
                        FilterChip(
                            selected = selectedAspect == aspect,
                            onClick = { selectedAspect = aspect },
                            label = { Text(aspect.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SipTealPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Crop area: adjustable frame with rule-of-thirds grid
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = displayBitmap
                    if (isLoading || bmp == null) {
                        CircularProgressIndicator(color = SipTealPrimary)
                    } else {
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val canvasW = constraints.maxWidth.toFloat()
                        val canvasH = constraints.maxHeight.toFloat()
                        val fit = minOf(canvasW / bmp.width, canvasH / bmp.height)
                        val imgW = bmp.width * fit
                        val imgH = bmp.height * fit
                        val imageRect = Rect(
                            (canvasW - imgW) / 2f, (canvasH - imgH) / 2f,
                            (canvasW + imgW) / 2f, (canvasH + imgH) / 2f
                        )
                        val slop = with(density) { 28.dp.toPx() }
                        val minSize = with(density) { 48.dp.toPx() }
                        val handleLen = with(density) { 18.dp.toPx() }
                        val strokeW = with(density) { 2.dp.toPx() }
                        val thickW = with(density) { 4.dp.toPx() }

                        var cropRect by remember(bmp, selectedAspect, canvasW, canvasH, resetTick) {
                            mutableStateOf(initialCropRect(imageRect, selectedAspect.ratio))
                        }
                        val rectState = rememberUpdatedState(cropRect)
                        val ratio = selectedAspect.ratio

                        // Expose current crop for the Save button
                        cropStateHolder.value = CropSnapshot(cropRect, imageRect, bmp)

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(bmp, selectedAspect, canvasW, canvasH) {
                                    var active = CropHandle.NONE
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            active = hitTestHandle(rectState.value, start, slop)
                                        },
                                        onDragEnd = { active = CropHandle.NONE },
                                        onDragCancel = { active = CropHandle.NONE }
                                    ) { change: PointerInputChange, drag: Offset ->
                                        change.consume()
                                        cropRect = when (active) {
                                            CropHandle.NONE -> cropRect
                                            CropHandle.MOVE -> moveCrop(cropRect, drag.x, drag.y, imageRect)
                                            else -> resizeCrop(cropRect, active, drag.x, drag.y, imageRect, ratio, minSize)
                                        }
                                    }
                                }
                        ) {
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
                                dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt())
                            )

                            val c = cropRect
                            val scrim = Color.Black.copy(alpha = 0.6f)
                            drawRect(scrim, Offset(0f, 0f), Size(size.width, c.top))
                            drawRect(scrim, Offset(0f, c.bottom), Size(size.width, size.height - c.bottom))
                            drawRect(scrim, Offset(0f, c.top), Size(c.left, c.height))
                            drawRect(scrim, Offset(c.right, c.top), Size(size.width - c.right, c.height))

                            // Grid (rule of thirds)
                            val gridColor = Color.White.copy(alpha = 0.55f)
                            for (i in 1..2) {
                                val gx = c.left + c.width * i / 3f
                                val gy = c.top + c.height * i / 3f
                                drawLine(gridColor, Offset(gx, c.top), Offset(gx, c.bottom), strokeWidth = 1.dp.toPx())
                                drawLine(gridColor, Offset(c.left, gy), Offset(c.right, gy), strokeWidth = 1.dp.toPx())
                            }

                            // Frame
                            drawRect(Color.White, c.topLeft, c.size, style = Stroke(width = strokeW))

                            // Thick corner handles
                            val hc = Color.White
                            fun corner(x: Float, y: Float, sx: Float, sy: Float) {
                                drawLine(hc, Offset(x - sx * thickW / 2f, y), Offset(x + sx * handleLen, y), strokeWidth = thickW)
                                drawLine(hc, Offset(x, y - sy * thickW / 2f), Offset(x, y + sy * handleLen), strokeWidth = thickW)
                            }
                            corner(c.left, c.top, 1f, 1f)
                            corner(c.right, c.top, -1f, 1f)
                            corner(c.left, c.bottom, 1f, -1f)
                            corner(c.right, c.bottom, -1f, -1f)

                            // Edge midpoint handles
                            val half = handleLen / 2f
                            drawLine(hc, Offset(c.center.x - half, c.top), Offset(c.center.x + half, c.top), strokeWidth = thickW)
                            drawLine(hc, Offset(c.center.x - half, c.bottom), Offset(c.center.x + half, c.bottom), strokeWidth = thickW)
                            drawLine(hc, Offset(c.left, c.center.y - half), Offset(c.left, c.center.y + half), strokeWidth = thickW)
                            drawLine(hc, Offset(c.right, c.center.y - half), Offset(c.right, c.center.y + half), strokeWidth = thickW)
                        }
                    }
                }

                Text(
                    text = "Drag the frame to move it, drag edges or corners to resize.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        onClick = {
                            val snap = cropStateHolder.value ?: return@Button
                            coroutineScope.launch {
                                val cropped = withContext(Dispatchers.Default) { cropBitmap(snap) }
                                onCropped(cropped)
                            }
                        },
                        enabled = displayBitmap != null && !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = SipTealPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Save Crop")
                    }
                }
            }
        }
    }
}

private class CropSnapshot(val crop: Rect, val imageRect: Rect, val bitmap: Bitmap)

private class CropHolder { var value: CropSnapshot? = null }

private fun cropBitmap(s: CropSnapshot): Bitmap {
    val bmp = s.bitmap
    val k = bmp.width / s.imageRect.width
    val x = ((s.crop.left - s.imageRect.left) * k).roundToInt().coerceIn(0, bmp.width - 1)
    val y = ((s.crop.top - s.imageRect.top) * k).roundToInt().coerceIn(0, bmp.height - 1)
    val w = (s.crop.width * k).roundToInt().coerceIn(1, bmp.width - x)
    val h = (s.crop.height * k).roundToInt().coerceIn(1, bmp.height - y)
    val out = Bitmap.createBitmap(bmp, x, y, w, h)
    val maxSide = 1024
    val longest = maxOf(out.width, out.height)
    return if (longest > maxSide) {
        val f = maxSide.toFloat() / longest
        Bitmap.createScaledBitmap(out, (out.width * f).roundToInt().coerceAtLeast(1), (out.height * f).roundToInt().coerceAtLeast(1), true)
    } else out
}
