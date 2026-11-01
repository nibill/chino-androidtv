package cloud.nalet.chino.tv.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme

/**
 * The shell's layout, as chino-web's (the reference): no margin round the
 * screen. The rail stands at the screen's left edge, from its top edge to its
 * bottom edge, the top bar at its top edge, the logo in the corner between
 * them; a page's content starts a [Gutter] in from the rail's line and the
 * top bar's, and keeps one from the screen's right edge and, where it comes
 * to an end, its bottom edge. Its lists scroll out to the right and bottom
 * edges, and a row runs to the right edge ([runToScreenEnd]).
 *
 * What takes focus keeps the room its ring and growth need from every edge,
 * so no ring is ever cut: the rail's items sit 16 dp inside the rail, the
 * top bar's controls 16 dp inside the bar, a shelf's cards a gutter in with
 * the shelf's room for the ring round them ([shelfRoom] - as wide as the
 * gutter), and a focused item is brought into view clear of a list's edge
 * ([ScrollsClearOfEdges]).
 *
 * The lengths are the TV's own, in this app's dp: the rail 80 wide, the top
 * bar 64 high, as chino-web's are in its px. The pictures that fill the
 * screen - the player, a trailer, Zap - keep the TV's [SafeArea] for their
 * controls instead.
 */
object Shell {
    /** The rail's width, its line at its right edge included (TvSideRail). */
    val RailWidth: Dp = 80.dp

    /** The top bar's height, its line at its bottom included (TvTopBar) -
     *  the logo cell's in the rail too. */
    val TopBarHeight: Dp = 64.dp

    /** Between a page's content and the rail's line, the top bar's line, the
     *  screen's right edge and its bottom edge: chino-web's 16. */
    val Gutter: Dp = 16.dp

    /** Where a page's content starts, beside the rail and below the top bar
     *  a gutter in: a title's page, which has neither, starts its poster and
     *  title there too, where Home's hero starts. */
    val ContentStart: Dp = RailWidth + Gutter
    val ContentTop: Dp = TopBarHeight + Gutter
}

/**
 * The TV's safe area (the design system's tv focus): the part of the picture
 * every TV shows in full - some cut a few percent off its edges - 48 dp from
 * the left and the right edge and 27 dp from the top and the bottom (the TV's
 * dp), measured to a focused item's ring at its growth.
 *
 * It holds where a picture fills the screen: the player, a trailer and Zap
 * put their controls, menus and messages inside it ([SafeAreaOverlay]), while
 * what only paints - the picture, the band behind the controls, a scrim -
 * reaches the screen's edges ([paintToScreenEdges], [scrim]). The shell's
 * pages do not use it: they are laid out edge to edge, as chino-web's
 * ([Shell]).
 */
object SafeArea {
    /** From the left and from the right edge. */
    val Horizontal: Dp = tvDp(48f)

    /** From the top and from the bottom edge. */
    val Vertical: Dp = tvDp(27f)

    /** The safe area's inset from the screen's edges. */
    val Insets: PaddingValues = PaddingValues(horizontal = Horizontal, vertical = Vertical)
}

/**
 * A page of the shell, in [ShellScreen]: its text in fg-2 on the canvas.
 * Not clipped - as a tv-material Surface would be - so a scrim on it covers
 * the whole screen.
 */
@Composable
fun ChinoPage(content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}

/** A screen of the shell: on the canvas, from edge to edge of the screen,
 *  with no margin round it ([Shell]). */
@Composable
fun ShellScreen(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChinoBg),
        content = content,
    )
}

/** What a full-screen picture - the player, Zap - shows over it, inside
 *  the safe area: its controls, menus and messages. */
@Composable
fun SafeAreaOverlay(content: @Composable BoxScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(SafeArea.Insets), content = content)
}

/** Paints [color] behind an item and out past the safe area it sits at the
 *  edge of, to the screen's [start], [top], [end] and [bottom] edges: the
 *  band behind the player's controls. */
fun Modifier.paintToScreenEdges(
    color: Color,
    start: Boolean = true,
    top: Boolean = true,
    end: Boolean = true,
    bottom: Boolean = true,
): Modifier = paintToScreenEdges(SolidColor(color), start, top, end, bottom)

/** Paints [brush] - a gradient over the whole of what it paints - behind an
 *  item and out to the screen's edges, as [paintToScreenEdges] a colour. */
fun Modifier.paintToScreenEdges(
    brush: Brush,
    start: Boolean = true,
    top: Boolean = true,
    end: Boolean = true,
    bottom: Boolean = true,
): Modifier = drawBehind {
    val h = SafeArea.Horizontal.toPx()
    val v = SafeArea.Vertical.toPx()
    val left = if (start) h else 0f
    val above = if (top) v else 0f
    drawRect(
        brush = brush,
        topLeft = Offset(-left, -above),
        size = Size(size.width + left + (if (end) h else 0f), size.height + above + (if (bottom) v else 0f)),
    )
}

/**
 * A scrolling row laid out [by] past its parent's end - the gutter its page
 * keeps there - so its cards run to the screen's edge. Its content padding
 * keeps [by] after its last card, so that one rests where it did, inside the
 * gutter ([shelfPadding]).
 */
fun Modifier.runToScreenEnd(by: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val extra = by.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout((placeable.width - extra).coerceIn(constraints.minWidth, constraints.maxWidth), placeable.height) {
        placeable.place(0, 0)
    }
}

/**
 * Brings a focused item into view in a list no nearer the list's end than
 * [trailing] px nor its start than [leading] px, scrolling as little as that
 * takes: the item a remote moves to, its ring and its growth, stay clear of
 * the screen's edge the list runs to. One that is there already stays where
 * it is. One rule for the item and for its ring's room round it (the focus
 * style asks for both), so the two never pull the list two ways.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class ClearOfEdgesBringIntoView(
    private val leading: Float,
    private val trailing: Float,
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val start = leading
        val end = containerSize - trailing
        return when {
            // Bigger than the room: its start at the start.
            size > end - start -> offset - start
            offset < start -> offset - start
            offset + size > end -> offset + size - end
            else -> 0f
        }
    }
}

/**
 * What scrolls in [content] - the page's own scroll, or a row of it -
 * brings a focused item into view [trailing] clear of its end and
 * [leading] clear of its start ([ClearOfEdgesBringIntoView]): for a page
 * that scrolls to the screen's bottom edge the gutter, for a row that runs
 * to the screen's right edge the gutter and the room a ring needs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollsClearOfEdges(trailing: Dp, leading: Dp = 0.dp, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val spec = remember(density, trailing, leading) {
        with(density) { ClearOfEdgesBringIntoView(leading.toPx(), trailing.toPx()) }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
