// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.singular.writer.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * The app's own icon — three sheets of paper seen edge-on — drifting as a watermark, while the
 * folder is read.
 *
 * This replaced three pulsing dots on 2026-09-09. The dots were honest and said nothing: any app
 * can draw three dots. The sheets are the thing the app is about, and a stack of paper riffling
 * gently is a better answer to *what is happening* than a generic pulse, because it is literally
 * the picture of leafing through a folder.
 *
 * Deliberately not a `CircularProgressIndicator`, for the reason it was never one: that is a system
 * part with a system's manners — it spins at its own speed in the accent colour and says *something
 * is working*. This says *reading*, which is what is happening and all that needs saying on a page
 * about to be full of writing.
 *
 * ## Why it is a watermark and not the icon
 *
 * The first version drew the icon in its own colours, and it was a logo sitting in the middle of an
 * empty page — the app introducing itself when nobody asked. A watermark is the same drawing making
 * a smaller claim: large enough to be unmistakably the sheets, faint enough that it belongs to the
 * page rather than sitting on it.
 *
 * That is why this is a single ink at [FILL_ALPHA] with a [STROKE_ALPHA] hairline, and not the
 * icon's palette. Two things follow from it. The **overlaps do the shading**: where sheets cross,
 * the translucent fills compound and the stack's depth appears for free, so nothing needs a second
 * colour to read as *behind*. And the icon's edge paths are gone — the second copy of each sheet
 * two units lower that gives it thickness. Thickness is a solid-object idea; on a flat translucent
 * mark it reads as a smudge. The hairline does that job instead, and keeps each sheet legible at
 * the moment of the cycle where they gather closest together.
 *
 * ## The movement
 *
 * Each sheet rides a sine, and the three are a sixth of a cycle apart. That phase offset is the
 * whole trick, and it is the same one the dots used: three sheets rising together would be one
 * object bobbing, whereas a wave travelling up the stack reads as *pages being gone through*.
 *
 * The travel is [AMPLITUDE] units, which is deliberately close to the roughly 57 that separate one
 * sheet from the next. The neighbouring pair is worst placed a third of a cycle apart, closing to
 * about 17 units at the tightest — near enough to touch, which is the point of a bigger amplitude,
 * and still short of crossing. **Do not raise it past about 50**: beyond that the sheets pass
 * through each other and the stack stops being a stack.
 *
 * ## Waiting before it appears
 *
 * The mark waits [APPEARS_AFTER] before showing, exactly as the dots did. A folder already warm in
 * the provider's cache comes back faster than that, and an indicator that appears and vanishes
 * inside a fifth of a second is worse than none: it reads as a fault. So the fast path stays blank
 * and only a read slow enough to notice gets an answer.
 */
@Composable
fun LoadingSheets(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.library_loading)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(APPEARS_AFTER)
        visible = true
    }

    val sheets = remember { Sheets.faces() }
    val ink = MaterialTheme.colorScheme.onSurfaceVariant

    val transition = rememberInfiniteTransition(label = "loading")
    val phases = sheets.indices.map { i ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(CYCLE, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
                // The bottom sheet leads, so the wave travels up through the stack.
                initialStartOffset = StartOffset(i * CYCLE / 6),
            ),
            label = "sheet$i",
        )
    }

    Canvas(
        modifier
            .size(width = ICON_WIDTH, height = ICON_HEIGHT)
            .alpha(if (visible) 1f else 0f)
            .semantics { contentDescription = description },
    ) {
        val scale = size.width / VIEW_WIDTH
        sheets.forEachIndexed { i, face -> sheet(face, ink, phases[i].value, scale) }
    }
}

/** One sheet: a translucent face and the hairline that keeps its outline when the stack closes. */
private fun DrawScope.sheet(face: Path, ink: Color, phase: Float, scale: Float) {
    val lift = AMPLITUDE * sin(2f * PI.toFloat() * phase)
    withTransform({
        translate(-VIEW_LEFT * scale, (AMPLITUDE - VIEW_TOP + lift) * scale)
        scale(scale, scale, Offset.Zero)
    }) {
        drawPath(face, ink, alpha = FILL_ALPHA)
        drawPath(face, ink, alpha = STROKE_ALPHA, style = Stroke(width = STROKE_WIDTH))
    }
}

/**
 * The icon's outlines, lifted verbatim from `assets/icon_animation.svg`.
 *
 * The file draws five paths, not three: each dark sheet is a face with a second copy two units
 * lower showing as its edge. Only the faces are here — see the note on thickness above.
 */
private object Sheets {
    fun faces() = listOf(path(BOTTOM), path(MIDDLE), path(TOP))

    private fun path(data: String): Path = PathParser().parsePathString(data).toPath()

    private const val BOTTOM =
        "M313.3 285.249C330.834 275.53 353.766 271.871 373.65 272.806C388.73 273.574 403.463 277.606 416.833 284.623C424.091 288.411 432.302 294.054 439.312 298.566C453.237 307.534 467.114 316.579 480.942 325.701C524.133 353.827 571.251 384.941 615.005 411.462C634.714 423.244 655.161 433.741 676.22 442.885C687.169 447.718 706.08 455.082 715.764 460.705C706.53 470.264 693.321 477.927 682.132 485.098C665.295 495.889 647.546 505.742 630.625 516.352C616.894 523.913 600.32 534.42 586.487 542.61L508.795 588.503C501.977 592.593 495.201 596.785 488.384 600.862C472.756 610.216 458.728 620.589 440.978 625.757C419.926 631.891 397.178 632.155 376.171 625.673C361.593 621.17 349.209 612.728 336.521 604.795L306.136 585.788L206.939 523.931C188.98 512.621 164.194 495.942 146.209 486.032C130.239 475.835 114.115 465.882 97.8428 456.176C88.475 450.61 70.4869 441.363 63.2363 434.566C70.3432 428.069 92.1681 416.244 101.666 410.751L158.045 377.609L263.584 314.428C279.903 304.674 296.672 294.464 313.3 285.249Z"

    private const val MIDDLE =
        "M313.3 228.581C330.834 218.862 353.766 215.203 373.65 216.138C388.73 216.906 403.463 220.938 416.833 227.955C424.091 231.743 432.302 237.387 439.312 241.898C453.237 250.867 467.114 259.912 480.942 269.033C524.133 297.16 571.251 328.273 615.005 354.795C634.714 366.577 655.161 377.073 676.22 386.218C687.169 391.05 706.08 398.414 715.764 404.037C706.53 413.596 693.321 421.26 682.132 428.43C665.295 439.222 647.546 449.074 630.625 459.684C616.894 467.245 600.32 477.753 586.487 485.942L508.795 531.836C501.977 535.925 495.201 540.117 488.384 544.194C472.756 553.548 458.728 563.921 440.978 569.09C419.926 575.224 397.178 575.487 376.171 569.006C361.593 564.503 349.209 556.061 336.521 548.128L306.136 529.12L206.939 467.263C188.98 455.953 164.194 439.274 146.209 429.364C130.239 419.167 114.115 409.214 97.8428 399.509C88.475 393.942 70.4869 384.695 63.2363 377.898C63.2363 377.898 127.992 338.608 158.045 320.941L263.584 257.761C279.903 248.006 296.672 237.796 313.3 228.581Z"

    private const val TOP =
        "M313.3 161.137C330.834 151.419 353.766 147.759 373.65 148.695C388.73 149.462 403.463 153.495 416.833 160.511C424.091 164.3 432.302 169.943 439.312 174.455C453.237 183.423 467.114 192.468 480.942 201.589C524.133 229.716 571.251 260.83 615.005 287.351C634.714 299.133 655.161 309.629 676.22 318.774C687.169 323.607 706.08 330.971 715.764 336.593C706.53 346.153 693.321 353.816 682.132 360.987C665.295 371.778 647.546 381.631 630.625 392.241C616.894 399.802 600.32 410.309 586.487 418.499L508.795 464.392C501.977 468.481 495.201 472.673 488.384 476.75C472.756 486.104 458.728 496.477 440.978 501.646C419.926 507.78 397.178 508.044 376.171 501.562C361.593 497.059 349.209 488.617 336.521 480.684L306.136 461.676L206.939 399.82C188.98 388.509 164.194 371.83 146.209 361.92C130.239 351.723 114.115 341.77 97.8428 332.065C88.475 326.499 70.4869 317.251 63.2363 310.455C70.3432 303.958 92.1681 292.132 101.666 286.639L158.045 253.498L263.584 190.317C279.903 180.562 296.672 170.353 313.3 161.137Z"
}

/** The drawing sits well inside the SVG's 779-unit square; these crop the empty margin away. */
private const val VIEW_LEFT = 63f
private const val VIEW_TOP = 148f
private const val VIEW_WIDTH = 653f
private const val VIEW_HEIGHT = 487f

/** Half the travel of a sheet, in those same units, and the room left above and below for it. */
private const val AMPLITUDE = 40f

/** Roughly a device pixel-and-a-half at [ICON_WIDTH], in icon units. */
private const val STROKE_WIDTH = 5f

private const val FILL_ALPHA = 0.10f
private const val STROKE_ALPHA = 0.22f

private val ICON_WIDTH = 128.dp
private val ICON_HEIGHT = (ICON_WIDTH.value * (VIEW_HEIGHT + 2 * AMPLITUDE) / VIEW_WIDTH).dp

private const val CYCLE = 1_800
private const val APPEARS_AFTER = 220L
