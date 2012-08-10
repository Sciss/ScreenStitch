/**
 * 	StitchView.scala
 * 	(ScreenStitch)
 * 
 * 	(C)opyright 2009 Hanns Holger Rutz. All rights reserved.
 * 
 *	Published under the GNU Lesser General Public License (LGPL) v3
 */
package de.sciss.screenstitch

import java.awt.{ BasicStroke, Color, Cursor, Dimension, EventQueue, Graphics,
						 Graphics2D, Point, Rectangle, RenderingHints }
import java.awt.event.{ MouseEvent, KeyAdapter, KeyEvent }
import java.awt.geom.GeneralPath
import java.awt.image.BufferedImage
import java.io.{ File, FileOutputStream, IOException, DataInputStream, DataOutputStream }
import java.util.{ Timer, TimerTask }
import javax.imageio.ImageIO
import javax.swing.{ JComponent, JViewport}
import javax.swing.event.MouseInputAdapter

import collection.immutable.{IndexedSeq => IIdxSeq}
import collection.mutable.ListBuffer
import com.itextpdf.text

class StitchView
extends JComponent // JPanel // JComponent
// WARNING: bug in JComponent : when a JComponent is inside a JPanel
// which is the view of a scrollpane's viewport, the display is truncted
// to 16384 pixels!!! for whatever reason, subclassing JPanel instead
// fixes the problem
with Zoomable {
	private var dirty = false
	private val jitter = 32
	private val shpStitch = new GeneralPath()
	private val strkStitch = new BasicStroke( 6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER )
	private var jitPt: IIdxSeq[ Point ] = _	
	
	private var viewPort: Option[ JViewport ] = None

	private var drag: Option[ StitchDrag ] = None
	
	private var alignThread: Option[ Thread ] = None
	
	private var dirtyAction: Option[ Function1[ Boolean, Unit ]] = None
	
	@volatile private var alignKeepRunning = true
	private var beep = 0L
	private var beepColor = Color.white
	
	// doc data
	private val collImg     = ListBuffer[ StitchImage ]()
	private val stitches 	= ListBuffer[ Stitch ]()
	
	// ---- constructor ----
	{
		recalcBounds()
//		setPreferredSize( new Dimension( 800, 800 ))

		shpStitch.moveTo( -30, 30 )
		shpStitch.quadTo( -10, -10, 30, -30 )
		shpStitch.moveTo( -30, -30 )
//		shpStitch.quadTo( 10, -10, 30, 30 )
		shpStitch.lineTo( 30, 30 )
		
		val unsorted = new ListBuffer[ Point ]()
		for( x <- (-jitter to jitter) ) {
			for( y <- (-jitter to jitter) ) {
				unsorted += new Point( x, y )
			}
		}
		jitPt = unsorted.sortWith( (pt1, pt2) => pt1.x*pt1.x + pt1.y*pt1.y < pt2.x*pt2.x + pt2.y*pt2.y ).toIndexedSeq
		
		setTransferHandler( new FileDropHandler( importImage( _ )))
		
		val mia = new MouseInputAdapter {
			
			override def mousePressed( e: MouseEvent ) {
				requestFocus()
				
				if( alignThread.isDefined ) return

				drag = None
				val virtPt = screenToVirtual( e.getPoint )
				val oimg = collImg.find( _.bounds.contains( virtPt ))
				if( oimg.isDefined ) {
					val img1 = oimg.get
					if( e.isAltDown ) {
						removeImage( img1 )
					} else if( e.getClickCount == 2 ) {
						collImg.find( img => (img != img1) && img.bounds.contains( virtPt ))
							.foreach( autoAlign( _, img1 ))
					} else {
						drag = Some( new StitchDrag( img1, e ))
					}
				}
			}
		
			override def mouseReleased( e: MouseEvent ) {
				drag.foreach( d => {
					if( d.started ) {
						recalcBounds()
						repaint()
					}
					drag = None
				})
			}
			
			override def mouseDragged( e: MouseEvent ) {
				drag.foreach( d => {
					if( !d.started ) {
						if( e.getPoint.distance( d.e.getPoint ) < 9 ) return
						d.started = true
						collImg -= d.img
						collImg.prepend( d.img )  // so gets painted on top
					}
					makeDirty()
					d.img.bounds.setLocation(
					    d.origPos.x + ((e.getX - d.e.getX) / zoom).toInt,
					    d.origPos.y + ((e.getY - d.e.getY) / zoom).toInt )
					repaint()
				})
			}
		}
		
		addMouseListener( mia )
		addMouseMotionListener( mia )
		
		addKeyListener( new KeyAdapter {
			override def keyPressed( e: KeyEvent ) {
//				println( "HUHU " + e.getKeyCode )
				if( e.getKeyCode == KeyEvent.VK_ESCAPE ) {
					alignKeepRunning = false
				}
			}
		})
		
		setOpaque( false )
	}
	
	def isDirty = dirty
	
	private def makeDirty() { changeModified( b = true )}
	
	def makeTidy() { changeModified( b = false )}
	
	private def changeModified( b: Boolean ) {
		if( dirty != b ) {
			dirty = b
			if( dirtyAction.isDefined ) dirtyAction.get.apply( b )
		}
	}
	
	@throws( classOf[ IOException ])
	def write( oos: DataOutputStream ) {
		oos.writeInt( collImg.size )
		collImg.foreach( img => {
			oos.writeUTF( img.fileName )
			oos.writeInt( img.bounds.x )
			oos.writeInt( img.bounds.y )
		})
		oos.writeInt( stitches.size )
		stitches.foreach( s => {
			oos.writeInt( collImg.indexOf( s.img1 ))
			oos.writeInt( collImg.indexOf( s.img2 ))
		})
	}
	
	@throws( classOf[ IOException ])
	def read( ois: DataInputStream ) {
		clear()
		val numImages = ois.readInt
		for( i <- (0 until numImages) ) {
			val fileName = ois.readUTF
			val x = ois.readInt
			val y = ois.readInt
			val bimg = ImageIO.read( new File( fileName ))
			val img = new StitchImage( fileName, bimg )
			img.bounds.setLocation( x, y )
			collImg += img
		}
		val numStitches = ois.readInt
		for( i <- (0 until numStitches) ) {
			val idx1 = ois.readInt
			val idx2 = ois.readInt
			val stitch = Stitch( collImg( idx1 ), collImg( idx2 ))
			stitches += stitch
		}
		recalcBounds()
		repaint()
	}
	
	def clear() {
		collImg.clear()
		stitches.clear()
		beep = 0L
		drag = None
		makeTidy()
		recalcBounds()
		repaint()
	}
	
	def setDirtyAction( fun: (Boolean) => Unit ) {
		dirtyAction = Some( fun )
	}
	
	@throws( classOf[ IOException ])
	def createPDF( file: File ) {
		val d		= getPreferredSize 
		val pageSize= new text.Rectangle( 0, 0, d.width, d.height )
		val doc		= new text.Document( pageSize, 80, 60, 60, 60 ) // margins don't matter
		val stream	= new FileOutputStream( file )
		val writer	= text.pdf.PdfWriter.getInstance( doc, stream )
		doc.open()
		val cb = writer.getDirectContent
		val tp = cb.createTemplate( d.width, d.height )
		val g2 = tp.createGraphics( d.width, d.height )
		paintGraphics( g2, extras = false )
		g2.dispose()
		cb.addTemplate( tp, 0, 0 )
		doc.close()
	}
	
	def autoAlign( img1: StitchImage, img2: StitchImage ) {
		// only one stitch per pair allowed
		if( img1.stitches.exists( s => s.img1 == img2 || s.img2 == img2 )) return
		
		alignThread = Some( new Thread( new Runnable {
			def run() {
				val oBestMove = calcBestMove( img1, img2 )
				EventQueue.invokeLater( new Runnable {
					def run() {
						beep = System.currentTimeMillis + 250
						if( oBestMove.isDefined ) {
							makeDirty()
							beepColor = Color.green
							img2.bounds.translate( oBestMove.get.x, oBestMove.get.y )
							val stitch = Stitch( img1, img2 )
							img1.stitches += stitch
							img2.stitches += stitch
							stitches += stitch
							recalcBounds()
						} else {
							beepColor = Color.red
						}
						repaint()
						new Timer().schedule( new TimerTask {
							def run() { repaint() }
						}, 300 )
						setCursor( null )
						alignThread = None
					}
				})
			}
		}))
		setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ))
		alignKeepRunning = true
		alignThread.get.start()
	}
	
	private def calcBestMove( img1: StitchImage, img2: StitchImage ) : Option[ Point ] = {
		val bestMove = new Point( 0, 0 )
		var bestRMS  = Double.MaxValue
		
		jitPt.foreach( pt => {
			if( !alignKeepRunning ) return None
				
//println( "checkin " + pt )
			
			val r2j = new Rectangle( img2.bounds )
			r2j.translate( pt.x, pt.y )
			val ri = r2j.intersection( img1.bounds )
			if( !ri.isEmpty ) {
				val rms = calcRMS( img1, img2, ri, pt )
				if( rms < bestRMS ) {
					bestRMS		= rms
					bestMove.x	= pt.x
					bestMove.y	= pt.y
					if( rms == 0 ) return Some( bestMove )
				}
			}
		})
		None
	}
	
	private def calcRMS( img1: StitchImage, img2: StitchImage, ru: Rectangle, pt: Point ) : Double = {
		var rms = 0.0
//		println( "calcRMS " + img1.bounds + " / " + img2.bounds + " / " + ru )
		for( x <- (ru.x until (ru.x + ru.width)) ) {
			for( y <- (ru.y until (ru.y + ru.height)) ) {
				val rgb1 = img1.bimg.getRGB( x - img1.bounds.x, y - img1.bounds.y )
				val rgb2 = img2.bimg.getRGB( x - (img2.bounds.x + pt.x), y - (img2.bounds.y + pt.y) )
				rms += calcRMS( rgb1, rgb2 )
			}
		}
		rms
	}
	
	private def calcRMS( rgb1: Int, rgb2: Int ) : Double = {
		val r1 = (rgb1 >> 16) & 0xFF
		val g1 = (rgb1 >>  8) & 0xFF
		val b1 = (rgb1)       & 0xFF
		val r2 = (rgb2 >> 16) & 0xFF
		val g2 = (rgb2 >>  8) & 0xFF
		val b2 = (rgb2)       & 0xFF
		val dr = r1 - r2
		val dg = g1 - g2
		val db = b1 - b2
//		Math.sqrt( (dr * dr + dg * dg + db * db) / 3.0 )
		(dr * dr + dg * dg + db * db) / 195075.0 // actually not RMS but faster, so who cares
	}
	
	def setViewPort( vp: JViewport ) {
		viewPort = Some( vp )
	}

	def removeImage( img: StitchImage ) {
		makeDirty()
		collImg -= img
		stitches --= img.stitches
		recalcBounds()
		repaint()
	}
	
	def importImage( f: File) {
		try {
			val bimg = ImageIO.read( f )
			val stitch = new StitchImage( f.getAbsolutePath, bimg )
			if( viewPort.isDefined ) {
				val pt = viewPort.get.getViewPosition
				stitch.bounds.x = ((pt.x / zoom) + virtualRect.x).toInt
				stitch.bounds.y = ((pt.y / zoom) + virtualRect.y).toInt
			}
			makeDirty()
			collImg.prepend( stitch )
			recalcBounds()
			repaint() // stitch.x, stitch.y, stitch.bimg.getWidth, stitch.bimg.getHeight
		}
		catch {
			case e: IOException =>
		}
	}
	
	def screenSizeUpdated( d: Dimension ) {
		setPreferredSize( d )
		setSize( d )
		revalidate()
	}

	private def recalcBounds() {
//println( "---1" )
		if( collImg.isEmpty ) {
			setVirtualBounds( 0, 0, 400, 400 )
			return
		}
		
		var minX = Int.MaxValue
		var minY = Int.MaxValue
		var maxX = Int.MinValue
		var maxY = Int.MinValue
		
		collImg.foreach( img => {
			minX = math.min( minX, img.bounds.x )
			minY = math.min( minY, img.bounds.y )
			maxX = math.max( maxX, img.bounds.x + img.bounds.width )
			maxY = math.max( maxY, img.bounds.y + img.bounds.height )
		})
		
//println( "---2 " + minX + ", " + minY + ", " + maxX + ", " + maxY )
		setVirtualBounds( minX - 200, minY - 200, maxX - minX + 400, maxY - minY + 400 )
	}
	
	override def paintComponent( g: Graphics ) {
		val g2 = g.asInstanceOf[ Graphics2D ]
      paintGraphics( g2, extras = true )
	}

    private def paintGraphics( g2: Graphics2D, extras: Boolean ) {
		val atOrig	= g2.getTransform

//println( virtualRect )
		g2.setRenderingHint( RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED )
		g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF )
		
		g2.scale( zoom, zoom )
		g2.translate( -clipLeftPx, -clipTopPx )
		if( extras ) {
			if( beep > System.currentTimeMillis ) {
				g2.setColor( beepColor )
				g2.fillRect( 0, 0, virtualRect.width, virtualRect.height )
			}
		}
		g2.translate( -virtualRect.x, -virtualRect.y )
		
		collImg.reverseIterator.foreach( img => {
//println( "x = " + img.x + "; y = " + img.y )
			g2.drawImage( img.bimg, img.bounds.x, img.bounds.y, this )
		})
		
		if( extras ) {
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON )
			g2.setColor( Color.yellow )
			val strkOrig = g2.getStroke
			g2.setStroke( strkStitch )
			stitches.foreach( s => {
				val ri		= s.img2.bounds.intersection( s.img1.bounds )
				val atOrig	= g2.getTransform
				g2.translate( ri.getCenterX, ri.getCenterY )
				g2.draw( shpStitch )
				g2.setTransform( atOrig )
			})
			g2.setStroke( strkOrig )
		
			g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF )
			g2.setColor( Color.red )
			drag.foreach( d => g2.draw( d.img.bounds ))
		}
		g2.setTransform( atOrig )
	}
}

case class Stitch( img1: StitchImage, img2: StitchImage )

class StitchDrag( val img: StitchImage, val e: MouseEvent ) {
	var started	= false
	val origPos = img.bounds.getLocation 
}

class StitchImage( val fileName: String, val bimg: BufferedImage ) {
	val bounds		= new Rectangle( 0, 0, bimg.getWidth, bimg.getHeight )
	val stitches	= new ListBuffer[ Stitch ]()
}