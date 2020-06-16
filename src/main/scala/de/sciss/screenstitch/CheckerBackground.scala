/*
 * CheckerBackground.scala
 * (ScreenStitch)
 *
 * Copyright (C) 2009-2020 Hanns Holger Rutz. All rights reserved.
 *
 * Published under the GNU Lesser General Public License (LGPL) v3
 */

package de.sciss.screenstitch

import java.awt.image.BufferedImage
import java.awt.{Dimension, Graphics, Graphics2D, Paint, Rectangle, TexturePaint}
import javax.swing.JComponent

class CheckerBackground(sizeH: Int)
  extends JComponent
  with Zoomable {

  private val pntBg: Paint = {
    val img = new BufferedImage(sizeH << 1, sizeH << 1, BufferedImage.TYPE_INT_ARGB)

    for (x <- 0 until img.getWidth) {
      for (y <- 0 until img.getHeight) {
        img.setRGB(x, y, if (((x / sizeH) ^ (y / sizeH)) == 0) 0xFF9F9F9F else 0xFF7F7F7F)
      }
    }

    new TexturePaint(img, new Rectangle(0, 0, img.getWidth, img.getHeight))
  }

  setOpaque(false)

  def screenSizeUpdated(d: Dimension): Unit = {
    setPreferredSize(d)
    setSize(d)
    revalidate()
  }

  override def paintComponent(g: Graphics): Unit = {
    val g2 = g.asInstanceOf[Graphics2D]
    val atOrig = g2.getTransform
    g2.scale(zoom, zoom)
    g2.translate(-clipLeftPx, -clipTopPx)
    g2.setPaint(pntBg)
    g2.fillRect(0, 0, virtualRect.width, virtualRect.height)
    g2.setTransform(atOrig)
  }
}
