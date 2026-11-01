package cloud.nalet.chino.tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import cloud.nalet.chino.tv.ui.library.TvSideRail
import cloud.nalet.chino.tv.ui.library.TvTopBar
import cloud.nalet.chino.tv.ui.player.PlayerBottomChrome
import cloud.nalet.chino.tv.ui.player.PlayerChromeOverlay
import cloud.nalet.chino.tv.ui.player.PlayerTopChrome
import cloud.nalet.chino.tv.ui.player.PlayerSafeArea
import cloud.nalet.chino.tv.ui.player.SkipSegmentButton
import cloud.nalet.chino.tv.data.api.Segment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyChild
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Where things sit on the TV's 960 × 540 dp (1920 × 1080 px). The shell has
 * no margin round the screen, as chino-web has none: the rail at the left
 * edge, the top bar at the top edge, their lines and canvas out to the edges,
 * a page's content a gutter in - every focused item's ring whole on the
 * screen. The player's controls stay inside the TV's safe area (the design
 * system's tv focus), 48 dp from the left and right edges and 27 dp from the
 * top and bottom, measured to a ring at its growth, over a picture to the
 * screen's edges.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w960dp-h540dp-xhdpi")
class SafeAreaTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    /** The screen in the TV's dp: 2 px each on the 1920 × 1080 screen. */
    private val safe = Rect(48f, 27f, 960f - 48f, 540f - 27f)

    /** Where [node]'s focus style reaches, in the TV's dp: its edge grown by
     *  [growth], and its ring, 3 dp past a 2 dp gap where it is outside. */
    private fun reach(node: SemanticsNodeInteraction, growth: Float, ringOutside: Boolean): Rect {
        val px = node.fetchSemanticsNode().boundsInRoot
        val b = Rect(px.left / 2f, px.top / 2f, px.right / 2f, px.bottom / 2f)
        val grow = (growth - 1f) / 2f
        val ring = if (ringOutside) 5f * growth else 0f
        return Rect(
            b.left - b.width * grow - ring,
            b.top - b.height * grow - ring,
            b.right + b.width * grow + ring,
            b.bottom + b.height * grow + ring,
        )
    }

    private fun assertInside(what: String, r: Rect) {
        assertTrue("$what reaches $r, past the safe area $safe",
            r.left >= safe.left - 0.01f && r.top >= safe.top - 0.01f &&
                r.right <= safe.right + 0.01f && r.bottom <= safe.bottom + 0.01f)
    }

    private fun control(name: String) =
        assertInside(name, reach(compose.onNode(hasContentDescription(name)), 1.05f, ringOutside = true))

    /** A control of the shell's: its ring at its growth whole on the screen and
     *  inside [within] - the rail, left of its line, or the top bar, above
     *  its line. */
    private fun shellControl(name: String, within: Rect) {
        val r = reach(compose.onNode(hasContentDescription(name)), 1.05f, ringOutside = true)
        assertTrue("$name reaches $r, past $within",
            r.left >= within.left - 0.01f && r.top >= within.top - 0.01f &&
                r.right <= within.right + 0.01f && r.bottom <= within.bottom + 0.01f)
    }

    /** The window as drawn (the screenshot tests' way under Robolectric). */
    private fun window(): android.graphics.Bitmap {
        val view = compose.activity.window.decorView
        val shot = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        compose.runOnIdle { view.draw(android.graphics.Canvas(shot)) }
        return shot
    }

    /** Whether [color] is drawn anywhere in x in [xs], y in [ys]. */
    private fun android.graphics.Bitmap.has(color: Color, xs: IntRange, ys: IntRange): Boolean =
        xs.any { x -> ys.any { y -> getPixel(x, y) == color.toArgb() } }

    private fun dp(px: Rect) = Rect(px.left / 2f, px.top / 2f, px.right / 2f, px.bottom / 2f)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { ChinoTvTheme { content() } }
        compose.waitForIdle()
    }

    @Test
    fun `the shell's rail at the left edge and its top bar at the top edge, as chino-web's, every ring whole on the screen`() {
        show {
            ShellScreen {
                Row(Modifier.fillMaxSize()) {
                    TvSideRail(activeType = null, onHome = {}, onMovies = {}, onSeries = {}, onSettings = {}, onZap = {}, onWatchlist = {})
                    Column(Modifier.weight(1f)) {
                        TvTopBar(activeAccount = null, onAccountClick = {})
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        val shot = window()
        val w = shot.width
        val h = shot.height
        // In px: the rail 120 wide from the left edge, its line at its right
        // edge from the top edge to the bottom edge; the logo cell's and the
        // top bar's line 96 px down, from the left edge to the right edge.
        assertTrue("the rail's line at the top edge", shot.has(ChinoBorder, 114 until 120, 0..1))
        assertTrue("the rail's line at the bottom edge", shot.has(ChinoBorder, 114 until 120, h - 2 until h))
        assertTrue("the logo's line at the left edge", shot.has(ChinoBorder, 0..1, 90 until 97))
        assertTrue("the top bar's line at the right edge", shot.has(ChinoBorder, w - 2 until w, 90 until 97))
        // The rail's items inside it, left of its line; the top bar's controls
        // inside the bar, above its line - every ring at its growth whole.
        val rail = Rect(0f, 0f, 59f, 540f)
        for (name in listOf("Home", "Movies", "Series", "Watchlist", "Zap", "Settings")) shellControl(name, rail)
        shellControl("Account", Rect(60f, 0f, 960f, 47f))
        // The search field a gutter past the rail's line, the account a
        // gutter from the right edge (12 of the TV's dp, chino-web's 16 px).
        assertEquals(72f, dp(compose.onNode(hasText("Search movies, shows…")).fetchSemanticsNode().boundsInRoot).left, 0.5f)
        assertEquals(948f, dp(compose.onNode(hasContentDescription("Account")).fetchSemanticsNode().boundsInRoot).right, 0.5f)
        // The search field, a row: its ring inside its edge, 2 % growth.
        val search = reach(compose.onNode(hasText("Search movies, shows…")), 1.02f, ringOutside = false)
        assertTrue("search reaches $search", search.left >= 60f && search.top >= 0f && search.bottom <= 47f)
    }

    @Test
    fun `Home's hero a gutter from the rail's line, the top bar's and the right edge, as chino-web's hero`() {
        val hero = cloud.nalet.chino.tv.data.model.Item(id = "m1", title = "Quiet Orchard", kind = "movie", year = 2021, rating = 7.4)
        show {
            ShellScreen {
                ChinoPage {
                    cloud.nalet.chino.tv.ui.library.LibraryContent(
                        movies = listOf(hero), series = emptyList(), continueWatching = emptyList(),
                        watchlist = emptySet(), baseUrl = "https://media.test", streamToken = "t",
                        genres = emptyList(), filter = cloud.nalet.chino.tv.ui.library.BrowseFilter(), heroItem = hero, heroCount = 1,
                        onItemSelected = {}, onHeroPlay = {}, onSearch = {}, onSettings = {}, onWatchlist = {},
                        onAccountClick = {}, onMoviesNav = {}, onSeriesNav = {}, noticesBell = null,
                        activeAccount = null, onFilterChange = {}, onZap = {},
                    )
                }
            }
        }
        val playNode = compose.onNode(hasText("play")).fetchSemanticsNode()
        val y = playNode.boundsInRoot.center.y.toInt()
        val shot = window()
        val black = Color.Black.toArgb()
        val canvas = ChinoBg.toArgb()
        // In px: the hero's black from 144 (the rail's 120 and the gutter's
        // 24) to 1896 (the gutter from the right edge), from 120 down (the
        // top bar's 96 and the gutter).
        assertEquals("the hero beside its right gutter", black, shot.getPixel(1893, y))
        assertEquals("the right gutter", canvas, shot.getPixel(1906, y))
        assertEquals("the gutter beside the rail", canvas, shot.getPixel(136, y))
        assertEquals("the hero beside the rail's gutter", black, shot.getPixel(147, y))
        assertEquals("the gutter under the top bar", canvas, shot.getPixel(600, 112))
        assertEquals("the hero under it", black, shot.getPixel(600, 124))
        // Its title and play 48 dp inside it.
        assertEquals(216f, compose.onNode(hasText("Quiet Orchard") and androidx.compose.ui.test.SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsActions.OnClick)).fetchSemanticsNode().boundsInRoot.left, 0.5f)
        val play = playNode.boundsInRoot
        val grown = if (playNode.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Focused) { false }) 1.05f else 1f
        assertEquals(216f, play.center.x - play.width / grown / 2f, 0.5f)
    }

    @Test
    fun `a page's backdrop and its scrim reach the screen's edges, with no margin round the shell`() {
        show {
            ShellScreen {
                ChinoPage {
                    Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Red))
                    Box(Modifier.fillMaxSize().padding(top = 200.dp).scrim())
                }
            }
        }
        val shot = window()
        // The backdrop at the screen's top corners.
        assertEquals(Color.Red.toArgb(), shot.getPixel(2, 2))
        assertEquals(Color.Red.toArgb(), shot.getPixel(shot.width - 3, 2))
        // The scrim at the bottom corners: the canvas dimmed, not the canvas.
        val canvas = ChinoBg.toArgb()
        val corner = shot.getPixel(2, shot.height - 3)
        assertTrue("the scrim reaches the corner", corner != canvas)
        assertEquals(ScrimColor.compositeOverBg().toArgb(), corner, 0x030303)
        assertTrue(shot.getPixel(shot.width - 3, shot.height - 3) != canvas)
    }

    private fun Color.compositeOverBg(): Color = compositeOver(ChinoBg)

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        for (s in listOf(16, 8, 0)) {
            assertTrue("channel ${s / 8}: ${Integer.toHexString(actual)} vs ${Integer.toHexString(expected)}",
                kotlin.math.abs(ch(expected, s) - ch(actual, s)) <= (tolerance and 0xFF))
        }
    }

    @Test
    fun `the player's controls inside the safe area, over a picture to the screen's edges`() {
        show {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                SafeAreaOverlay {
                    PlayerChromeOverlay(
                        visible = true,
                        top = { PlayerTopChrome(title = "Quiet Orchard", onBack = {}, onUserInteraction = {}, onHome = {}) },
                        bottom = { Bar() },
                    )
                }
            }
        }
        for (name in barButtons + listOf("Back", "Home")) control(name)
    }

    /** The control bar of an episode, every button on it. */
    @Composable
    private fun Bar() {
        PlayerBottomChrome(
            isPlaying = true, positionMs = 60_000, durationMs = 600_000, bufferedMs = 120_000,
            segments = emptyList(), trickplayCues = emptyList(), trickplayBaseUrl = "", streamToken = "",
            muted = false, volume = 1f, onPlayPause = {}, onToggleMute = {}, onVolumeChange = {},
            onScrubberSeek = {}, onAudio = {}, onSubtitles = {}, onQuality = {}, onSpeed = {},
            onInfo = {}, onPrevEpisode = {}, onNextEpisode = {},
            playPauseFocusRequester = FocusRequester(), onUserInteraction = {},
        )
    }

    private val barButtons = listOf(
        "Pause", "Volume", "Audio track", "Subtitles", "Playback speed", "Quality",
        "Previous episode", "Next episode", "Playback info",
    )

    @Test
    fun `the skip pill stands above the control bar while it shows, on none of its buttons, and at the bottom when it hides`() {
        var controls by mutableStateOf(true)
        show {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                PlayerSafeArea(
                    controlsVisible = controls,
                    top = { PlayerTopChrome(title = "Quiet Orchard", onBack = {}, onUserInteraction = {}, onHome = {}) },
                    bottom = { Bar() },
                ) {
                    SkipSegmentButton(segment = Segment(kind = "intro", startMs = 0, endMs = 90_000), onSkip = {})
                }
            }
        }
        // The pill: the focusable row around its label.
        val pill = compose.onNode(hasAnyChild(hasText("skip intro")), useUnmergedTree = true)
        val lifted = reach(pill, 1.05f, ringOutside = true)
        assertInside("skip intro", lifted)
        // Its ring at its growth clear of every button's, and of the seek
        // row's time label below it in the corner.
        val covered = (barButtons.map { it to compose.onNode(hasContentDescription(it)) } +
            ("10:00" to compose.onNode(hasText("10:00"))))
            .map { (name, node) -> name to reach(node, 1.05f, ringOutside = true) }
            .filter { (_, r) -> r.overlaps(lifted) }
        assertTrue("skip intro at $lifted on ${covered.map { it.first }}", covered.isEmpty())
        // The bar hidden: back down in the safe area's corner, its ring's room below it.
        controls = false
        compose.waitForIdle()
        val down = pill.fetchSemanticsNode().boundsInRoot
        assertEquals(safe.bottom - 12f, down.bottom / 2f, 0.5f)
        assertEquals(safe.right - 12f, down.right / 2f, 0.5f)
        assertInside("skip intro", reach(pill, 1.05f, ringOutside = true))
    }
}
