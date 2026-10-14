package com.englishtutor.ui.flashcards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class FlashcardResolvedLayout(
    val englishSp: Int,
    val russianSp: Int,
    val englishTopDp: Int,
    val enRuGapDp: Int,
    /** Horizontal inset for the card text area. */
    val horizontalPaddingDp: Int,
    /** False when even minimum sizes still overflow (caller may allow scroll). */
    val fits: Boolean,
)

private const val MIN_EN_SP = 11
private const val ABS_MIN_EN_SP = 8
private const val MIN_RU_SP = 10
private const val ABS_MIN_RU_SP = 7
private const val MIN_TOP_DP = 0
private const val MIN_GAP_DP = 2
private const val ABS_MIN_GAP_DP = 0
private const val DEFAULT_H_PAD_DP = 24
private const val MIN_H_PAD_DP = 8
private const val ABS_MIN_H_PAD_DP = 4
/** Extra slack so measured layout stays inside the clipped card area. */
private const val SAFETY_DP = 12

internal const val FLASHCARD_EN_LINE_MULT = 1.15f
internal const val FLASHCARD_RU_LINE_MULT = 1.2f

internal fun flashcardTextStyle(sp: Int, lineHeightMult: Float): TextStyle {
    val size = sp.coerceAtLeast(ABS_MIN_RU_SP)
    return TextStyle(
        fontSize = size.sp,
        lineHeight = (size * lineHeightMult).sp,
        textAlign = TextAlign.Center,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
}

@Composable
fun rememberFlashcardResolvedLayout(
    settings: FlashcardDisplaySettings,
    english: String,
    russian: String?,
    contentWidth: Dp,
    contentHeight: Dp,
    chromeHeight: Dp = 0.dp,
): FlashcardResolvedLayout {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fullWidthPx = with(density) { contentWidth.roundToPx() }.coerceAtLeast(1)
    val maxHeightPx = with(density) { (contentHeight - chromeHeight).roundToPx() }.coerceAtLeast(1)
    val dpToPx: (Int) -> Int = { dp -> with(density) { dp.dp.roundToPx() } }

    return remember(
        settings,
        english,
        russian,
        fullWidthPx,
        maxHeightPx,
        density.density,
        density.fontScale,
    ) {
        resolveFlashcardLayout(
            settings = settings,
            english = english,
            russian = russian,
            fullWidthPx = fullWidthPx,
            maxHeightPx = maxHeightPx,
            dpToPx = dpToPx,
            measure = { text, sp, lineMult, widthPx ->
                if (text.isBlank() || widthPx <= 0) {
                    0
                } else {
                    textMeasurer.measure(
                        text = text,
                        style = flashcardTextStyle(sp, lineMult),
                        overflow = TextOverflow.Clip,
                        softWrap = true,
                        constraints = Constraints(maxWidth = widthPx),
                    ).size.height
                }
            },
        )
    }
}

internal fun resolveFlashcardLayout(
    settings: FlashcardDisplaySettings,
    english: String,
    russian: String?,
    fullWidthPx: Int,
    maxHeightPx: Int,
    dpToPx: (Int) -> Int,
    measure: (text: String, sp: Int, lineHeightMult: Float, widthPx: Int) -> Int,
): FlashcardResolvedLayout {
    val percent = settings.russianSmallerPercent.coerceIn(10, 55)
    val ruRatio = (100 - percent) / 100f
    val desiredTop = settings.englishTopPaddingDp.coerceIn(0, 160)
    val desiredGap = settings.enRuGapDp.coerceIn(0, 120)
    val desiredEn = settings.englishSp.coerceIn(ABS_MIN_EN_SP, 72)
    val desiredRuCap = settings.russianSp.coerceAtLeast(ABS_MIN_RU_SP)
    val hasRu = !russian.isNullOrBlank()
    val budgetPx = (maxHeightPx - dpToPx(SAFETY_DP)).coerceAtLeast(1)

    fun ruFor(en: Int): Int =
        floor(en * ruRatio).roundToInt().coerceIn(ABS_MIN_RU_SP, (en - 1).coerceAtLeast(ABS_MIN_RU_SP))

    fun textWidthPx(hPadDp: Int): Int = (fullWidthPx - 2 * dpToPx(hPadDp)).coerceAtLeast(1)

    fun totalHeight(en: Int, ru: Int, top: Int, gap: Int, hPad: Int): Int {
        val width = textWidthPx(hPad)
        val enH = measure(english, en, FLASHCARD_EN_LINE_MULT, width)
        val ruH = if (!hasRu) 0 else measure(russian!!, ru, FLASHCARD_RU_LINE_MULT, width)
        val gapH = if (!hasRu) 0 else dpToPx(gap)
        return dpToPx(top) + enH + gapH + ruH
    }

    fun fits(en: Int, ru: Int, top: Int, gap: Int, hPad: Int): Boolean =
        totalHeight(en, ru, top, gap, hPad) <= budgetPx

    // Fast path: user sizes + paddings already fit.
    run {
        val ru = minOf(desiredRuCap, ruFor(desiredEn))
            .coerceAtMost((desiredEn - 1).coerceAtLeast(ABS_MIN_RU_SP))
        if (fits(desiredEn, ru, desiredTop, desiredGap, DEFAULT_H_PAD_DP)) {
            return FlashcardResolvedLayout(
                englishSp = desiredEn,
                russianSp = ru,
                englishTopDp = desiredTop,
                enRuGapDp = if (hasRu) desiredGap else 0,
                horizontalPaddingDp = DEFAULT_H_PAD_DP,
                fits = true,
            )
        }
    }

    /**
     * For a fixed English size, shrink padding / Russian until the card fits (or floors hit).
     * Returns the tightest fitting combo, or the absolute-minimum attempt if nothing fits.
     */
    fun packForEnglish(en: Int): FlashcardResolvedLayout {
        var topDp = desiredTop
        var gapDp = desiredGap
        var hPadDp = DEFAULT_H_PAD_DP
        var ruSp = minOf(desiredRuCap, ruFor(en))
            .coerceAtMost((en - 1).coerceAtLeast(ABS_MIN_RU_SP))

        fun currentFits() = fits(en, ruSp, topDp, gapDp, hPadDp)

        if (currentFits()) {
            return FlashcardResolvedLayout(
                englishSp = en,
                russianSp = ruSp,
                englishTopDp = topDp,
                enRuGapDp = if (hasRu) gapDp else 0,
                horizontalPaddingDp = hPadDp,
                fits = true,
            )
        }

        var attempts = 0
        while (!currentFits() && attempts < 400) {
            attempts++
            when {
                topDp > MIN_TOP_DP -> topDp = max(MIN_TOP_DP, (topDp * 0.7f).roundToInt())
                gapDp > MIN_GAP_DP -> gapDp = max(MIN_GAP_DP, (gapDp * 0.7f).roundToInt())
                hPadDp > MIN_H_PAD_DP -> hPadDp -= 2
                hasRu && ruSp > MIN_RU_SP -> ruSp--
                hPadDp > ABS_MIN_H_PAD_DP -> hPadDp--
                gapDp > ABS_MIN_GAP_DP -> gapDp--
                topDp > 0 -> topDp--
                hasRu && ruSp > ABS_MIN_RU_SP -> ruSp--
                else -> break
            }
        }

        if (ruSp >= en) ruSp = (en - 1).coerceAtLeast(ABS_MIN_RU_SP)

        return FlashcardResolvedLayout(
            englishSp = en,
            russianSp = ruSp,
            englishTopDp = topDp,
            enRuGapDp = if (hasRu) gapDp else 0,
            horizontalPaddingDp = hPadDp,
            fits = currentFits(),
        )
    }

    // Largest English size that can be packed into the budget (binary search).
    var lo = ABS_MIN_EN_SP
    var hi = desiredEn
    var best = packForEnglish(ABS_MIN_EN_SP)
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val candidate = packForEnglish(mid)
        if (candidate.fits) {
            best = candidate
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }

    if (best.fits) return best

    // Last resort: absolute floors (may still overflow → caller can scroll).
    return packForEnglish(ABS_MIN_EN_SP)
}
