package com.englishtutor.ui.flashcards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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

private const val MIN_EN_SP = 12
private const val ABS_MIN_EN_SP = 10
private const val MIN_RU_SP = 10
private const val ABS_MIN_RU_SP = 9
private const val MIN_TOP_DP = 0
private const val MIN_GAP_DP = 4
private const val ABS_MIN_GAP_DP = 0
private const val DEFAULT_H_PAD_DP = 24
private const val MIN_H_PAD_DP = 8
private const val ABS_MIN_H_PAD_DP = 4

@Composable
fun rememberFlashcardResolvedLayout(
    settings: FlashcardDisplaySettings,
    english: String,
    russian: String?,
    contentWidth: Dp,
    contentHeight: Dp,
    chromeHeight: Dp,
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
                    val size = sp.coerceAtLeast(ABS_MIN_RU_SP)
                    textMeasurer.measure(
                        text = text,
                        style = TextStyle(
                            fontSize = size.sp,
                            lineHeight = (size * lineMult).sp,
                        ),
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
    var topDp = settings.englishTopPaddingDp.coerceIn(0, 160)
    var gapDp = settings.enRuGapDp.coerceIn(0, 120)
    var hPadDp = DEFAULT_H_PAD_DP
    var enSp = settings.englishSp.coerceIn(ABS_MIN_EN_SP, 72)

    fun ruFor(en: Int): Int =
        floor(en * ruRatio).roundToInt().coerceIn(ABS_MIN_RU_SP, (en - 1).coerceAtLeast(ABS_MIN_RU_SP))

    // Start from ratio target, but honor a smaller user-picked Russian size.
    var ruSp = minOf(
        settings.russianSp.coerceAtLeast(ABS_MIN_RU_SP),
        ruFor(enSp),
    ).coerceAtMost((enSp - 1).coerceAtLeast(ABS_MIN_RU_SP))

    fun textWidthPx(): Int = (fullWidthPx - 2 * dpToPx(hPadDp)).coerceAtLeast(1)

    fun totalHeight(en: Int, ru: Int, top: Int, gap: Int, width: Int): Int {
        val enH = measure(english, en, 1.15f, width)
        val ruH = if (russian.isNullOrBlank()) 0 else measure(russian, ru, 1.2f, width)
        val gapH = if (russian.isNullOrBlank()) 0 else dpToPx(gap)
        return dpToPx(top) + enH + gapH + ruH
    }

    fun fits(): Boolean = totalHeight(enSp, ruSp, topDp, gapDp, textWidthPx()) <= maxHeightPx

    if (fits()) {
        return FlashcardResolvedLayout(
            englishSp = enSp,
            russianSp = ruSp,
            englishTopDp = topDp,
            enRuGapDp = if (russian.isNullOrBlank()) 0 else gapDp,
            horizontalPaddingDp = hPadDp,
            fits = true,
        )
    }

    var attempts = 0
    while (!fits() && attempts < 320) {
        attempts++
        when {
            // 1) Compress vertical spacing first — keeps fonts readable longer.
            topDp > MIN_TOP_DP -> topDp = max(MIN_TOP_DP, (topDp * 0.75f).roundToInt())
            gapDp > MIN_GAP_DP -> gapDp = max(MIN_GAP_DP, (gapDp * 0.75f).roundToInt())
            hPadDp > MIN_H_PAD_DP -> hPadDp -= 2

            // 2) Shrink English (+ proportional Russian).
            enSp > MIN_EN_SP -> {
                enSp--
                ruSp = ruFor(enSp)
            }

            // 3) Russian definitions are often the tall part — shrink them further.
            !russian.isNullOrBlank() && ruSp > MIN_RU_SP -> ruSp--

            // 4) Squeeze remaining spacing.
            hPadDp > ABS_MIN_H_PAD_DP -> hPadDp--
            gapDp > ABS_MIN_GAP_DP -> gapDp--
            topDp > 0 -> topDp--

            // 5) Absolute floor for fonts.
            enSp > ABS_MIN_EN_SP -> {
                enSp--
                ruSp = minOf(ruSp, enSp - 1).coerceAtLeast(ABS_MIN_RU_SP)
            }
            !russian.isNullOrBlank() && ruSp > ABS_MIN_RU_SP -> ruSp--
            else -> break
        }
    }

    if (ruSp >= enSp) ruSp = (enSp - 1).coerceAtLeast(ABS_MIN_RU_SP)

    return FlashcardResolvedLayout(
        englishSp = enSp,
        russianSp = ruSp,
        englishTopDp = topDp,
        enRuGapDp = if (russian.isNullOrBlank()) 0 else gapDp,
        horizontalPaddingDp = hPadDp,
        fits = fits(),
    )
}
