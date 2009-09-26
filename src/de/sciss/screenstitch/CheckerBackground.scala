package de.sciss.screenstitch

import _root_.java.awt.{ Color, Dimension, Graphics, Graphics2D, Paint, Rectangle, TexturePaint }
import _root_.java.awt.image.{ BufferedImage }
import _root_.javax.swing.{ JComponent, JPanel }

class CheckerBackground( sizeH: Int )
extends JComponent
with Zoomable {
	private var pntBg: Paint = _

	// ---- constructor ----
	{
		val img = new BufferedImage( sizeH << 1, sizeH << 1, BufferedImage.TYPE_INT_ARGB )
		
		for( x <- (0 until img.getWidth) ) {
			for( y <- (0 until img.getHeight) ) {
				img.setRGB( x, y, if( ((x / sizeH) ^ (y / sizeH)) == 0 ) 0xFF9F9F9F else 0xFF7F7F7F ) 
			}
		}
		
		pntBg = new TexturePaint( img, new Rectangle( 0, 0, img.getWidth, img.getHeight ))
		setOpaque( false )
	}

	def screenSizeUpdated( d: Dimension ) {
		setPreferredSize( d )
		setSize( d )
		revalidate()
	}
	
	override def paintComponent( g: Graphics ) {
		val g2 = g.asInstanceOf[ Graphics2D ]
        val atOrig	= g2.getTransform
        g2.scale( zoom, zoom )
        g2.setPaint( pntBg )
        g2.fillRect( 0, 0, virtualRect.width, virtualRect.height )
		g2.setTransform( atOrig )
	}
}