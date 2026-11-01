package cloud.nalet.chino.tv.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** The dim behind a menu or a question over the page. */
val ScrimColor = Color(0xCC000000)

/** The dim behind a menu or a question over the page, over all of it: to
 *  the screen's edges, past the safe area too where it sits in one - over
 *  the player, a trailer, Zap. */
fun Modifier.scrim(): Modifier = paintToScreenEdges(ScrimColor)
