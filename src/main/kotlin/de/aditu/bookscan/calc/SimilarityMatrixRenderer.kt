package de.aditu.bookscan.calc

import de.aditu.bookscan.model.Matrix
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.io.File
import java.awt.Font
import java.awt.geom.AffineTransform

/**
 * create image for matrix
 */
@Service
class SimilarityMatrixRenderer {

    companion object {
        private val RECT_SIZE = 10
        private val MARGIN_TOP = 200
        private val MARGIN_LEFT = 300
        private val TEXT_MARGIN = 10
        private val IMAGE_MARGIN_BOTTOM_RIGHT = 100
        private val COLORS  = listOf("ffffe4", "fffee2", "fffee0", "fffddd", "fffddb", "fffcd8", "fffbd5", "fffad2", "fffacf", "fff9cc", "fff8c8", "fff7c5", "fff6c1", "fff4be", "fff3ba", "fff2b6", "fff1b2", "ffefae", "ffeeaa", "ffeca5", "ffeba1", "ffe99c", "ffe798", "ffe593", "ffe38f", "ffe18a", "ffdf86", "ffdd81", "ffdb7c", "ffd878", "ffd673", "ffd36f", "ffd16a", "ffce65", "ffcb61", "ffc95d", "fec658", "fec354", "fec050", "febd4c", "feba48", "feb644", "feb341", "fdb03d", "fdac3a", "fda936", "fda633", "fca230", "fc9f2d", "fb9b2a", "fb9828", "fa9425", "f99123", "f98d20", "f88a1e", "f7861c", "f6831a", "f57f18", "f37c17", "f27815", "f17513", "ef7212", "ed6e11", "eb6b10", "e9680e", "e7650d", "e5610c", "e25e0c", "e05b0b", "dd590a", "da5609", "d75309", "d45008", "d04e08", "cd4b07", "c94907", "c64606", "c24406", "be4206", "ba4006", "b63e05", "b13c05", "ad3a05", "a83805", "a43605", "a03505", "9b3304", "973104", "923004", "8e2f04", "892d04", "852c04", "812b04", "7d2a04", "792904", "752804", "712704", "6d2604", "692504", "662404")
                .map { Color(Integer.parseInt( it,16)) }
    }

    fun render(matrix: Matrix, target: String) {
        val width = matrix.nodes.size * RECT_SIZE + MARGIN_LEFT + IMAGE_MARGIN_BOTTOM_RIGHT
        val height = matrix.nodes.size * RECT_SIZE + MARGIN_TOP + IMAGE_MARGIN_BOTTOM_RIGHT

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setColor(Color(Integer.parseInt( "fafafa",16)))
        g.fillRect(0, 0, width, height)

        // paint matrix
        for ((row, elements) in matrix.m.withIndex()) {
            for ((col, value) in elements.withIndex()) {
                g.setColor(COLORS[value.toInt()])
                g.fillRect(MARGIN_LEFT + (col * RECT_SIZE), MARGIN_TOP + (row * RECT_SIZE), RECT_SIZE, RECT_SIZE)
            }
        }

        // paint left labels
        g.setColor(Color.BLACK)
        val font = Font(null, Font.PLAIN, RECT_SIZE)
        g.setFont(font)
        val metrics = g.getFontMetrics()
        for ((i, node) in matrix.nodes.withIndex()) {
            g.drawString(node, MARGIN_LEFT - (TEXT_MARGIN + metrics.stringWidth(node)), MARGIN_TOP + (i+1) * RECT_SIZE)
        }

        // paint top labels
        val affineTransform = AffineTransform()
        affineTransform.rotate(Math.toRadians(270.0), 0.0, 0.0)
        val rotatedFont = font.deriveFont(affineTransform)
        g.setFont(rotatedFont)
        for ((i, node) in matrix.nodes.withIndex()) {
            g.drawString(node, MARGIN_LEFT + ((i+1) * RECT_SIZE), MARGIN_TOP - TEXT_MARGIN)
        }

        g.dispose()
        ImageIO.write(image, "png", File(target))
    }
}