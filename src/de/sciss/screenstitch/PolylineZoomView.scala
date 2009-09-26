/**
 * 	PolylineZoomView.scala
 * 	(ScreenStitch)
 * 
 * 	(C)opyright 2009 Hanns Holger Rutz. All rights reserved.
 * 
 *	Published under the GNU Lesser General Public License (LGPL) v3
 */
package de.sciss.screenstitch

import _root_.java.awt.{ Dimension, Graphics, Graphics2D }
import _root_.java.awt.geom.{ Point2D, Rectangle2D }

class PolylineZoomView
extends PolylineView
with Zoomable {
	def screenSizeUpdated( d: Dimension ) {
		setPreferredSize( d )
		setSize( d )
		revalidate()
	}

	override def paintComponent( g: Graphics ) {
		val g2		= g.asInstanceOf[ Graphics2D ]
		val ins		= getInsets()
//		val cw		= getWidth() - ins.left - ins.right
//		val ch		= getHeight() - ins.top - ins.bottom
		val cw		= virtualRect.width
		val ch		= virtualRect.height
		val atOrig	= g2.getTransform()

//		g2.translate( ins.left, ins.top )
		g2.scale( zoom, zoom )
		paintKnob( g2, cw, ch )
		g2.setTransform( atOrig )
	}
	
	override protected def screenToVirtual( pt: Point2D ) : Point2D = {
		new Point2D.Double( pt.getX / zoom, pt.getY / zoom )
	}
	
	override protected def screenToVirtual( r: Rectangle2D ) : Rectangle2D = {
		new Rectangle2D.Double( r.getX / zoom, r.getY / zoom, r.getWidth / zoom, r.getHeight / zoom )
	}
}
