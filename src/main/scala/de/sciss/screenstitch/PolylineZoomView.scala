/*
 * PolylineZoomView.scala
 * (ScreenStitch)
 *
 * Copyright (C) 2009-2020 Hanns Holger Rutz. All rights reserved.
 *
 * Published under the GNU Lesser General Public License (LGPL) v3
 */

package de.sciss.screenstitch

import java.awt.geom.{Point2D, Rectangle2D}
import java.awt.{Dimension, Graphics, Graphics2D}

class PolylineZoomView
  extends PolylineView
  with Zoomable {

  def screenSizeUpdated(d: Dimension): Unit = {
    setPreferredSize(d)
    setSize(d)
    revalidate()
  }

  override def paintComponent(g: Graphics): Unit = {
    val g2 = g.asInstanceOf[Graphics2D]
    val cw = virtualRect.width
    val ch = virtualRect.height
    val atOrig = g2.getTransform

    g2.scale(zoom, zoom)
    g2.translate(-clipLeftPx, -clipTopPx)
    paintKnob(g2, cw, ch)
    g2.setTransform(atOrig)
  }

  override protected def screenToVirtual(pt: Point2D): Point2D =
    new Point2D.Double(pt.getX / zoom + clipLeftPx, pt.getY / zoom + clipTopPx)

  override protected def screenToVirtual(r: Rectangle2D): Rectangle2D =
    new Rectangle2D.Double(r.getX / zoom + clipLeftPx, r.getY / zoom + clipTopPx, r.getWidth / zoom, r.getHeight / zoom)
}
