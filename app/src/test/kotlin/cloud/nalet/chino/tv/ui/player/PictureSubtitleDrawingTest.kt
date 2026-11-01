package cloud.nalet.chino.tv.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.media3.common.text.Cue
import androidx.media3.ui.SubtitleView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A PGS picture drawn by the player's subtitle view, in the look
 * (layOutCues, applySubtitleLook): where its plane puts it, as large as the
 * plane makes it, on a picture of the plane's shape and on a film cropped to
 * 2.40:1 that kept its 16:9 plane - and in its own shape while the line
 * moves left of an open menu. Media3's own drawing, under Robolectric's
 * native graphics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PictureSubtitleDrawingTest {
    /** A white box 400 x 40 at (760, 900) of a 1920x1080 plane, as PgsParser places it. */
    private val line: Cue = Cue.Builder()
        .setBitmap(Bitmap.createBitmap(400, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) })
        .setPosition(760f / 1920f)
        .setPositionAnchor(Cue.ANCHOR_TYPE_START)
        .setLine(900f / 1080f, Cue.LINE_TYPE_FRACTION)
        .setLineAnchor(Cue.ANCHOR_TYPE_START)
        .setSize(400f / 1920f)
        .setBitmapHeight(40f / 1080f)
        .build()

    /** Where [cues] are drawn in a subtitle view [width] x [height] with a
     *  menu open from [menuLeft] on: the box of the opaque pixels, left,
     *  top, right, bottom. */
    private fun drawn(cues: List<Cue>, width: Int, height: Int, menuLeft: Float? = null): List<Int> {
        val view = SubtitleView(ApplicationProvider.getApplicationContext())
        val placement = view.applySubtitleLook()
        view.setCues(cues)
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        if (menuLeft != null) {
            placement.menuLeft = menuLeft
            placement.aside = 1f
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
        }
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(frame))
        val pixels = IntArray(width * height).also { frame.getPixels(it, 0, width, 0, 0, width, height) }
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (Color.alpha(pixels[y * width + x]) > 128) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + 1)
                bottom = maxOf(bottom, y + 1)
            }
        }
        return listOf(left, top, right, bottom)
    }

    private fun assertNear(expected: List<Int>, actual: List<Int>) =
        assertTrue("drawn at $actual, not $expected", expected.zip(actual).all { (e, a) -> abs(e - a) <= 1 })

    @Test
    fun `on a picture of its plane's shape it is where the plane puts it`() {
        assertNear(listOf(760, 900, 1160, 940), drawn(layOutCues(listOf(line)), 1920, 1080))
    }

    @Test
    fun `on a cropped film it keeps its shape`() {
        // 1920 x 800: as wide and as high as on the plane, its bottom edge
        // where the plane has it (940 of 1080).
        assertNear(listOf(760, 656, 1160, 696), drawn(layOutCues(listOf(line)), 1920, 800))
        // As Media3 alone draws it: squashed to 30 rows.
        assertNear(listOf(760, 667, 1160, 697), drawn(listOf(line), 1920, 800))
    }

    @Test
    fun `beside an open menu it is smaller, never squashed, and clear of the menu`() {
        val (left, top, right, bottom) = drawn(layOutCues(listOf(line)), 1920, 1080, menuLeft = 1152f)
        assertEquals(10f, (right - left).toFloat() / (bottom - top), 0.5f)
        assertTrue("right edge $right", right <= 1152)
        assertEquals(940f, bottom.toFloat(), 1f)
    }
}
