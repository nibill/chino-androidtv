package cloud.nalet.chino.tv.ui.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Looper
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.view.View
import android.view.accessibility.CaptioningManager
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.text.LanguageFeatureSpan
import androidx.media3.common.text.RubySpan
import androidx.media3.common.text.TextAnnotation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

/*
 * How a subtitle looks: one look for every title, set here and nowhere else
 * (the design system's subtitles pattern). SubtitleStyleTest checks the
 * values and the layout.
 *
 * White text with a black outline and nothing behind it - no box, no window -
 * in the system's sans-serif, which has the letters of every language
 * (Chinese, Japanese and Korean included). Its size follows the picture:
 * 5.33 % of the picture's height, Media3's own default. Centred at the
 * bottom, the lines wrap within 80 % of the picture's width, inside the TV's
 * safe area (48 dp left and right, 27 dp at the bottom), so no line is cut at
 * an edge. What a file brings - its colours, boxes, fonts, sizes and places -
 * is ignored, but for its italics (a voice off screen, a song, a thought) and
 * its ruby (the readings above Japanese and Chinese characters), which mean
 * something. While the player's controls show, the line moves up above them,
 * and back down when they hide, at the controls' own pace. While one of the
 * player's menus is open beside it - subtitles, audio, quality, speed, at
 * the bottom-right above the controls - the line moves out of its way: it is
 * centred in the part of the picture left of the menu, within 80 % of that,
 * or, where that part is too narrow for a line (a narrow picture, a wide
 * menu), it goes above the menu - so the menu never hides the line it
 * changes. Where the viewer
 * has switched Android's caption settings on (accessibility), their style and
 * their text size win, box included.
 *
 * The subtitles are drawn by one view, the PlayerView's own SubtitleView,
 * from the one text renderer the player has: one subtitle at a time.
 */

/** The one look of a subtitle, and where it sits. */
object SubtitleLook {
    /** White text, a black outline, no background and no window; the
     *  system's sans-serif. */
    val captionStyle: CaptionStyleCompat
        get() = CaptionStyleCompat(
            /* foregroundColor = */ Color.WHITE,
            /* backgroundColor = */ Color.TRANSPARENT,
            /* windowColor = */ Color.TRANSPARENT,
            /* edgeType = */ CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            /* edgeColor = */ Color.BLACK,
            /* typeface = */ Typeface.SANS_SERIF,
        )

    /** The text's size as a share of the picture's height: Media3's default,
     *  5.33 %. */
    const val TEXT_SIZE_FRACTION = SubtitleView.DEFAULT_TEXT_SIZE_FRACTION

    /** The width the lines wrap within, as a share of the picture's width,
     *  centred: 80 %. */
    const val LINE_WIDTH_FRACTION = 0.8f

    /** How far above the picture's bottom edge the last line ends, as a share
     *  of the picture's height: Media3's default, 8 %. Raised where the
     *  picture reaches into the safe area by more ([subtitleBottomFraction]). */
    const val BOTTOM_FRACTION = SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION

    /** The TV's safe area, left and right. */
    const val SAFE_SIDE_DP = 48f

    /** The TV's safe area, at the bottom. */
    const val SAFE_BOTTOM_DP = 27f

    /** How far above the player's controls the last line ends while they show. */
    const val ABOVE_CONTROLS_DP = 12f

    /** How far left of an open menu the line's box ends, and how far above
     *  it the line ends where it goes above it. */
    const val BESIDE_MENU_DP = 12f

    /** Beside an open menu the line's box is at least this many times the
     *  text's size wide - a line of some 16 letters; where the menu leaves
     *  less of the picture, the line goes above the menu instead. */
    const val MIN_BESIDE_EMS = 10f

    /** A ruby reading's size, as a share of the text's. */
    const val RUBY_SIZE = 0.5f
}

/**
 * Where the last line of a subtitle ends, as a share of the picture's height
 * above its bottom edge: [SubtitleLook.BOTTOM_FRACTION], or more where the
 * picture's bottom edge lies inside the safe area so that 8 % would not clear
 * it. [pictureBottom] is the picture's bottom edge and [screenHeight] the
 * screen's, in pixels; [safeBottom] the safe area in pixels. While the
 * player's controls show, the line ends [SubtitleLook.ABOVE_CONTROLS_DP] above
 * their top edge [controlsTop] (screen pixels, [aboveControls] the gap in
 * pixels) where that is higher: by [lift] of the way, 0 with the controls
 * hidden, 1 with them shown, as they come and go.
 *
 * The lines need no such care at the sides: centred within 80 % of the
 * picture's width, they keep a tenth of the picture's width clear on either
 * side, more than the safe area's 5 % of the screen's for any picture at
 * least half as wide as the screen; a narrower one is centred, far from the
 * edges.
 */
fun subtitleBottomFraction(
    pictureHeight: Int,
    pictureBottom: Int,
    screenHeight: Int,
    safeBottom: Float,
    controlsTop: Float? = null,
    aboveControls: Float = 0f,
    lift: Float = 0f,
): Float {
    if (pictureHeight <= 0) return SubtitleLook.BOTTOM_FRACTION
    val below = (screenHeight - pictureBottom).coerceAtLeast(0)
    val inside = (safeBottom - below).coerceAtLeast(0f)
    val resting = maxOf(SubtitleLook.BOTTOM_FRACTION, inside / pictureHeight)
    if (controlsTop == null || lift <= 0f) return resting
    val clear = (pictureBottom - controlsTop + aboveControls) / pictureHeight
    val raised = clear.coerceAtMost(1f - SubtitleLook.TEXT_SIZE_FRACTION)
    if (raised <= resting) return resting
    return resting + (raised - resting) * lift.coerceAtMost(1f)
}

/**
 * How far before the picture's right edge the subtitle's box ends, in
 * pixels, so its line clears an open menu of the player: the menu's left
 * edge [menuLeft] (window pixels; null while none is open) and [gap] before
 * it, from the picture that starts at [pictureLeft] and is [pictureWidth]
 * wide and [pictureHeight] high, by [aside] of the way, 0 with no menu, 1
 * with one open, as it comes and goes. The whole clearance, however wide the
 * menu. 0 where the menu lies past the picture, and where the line goes
 * above the menu instead ([subtitleAboveMenu]).
 */
fun subtitleEndInset(
    pictureLeft: Int,
    pictureWidth: Int,
    pictureHeight: Int,
    menuLeft: Float?,
    gap: Float,
    aside: Float = 1f,
): Int {
    if (menuLeft == null || pictureWidth <= 0 || aside <= 0f) return 0
    if (!roomBesideMenu(pictureLeft, pictureHeight, menuLeft, gap)) return 0
    val inset = (pictureLeft + pictureWidth - menuLeft + gap).coerceIn(0f, pictureWidth.toFloat())
    return kotlin.math.round(inset * aside.coerceAtMost(1f)).toInt()
}

/**
 * True where the line goes above an open menu of the player rather than
 * beside it: the picture reaches the menu's left edge [menuLeft] (window
 * pixels, [gap] before it), and left of it is less than
 * [SubtitleLook.MIN_BESIDE_EMS] times the text's size, a share of the
 * picture's height - a narrow picture, a wide menu. Its last line then ends
 * [gap] above the menu's top ([subtitleBottomFraction], with the menu's top
 * for the controls').
 */
fun subtitleAboveMenu(pictureLeft: Int, pictureWidth: Int, pictureHeight: Int, menuLeft: Float, gap: Float): Boolean =
    pictureWidth > 0 && pictureLeft + pictureWidth - menuLeft + gap > 0f &&
        !roomBesideMenu(pictureLeft, pictureHeight, menuLeft, gap)

/** Whether the picture left of a menu holds a line beside it. */
private fun roomBesideMenu(pictureLeft: Int, pictureHeight: Int, menuLeft: Float, gap: Float): Boolean =
    menuLeft - gap - pictureLeft >= SubtitleLook.MIN_BESIDE_EMS * SubtitleLook.TEXT_SIZE_FRACTION * pictureHeight

/**
 * The cues as the look draws them. The text ones become one subtitle,
 * centred at the bottom within [SubtitleLook.LINE_WIDTH_FRACTION] of the
 * picture's width: what is shown at once, top to bottom as the file placed
 * it, each cue on lines of its own. The places a file gives its cues (a line
 * at the top, a position at the side, an alignment, vertical text), its box
 * and its text sizes go, and of its styles only italics and the language's
 * own spans stay ([keepItalicsAndRuby]); a ruby reading is drawn above its
 * characters. Picture cues (PGS) keep their own place: a picture cannot be
 * re-flowed. They keep their own shape too ([inItsShape]).
 */
fun layOutCues(cues: List<Cue>): List<Cue> {
    val pictures = cues.filter { it.bitmap != null }.map(::inItsShape)
    val texts = cues.filter { it.bitmap == null && !it.text.isNullOrBlank() }
    if (texts.isEmpty()) return pictures
    val text = SpannableStringBuilder()
    texts.withIndex()
        .sortedWith(compareBy<IndexedValue<Cue>> { heightOnScreen(it.value) }.thenBy { it.index })
        .forEachIndexed { i, (_, cue) ->
            if (i > 0) text.append('\n')
            text.append(cue.text)
        }
    keepItalicsAndRuby(text)
    val subtitle = Cue.Builder()
        .setText(text)
        .setTextAlignment(Layout.Alignment.ALIGN_CENTER)
        .setPosition(0.5f)
        .setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
        .setSize(SubtitleLook.LINE_WIDTH_FRACTION)
        .build()
    return pictures + subtitle
}

/** How far from 16:9 a picture subtitle's plane may be and still be in an HD
 *  picture's shape. */
private const val HD_PLANE_TOLERANCE = 0.02f

/**
 * A picture subtitle (PGS) in its own shape. Media3 draws a picture over a
 * share of the view's width and a share of its height, the shares of the
 * plane it was made on - stretched wherever the box it is drawn in is of
 * another shape than that plane: a film cropped to 2.39:1 that kept its
 * Blu-ray's subtitles, made on a 16:9 plane, gets them squashed, and so does
 * every picture while the line moves left of an open menu ([SubtitlePlacement]
 * narrows the box). A plane in an HD picture's shape (16:9, square pixels)
 * keeps the picture's own shape instead: as wide as Media3 draws it, as high
 * as that makes it, and one in the lower half of its plane hangs from its
 * bottom edge there, so the frame never cuts it off. Where the box is of the
 * plane's shape this is exactly Media3's drawing. A plane of another shape
 * (a DVD's or a broadcast's, whose pixels are not square) is drawn as Media3
 * draws it.
 */
fun inItsShape(cue: Cue): Cue {
    val bitmap = cue.bitmap ?: return cue
    if (cue.size <= 0f || cue.bitmapHeight <= 0f || bitmap.width <= 0 || bitmap.height <= 0) return cue
    if (cue.lineType != Cue.LINE_TYPE_FRACTION || cue.lineAnchor != Cue.ANCHOR_TYPE_START || cue.line == Cue.DIMEN_UNSET) return cue
    val plane = (bitmap.width / cue.size) / (bitmap.height / cue.bitmapHeight)
    if (kotlin.math.abs(plane - 16f / 9f) > 16f / 9f * HD_PLANE_TOLERANCE) return cue
    val shaped = cue.buildUpon().setBitmapHeight(Cue.DIMEN_UNSET)
    if (cue.line + cue.bitmapHeight / 2f > 0.5f) {
        shaped.setLine(cue.line + cue.bitmapHeight, Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_END)
    }
    return shaped.build()
}

/**
 * Drops every style a file gives its text but italics and the spans of the
 * language itself (ruby, emphasis marks): bold, colours, backgrounds, sizes,
 * fonts, underlines. A bold italic stays italic. Each ruby reading gets a
 * [RubyReading] to draw it: Media3's own drawing shows the characters
 * without it.
 */
internal fun keepItalicsAndRuby(text: Spannable) {
    for (span in text.getSpans(0, text.length, Any::class.java)) {
        if (span is RubyReading) continue
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        val flags = text.getSpanFlags(span)
        when {
            span is StyleSpan && span.style == Typeface.ITALIC -> Unit
            span is StyleSpan && span.style == Typeface.BOLD_ITALIC -> {
                text.removeSpan(span)
                text.setSpan(StyleSpan(Typeface.ITALIC), start, end, flags)
            }
            span is RubySpan -> text.setSpan(RubyReading(span.rubyText, under = span.position == TextAnnotation.POSITION_AFTER), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span is LanguageFeatureSpan -> Unit
            else -> text.removeSpan(span)
        }
    }
}

/**
 * A ruby reading drawn with its characters: the reading at half their size,
 * centred over them (under them where the file puts it after), in the
 * subtitle's own paint, so the outline goes round it as round the text. The
 * line grows by the reading's height.
 */
class RubyReading(val reading: String, val under: Boolean) : ReplacementSpan() {
    private fun small(paint: Paint) = TextPaint(paint).apply { textSize = paint.textSize * SubtitleLook.RUBY_SIZE }

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val small = small(paint)
        if (fm != null) {
            paint.getFontMetricsInt(fm)
            val extra = kotlin.math.ceil(small.descent() - small.ascent()).toInt()
            if (under) {
                fm.descent += extra
                fm.bottom += extra
            } else {
                fm.ascent -= extra
                fm.top -= extra
            }
        }
        return kotlin.math.ceil(maxOf(paint.measureText(text, start, end), small.measureText(reading))).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val small = small(paint)
        val base = paint.measureText(text, start, end)
        val above = small.measureText(reading)
        val width = maxOf(base, above)
        canvas.drawText(text, start, end, x + (width - base) / 2f, y.toFloat(), paint)
        val readingY = if (under) y + paint.descent() - small.ascent() else y + paint.ascent() - small.descent()
        canvas.drawText(reading, x + (width - above) / 2f, readingY, small)
    }
}

/** How high a file placed a cue, from 0 (the top) to 1 (the bottom): what
 *  orders two cues shown at once. One with no line is at the bottom. */
private fun heightOnScreen(cue: Cue): Float {
    if (cue.line == Cue.DIMEN_UNSET) return 1f
    return when (cue.lineType) {
        Cue.LINE_TYPE_FRACTION -> cue.line.coerceIn(0f, 1f)
        Cue.LINE_TYPE_NUMBER -> if (cue.line >= 0) (cue.line / 15f).coerceIn(0f, 1f) else (1f + (cue.line + 1f) / 15f).coerceIn(0f, 1f)
        else -> 1f
    }
}

/** What a text renderer hands on, laid out by [layOutCues]: every cue the
 *  player shows passes here. */
class LaidOutCues(private val output: TextOutput) : TextOutput {
    @Deprecated("Use onCues(CueGroup)")
    @Suppress("DEPRECATION")
    override fun onCues(cues: List<Cue>) = output.onCues(layOutCues(cues))

    override fun onCues(cueGroup: CueGroup) = output.onCues(CueGroup(layOutCues(cueGroup.cues), cueGroup.presentationTimeUs))
}

/**
 * The player's renderers: Media3's, with one text renderer whose cues are
 * laid out by [layOutCues]. It decodes the side-loaded subtitles as they
 * come (legacy decoding): Media3 fetches a side-loaded subtitle only once it
 * is on when it decodes it here, rather than parsing every one as the
 * playback starts. Its decoders are Media3's, but for PGS: a .sup sidecar is
 * read by SupParser, its display sets drawn as each is shown
 * ([SubtitleDecoders], SupSubtitles.kt).
 */
class StreamRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        out += TextRenderer(LaidOutCues(output), outputLooper, SubtitleDecoders()).apply { experimentalSetLegacyDecodingEnabled(true) }
    }
}

/** Android's caption settings where the viewer has switched them on
 *  (accessibility), else null. */
private fun viewerCaptions(context: Context): CaptioningManager? =
    (context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager)?.takeIf { it.isEnabled }

/**
 * Where a picture cue's bottom edge is drawn, in pixels from the top of a box
 * [width] x [height], as Media3's SubtitlePainter places a bitmap; null for a
 * text cue.
 */
internal fun pictureBottom(cue: Cue, width: Int, height: Int): Float? {
    val bitmap = cue.bitmap ?: return null
    if (width <= 0 || height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return null
    if (cue.line == Cue.DIMEN_UNSET || cue.lineType != Cue.LINE_TYPE_FRACTION || cue.size == Cue.DIMEN_UNSET) return null
    val drawnHeight = if (cue.bitmapHeight != Cue.DIMEN_UNSET) {
        height * cue.bitmapHeight
    } else {
        width * cue.size * bitmap.height / bitmap.width
    }
    val anchor = height * cue.line
    return when (cue.lineAnchor) {
        Cue.ANCHOR_TYPE_END -> anchor
        Cue.ANCHOR_TYPE_MIDDLE -> anchor + drawnHeight / 2f
        else -> anchor + drawnHeight
    }
}

/**
 * How far the subtitle must rise, in pixels, so that what it shows - [shown],
 * the cues on screen or the last ones - ends [gap] above [edge] (window
 * pixels): the controls' or a menu's top. A line of text ends at [textBottom]
 * (window pixels), a picture at its own bottom edge in the box [width] x
 * [height] whose top is at [boxTop] ([pictureBottom]). Nothing shown yet: as a
 * line of text would.
 */
internal fun subtitleClearance(shown: List<Cue>, width: Int, height: Int, boxTop: Float, textBottom: Float, edge: Float, gap: Float): Float {
    var lowest = if (shown.isEmpty() || shown.any { it.bitmap == null }) textBottom else Float.NEGATIVE_INFINITY
    for (cue in shown) pictureBottom(cue, width, height)?.let { lowest = maxOf(lowest, boxTop + it) }
    if (lowest == Float.NEGATIVE_INFINITY) return 0f
    return (lowest - (edge - gap)).coerceAtLeast(0f)
}

/**
 * Where a subtitle sits: above the bottom of the picture and the safe area,
 * and above the player's controls while they show ([subtitleBottomFraction]);
 * left of a menu of the player while one is open ([subtitleEndInset]), or
 * above it where left of it is too narrow for a line ([subtitleAboveMenu]).
 * The player tells it where the controls' top edge is and how far they have
 * come in, where an open menu's left and top edges are, and what is on
 * screen ([cues]). Over the safe area the line rests at its bottom margin;
 * above the controls or a menu the whole view rises - a picture subtitle
 * (PGS), which Media3 keeps where its plane puts it, by its own bottom edge,
 * as text does by its last line's.
 */
class SubtitlePlacement internal constructor(private val view: SubtitleView) {
    private var bottom = SubtitleLook.BOTTOM_FRACTION
    private var resting = SubtitleLook.BOTTOM_FRACTION
    private var end = 0

    /** Where the last line ends now, as a share of the picture's height
     *  above its bottom edge. */
    internal val bottomFraction: Float get() = bottom

    /** The cues on screen, as laid out ([layOutCues]). While none are, the
     *  rise goes by the last ones. */
    var cues: List<Cue> = emptyList()
        set(value) {
            field = value
            if (value.isNotEmpty()) shown = value
            update()
        }

    private var shown: List<Cue> = emptyList()

    /** The left edge of the player's open menu, in window pixels; null while
     *  none is open. */
    var menuLeft: Float? = null
        set(value) {
            field = value
            update()
        }

    /** The top edge of the player's open menu, in window pixels; null while
     *  none is open. */
    var menuTop: Float? = null
        set(value) {
            field = value
            update()
        }

    /** How far the line has moved out of a menu's way: 0 none open, 1 one
     *  open. */
    var aside: Float = 0f
        set(value) {
            field = value
            update()
        }

    /** The top edge of the player's controls, in window pixels; null before
     *  they have been laid out. */
    var controlsTop: Float? = null
        set(value) {
            field = value
            update()
        }

    /** How far the controls have come in: 0 hidden, 1 shown. */
    var lift: Float = 0f
        set(value) {
            field = value
            update()
        }

    internal fun update() {
        val at = IntArray(2)
        view.getLocationInWindow(at)
        // Where the view is laid out, its own rise left out: from where its
        // parent is.
        val viewTop = (view.parent as? View)?.let { parent ->
            IntArray(2).also(parent::getLocationInWindow)[1] + view.top.toFloat()
        } ?: view.top.toFloat()
        val density = view.resources.displayMetrics.density
        val gap = SubtitleLook.BESIDE_MENU_DP * density
        val aboveControls = SubtitleLook.ABOVE_CONTROLS_DP * density
        // The line's end, clear above edge, by share of the way.
        fun above(edge: Float?, clear: Float, share: Float) = subtitleBottomFraction(
            pictureHeight = view.height,
            pictureBottom = (viewTop + view.height).toInt(),
            screenHeight = view.rootView.height,
            safeBottom = SubtitleLook.SAFE_BOTTOM_DP * density,
            controlsTop = edge,
            aboveControls = clear,
            lift = share,
        )
        val left = menuLeft
        val top = menuTop
        // Above an open menu where left of it is too narrow for a line: its
        // last line ends above the menu's top as above the controls' -
        // whichever is higher.
        val aboveMenu = left != null && top != null && subtitleAboveMenu(at[0], view.width, view.height, left, gap)
        val rest = above(null, 0f, 0f)
        val fraction = maxOf(
            above(controlsTop, aboveControls, lift),
            if (aboveMenu) above(top, gap, aside) else 0f,
        )
        // Beside an open menu: the box the line is centred in ends left of
        // it - a padding of the view at its right, which leaves the text's
        // size, a share of the picture's height, as it is.
        val inset = subtitleEndInset(at[0], view.width, view.height, left, gap, aside)
        if (inset != end) {
            end = inset
            view.setPadding(view.paddingLeft, view.paddingTop, inset, view.paddingBottom)
        }
        // The rise above the controls or the menu: a line of text's, or a
        // picture's by its own bottom edge - in the box as it is now.
        var rise = (fraction - rest) * view.height
        if (shown.any { it.bitmap != null }) {
            val boxWidth = view.width - view.paddingLeft - view.paddingRight
            val boxHeight = view.height - view.paddingTop - view.paddingBottom
            val boxTop = viewTop + view.paddingTop
            val textBottom = viewTop + view.height * (1f - rest)
            fun clear(edge: Float?, clearance: Float, share: Float): Float =
                if (edge == null || share <= 0f) {
                    0f
                } else {
                    subtitleClearance(shown, boxWidth, boxHeight, boxTop, textBottom, edge, clearance) * share.coerceAtMost(1f)
                }
            rise = maxOf(clear(controlsTop, aboveControls, lift), if (aboveMenu) clear(top, gap, aside) else 0f)
                .coerceAtMost(view.height * (1f - SubtitleLook.TEXT_SIZE_FRACTION))
        }
        // Set only when it changed: each set asks for a new drawing.
        if (rest != resting) {
            resting = rest
            view.setBottomPaddingFraction(rest)
        }
        if (view.translationY != -rise) view.translationY = -rise
        bottom = if (view.height > 0) rest + rise / view.height else rest
    }
}

/**
 * Gives [view] the look: embedded sizes off, the file's styles but italics
 * and ruby dropped as the cues are laid out ([layOutCues]), and
 * [SubtitleLook]'s style and size - or, where Android's caption settings are
 * on, the viewer's style and their text size (their scale of our 5.33 %).
 * Again whenever those settings change, while the view is attached. The
 * bottom margin follows the picture's place on the screen and the player's
 * controls: the [SubtitlePlacement] it returns.
 */
fun SubtitleView.applySubtitleLook(): SubtitlePlacement {
    // Media3's switch for embedded styles would drop italics with the rest:
    // layOutCues keeps them, and only them and ruby.
    setApplyEmbeddedStyles(true)
    setApplyEmbeddedFontSizes(false)
    applyCaptionSettings()
    val captioning = context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
    val listener = object : CaptioningManager.CaptioningChangeListener() {
        override fun onEnabledChanged(enabled: Boolean) = applyCaptionSettings()
        override fun onUserStyleChanged(userStyle: CaptioningManager.CaptionStyle) = applyCaptionSettings()
        override fun onFontScaleChanged(fontScale: Float) = applyCaptionSettings()
    }
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            captioning?.addCaptioningChangeListener(listener)
            applyCaptionSettings()
        }

        override fun onViewDetachedFromWindow(v: View) {
            captioning?.removeCaptioningChangeListener(listener)
        }
    })
    if (isAttachedToWindow) captioning?.addCaptioningChangeListener(listener)
    // The bottom margin set last: a layout pass sets it only when it changed.
    val placement = SubtitlePlacement(this)
    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> placement.update() }
    return placement
}

/** The style and size: the viewer's where Android's caption settings are on,
 *  else ours. */
private fun SubtitleView.applyCaptionSettings() {
    val own = viewerCaptions(context)
    setStyle(own?.let { CaptionStyleCompat.createFromCaptionStyle(it.userStyle) } ?: SubtitleLook.captionStyle)
    setFractionalTextSize(SubtitleLook.TEXT_SIZE_FRACTION * (own?.fontScale ?: 1f))
}
