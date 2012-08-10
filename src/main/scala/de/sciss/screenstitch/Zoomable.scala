/**
 * 	Zoomable.scala
 * 	(ScreenStitch)
 * 
 * 	(C)opyright 2009 Hanns Holger Rutz. All rights reserved.
 * 
 *	Published under the GNU Lesser General Public License (LGPL) v3
 */
package de.sciss.screenstitch

import java.awt.{ Dimension, Point, Rectangle }

trait Zoomable {
	protected var	zoom		= 1.0f
	private var		clipLeftAmt	= 0f
	protected var	clipLeftPx	= 0
	private var		clipTopAmt	= 0f
	protected var	clipTopPx	= 0
	protected val	virtualRect = new Rectangle( 0, 0, 400, 400 )
	private var		slaves: List[ Zoomable ] = Nil

	def setZoom( x: Float ) {
		if( zoom != x ) {
			zoom = x
			updateScreenSize()
		}
		slaves.foreach( _.setZoom( x ))
	}
	
	// stupid fix for >16384 px problem
	def clipLeft( amount: Float ) {
		if( clipLeftAmt != amount ) {
			clipLeftAmt = amount
			clipLeftPx  = (virtualRect.width * clipLeftAmt).toInt
//println( "amt " + clipLeftAmt + "; px " + clipLeftPx )
			updateScreenSize()
		}
		slaves.foreach( _.clipLeft( amount ))
	}
	// stupid fix for >16384 px problem
	def clipTop( amount: Float ) {
		if( clipTopAmt != amount ) {
			clipTopAmt = amount
			clipTopPx  = (virtualRect.height * clipTopAmt).toInt
			updateScreenSize()
		}
		slaves.foreach( _.clipTop( amount ))
	}
	
	def addSlave( z: Zoomable ) {
		slaves ::= z
	}
	
	def removeSlave( z: Zoomable ) {
		slaves = slaves.diff( List( z )) // ??? terrible
	}
	
	private def updateScreenSize() {
		val scrW = (virtualRect.width * zoom * (1.0f - clipLeftAmt)).toInt
		val scrH = (virtualRect.height * zoom * (1.0f - clipTopAmt)).toInt
		val d    = new Dimension( scrW, scrH )
//println( "updateScreenSize : virtual = " + virtualRect + "; screen = " + d )
		screenSizeUpdated( d )
	}

	def setVirtualBounds( x: Int, y: Int, w: Int, h: Int ) {
		if( (virtualRect.x == x) && (virtualRect.y == y) &&
	    	(virtualRect.width == w) && (virtualRect.height == h) ) return
	
	    virtualRect.setBounds( x, y, w, h )
	    clipLeftPx = (virtualRect.width * clipLeftAmt).toInt
	    clipTopPx  = (virtualRect.width * clipTopAmt).toInt
	   	updateScreenSize()
	   	
		slaves.foreach( _.setVirtualBounds( x, y, w, h ))
	}

	def screenSizeUpdated( d: Dimension ) : Unit

	def screenToVirtual( scrPt: Point ) =
		new Point( (scrPt.x / zoom).toInt + virtualRect.x + clipLeftPx,
		           (scrPt.y / zoom).toInt + virtualRect.y + clipTopPx )
	
}
