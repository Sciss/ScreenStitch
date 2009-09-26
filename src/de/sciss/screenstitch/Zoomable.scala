/**
 * 	Zoomable.scala
 * 	(ScreenStitch)
 * 
 * 	(C)opyright 2009 Hanns Holger Rutz. All rights reserved.
 * 
 *	Published under the GNU Lesser General Public License (LGPL) v3
 */
package de.sciss.screenstitch

import _root_.java.awt.{ Dimension, Point, Rectangle }

trait Zoomable {
	protected var zoom = 1.0f
	protected val virtualRect = new Rectangle( 0, 0, 400, 400 )
	private var slaves: List[ Zoomable ] = Nil

	def setZoom( x: Float ) {
		if( zoom != x ) {
			zoom = x
			updateScreenSize
		}
		slaves.foreach( _.setZoom( x ))
	}
	
	def addSlave( z: Zoomable ) {
		slaves ::= z
	}
	
	def removeSlave( z: Zoomable ) {
		slaves = slaves.diff( List( z )) // ??? terrible
	}
	
	private def updateScreenSize {		
		val scrW = (virtualRect.width * zoom).toInt
		val scrH = (virtualRect.height * zoom).toInt
		val d    = new Dimension( scrW, scrH )
//println( "updateScreenSize : virtual = " + virtualRect + "; screen = " + d )
		screenSizeUpdated( d )
	}

	def setVirtualBounds( x: Int, y: Int, w: Int, h: Int ) {
		if( (virtualRect.x == x) && (virtualRect.y == y) &&
	    	(virtualRect.width == w) && (virtualRect.height == h) ) return
	
	    virtualRect.setBounds( x, y, w, h )
	   	updateScreenSize
	   	
		slaves.foreach( _.setVirtualBounds( x, y, w, h ))
	}

	def screenSizeUpdated( d: Dimension ) : Unit

	def screenToVirtual( scrPt: Point ) =
		new Point( (scrPt.x / zoom).toInt + virtualRect.x,
		           (scrPt.y / zoom).toInt + virtualRect.y )
	
}
