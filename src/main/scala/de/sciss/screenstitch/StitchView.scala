/*
 * StitchView.scala
 * (ScreenStitch)
 *
 * Copyright (C) 2009-2019 Hanns Holger Rutz. All rights reserved.
 *
 * Published under the GNU Lesser General Public License (LGPL) v3
 */

package de.sciss.screenstitch

import java.awt.event.{KeyAdapter, KeyEvent, MouseEvent}
import java.awt.geom.GeneralPath
import java.awt.image.BufferedImage
import java.awt.{BasicStroke, Color, Cursor, Dimension, EventQueue, Graphics, Graphics2D, Point, Rectangle, RenderingHints}
import java.io.{DataInputStream, DataOutputStream, File, FileOutputStream, IOException}
import java.util.{Timer, TimerTask}
import javax.imageio.ImageIO
import javax.swing.event.MouseInputAdapter
import javax.swing.{JComponent, JViewport}

import com.itextpdf.text

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.collection.mutable.ListBuffer

class StitchView
  extends JComponent // JPanel // JComponent
  // WARNING: bug in JComponent : when a JComponent is inside a JPanel
  // which is the view of a scrollpane's viewport, the display is truncated
  // to 16384 pixels!!! for whatever reason, subclassing JPanel instead
  // fixes the problem
  with Zoomable {
  
  private var dirty         = false
  private val jitter        = 24 // 32
  private val shpStitch     = new GeneralPath()
  private val strokeStitch  = new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER)
  private var jitPt       : Vec[Point] = _

  private var viewPort      = Option.empty[JViewport]

  private var drag          = Option.empty[StitchDrag]

  private var alignThread   = Option.empty[Thread]

  private var dirtyAction   = Option.empty[Boolean => Unit]

  @volatile private var alignKeepRunning = true
  private var beep          = 0L
  private var beepColor     = Color.white

  // doc data
  private val collImg       = ListBuffer[StitchImage]()
  private val stitches      = ListBuffer[Stitch]()

  // ---- constructor ----
  {
    recalculateBounds()
    //		setPreferredSize( new Dimension( 800, 800 ))

    shpStitch.moveTo(-30, 30)
    shpStitch.quadTo(-10, -10, 30, -30)
    shpStitch.moveTo(-30, -30)
    //		shpStitch.quadTo( 10, -10, 30, 30 )
    shpStitch.lineTo(30, 30)

    val unsorted = new ListBuffer[Point]()
    val jitSq = jitter *  jitter
    for (x <- -jitter to jitter) {
      for (y <- -jitter to jitter) {
        val rdSq = x * x + y * y
        if (rdSq <= jitSq) {
          unsorted += new Point(x, y)
        }
      }
    }
    jitPt = unsorted.sortWith((pt1, pt2) => pt1.x * pt1.x + pt1.y * pt1.y < pt2.x * pt2.x + pt2.y * pt2.y).toIndexedSeq

    setTransferHandler(new FileDropHandler(importImage))

    val mia = new MouseInputAdapter {

      override def mousePressed(e: MouseEvent): Unit = {
        requestFocus()

        if (alignThread.isDefined) return

        drag = None
        val vPt = screenToVirtual(e.getPoint)
        val oImg = collImg.find(_.bounds.contains(vPt))
        if (oImg.isDefined) {
          val img1 = oImg.get
          if (e.isAltDown) {
            removeImage(img1)
          } else if (e.getClickCount == 2) {
            collImg.find(img => (img != img1) && img.bounds.contains(vPt)).foreach { img2 =>
              if (areStitched(img1, img2)) {
                unStitch(img1, img2)
              } else {
                autoAlign(img1, img2)
              }
            }
          } else {
            drag = Some(new StitchDrag(img1, e))
          }
        }
      }

      override def mouseReleased(e: MouseEvent): Unit =
        drag.foreach { d =>
          if (d.started) {
            recalculateBounds()
            repaint()
          }
          drag = None
        }

      override def mouseDragged(e: MouseEvent): Unit =
        drag.foreach { d =>
          if (!d.started) {
            if (e.getPoint.distance(d.e.getPoint) < 9) return
            d.started = true
            collImg -= d.img
            collImg.prepend(d.img) // so gets painted on top
          }
          makeDirty()
          d.img.bounds.setLocation(
            d.origPos.x + ((e.getX - d.e.getX) / zoom).toInt,
            d.origPos.y + ((e.getY - d.e.getY) / zoom).toInt)
          repaint()
        }
    }

    addMouseListener(mia)
    addMouseMotionListener(mia)

    addKeyListener(new KeyAdapter {
      override def keyPressed(e: KeyEvent): Unit =
        if (e.getKeyCode == KeyEvent.VK_ESCAPE) {
          alignKeepRunning = false
        }
    })

    setOpaque(false)
  }

  def isDirty: Boolean = dirty

  private def makeDirty(): Unit =
    changeModified(b = true)

  def makeTidy(): Unit =
    changeModified(b = false)

  private def changeModified(b: Boolean): Unit =
    if (dirty != b) {
      dirty = b
      if (dirtyAction.isDefined) dirtyAction.get.apply(b)
    }

  def write(oos: DataOutputStream): Unit = {
    oos.writeInt(collImg.size)
    collImg.foreach(img => {
      oos.writeUTF(img.fileName)
      oos.writeInt(img.bounds.x)
      oos.writeInt(img.bounds.y)
    })
    oos.writeInt(stitches.size)
    stitches.foreach(s => {
      oos.writeInt(collImg.indexOf(s.img1))
      oos.writeInt(collImg.indexOf(s.img2))
    })
  }

  def read(ois: DataInputStream): Unit = {
    clear()
    val numImages = ois.readInt
    for (_ <- 0 until numImages) {
      val fileName  = ois.readUTF
      val x         = ois.readInt
      val y         = ois.readInt
      val bImg      = ImageIO.read(new File(fileName))
      val img       = new StitchImage(fileName, bImg)
      img.bounds.setLocation(x, y)
      collImg += img
    }
    val numStitches = ois.readInt
    for (_ <- 0 until numStitches) {
      val idx1      = ois.readInt
      val idx2      = ois.readInt
      val stitch    = Stitch(collImg(idx1), collImg(idx2))
      stitches += stitch
    }
    recalculateBounds()
    repaint()
  }

  var fuzzy = false

  def clear(): Unit = {
    collImg.clear()
    stitches.clear()
    beep = 0L
    drag = None
    makeTidy()
    recalculateBounds()
    repaint()
  }

  def setDirtyAction(fun: Boolean => Unit): Unit =
    dirtyAction = Some(fun)

  def createPDF(file: File): Unit = {
    val d = getPreferredSize
    val pageSize = new text.Rectangle(0, 0, d.width, d.height)
    val doc = new text.Document(pageSize, 80, 60, 60, 60) // margins don't matter
    val stream = new FileOutputStream(file)
    val writer = text.pdf.PdfWriter.getInstance(doc, stream)
    doc.open()
    val cb = writer.getDirectContent
    val tp = cb.createTemplate(d.width, d.height)
    val g2 = tp.createGraphics(d.width, d.height)
    paintGraphics(g2, extras = false)
    g2.dispose()
    cb.addTemplate(tp, 0, 0)
    doc.close()
  }

  def createPNG(file: File): Unit = {
    if (collImg.isEmpty) return

    val r   = calcMinimumRectangle
    val w   = r.width
    val h   = r.height
    val img = new BufferedImage(w, h, BufferedImage.TYPE_4BYTE_ABGR)
    val g   = img.createGraphics()
    g.setColor(Color.white) // XXX TODO could be customisable
    g.fillRect(0, 0, w, h)
    collImg.foreach { i =>
      val b = i.bounds
      g.drawImage(i.bImg, b.x - r.x, b.y - r.y, this)
    }
    g.dispose()
    ImageIO.write(img, "png", file)
  }

  def areStitched(img1: StitchImage, img2: StitchImage): Boolean =
    img1.stitches.exists(s => s.img1 == img2 || s.img2 == img2)

  def unStitch(img1: StitchImage, img2: StitchImage): Unit = {
    def remove(img: StitchImage): Unit = {
      val st = img.stitches.filter(s => (s.img1 == img1 && s.img2 == img2) || (s.img1 == img2 && s.img2 == img1))
      img.stitches -- st
      stitches -- st
    }

    remove(img1)
    remove(img2)
    repaint()
  }

  def autoAlign(img1: StitchImage, img2: StitchImage): Unit = {
    // only one stitch per pair allowed
    if (areStitched(img1, img2)) return

    alignThread = Some(new Thread(new Runnable {
      def run(): Unit = {
        val oBestMove = calcBestMove(img1, img2)
        EventQueue.invokeLater(new Runnable {
          def run(): Unit = {
            beep = System.currentTimeMillis + 250
            if (oBestMove.isDefined) {
              makeDirty()
              beepColor = Color.green
              img2.bounds.translate(oBestMove.get.x, oBestMove.get.y)
              val stitch = Stitch(img1, img2)
              img1.stitches += stitch
              img2.stitches += stitch
              stitches += stitch
              recalculateBounds()
            } else {
              beepColor = Color.red
            }
            repaint()
            new Timer().schedule(new TimerTask {
              def run(): Unit = repaint()
            }, 300)
            setCursor(null)
            alignThread = None
          }
        })
      }
    }))
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR))
    alignKeepRunning = true
    alignThread.get.start()
  }

  private def calcBestMove(img1: StitchImage, img2: StitchImage): Option[Point] = {
    val bestMove  = new Point(0, 0)
    var bestRMS   = Double.MaxValue

    jitPt.foreach { pt =>
      if (!alignKeepRunning) return None

      val r2j = new Rectangle(img2.bounds)
      r2j.translate(pt.x, pt.y)
      val ri = r2j.intersection(img1.bounds)
      if (!ri.isEmpty) {
        val rms = calcRMS(img1, img2, ri, pt)
        if (rms < bestRMS) {
          bestRMS = rms
          bestMove.x = pt.x
          bestMove.y = pt.y
          if (rms == 0.0) return Some(bestMove)
        }
      }
    }
    if (fuzzy) Some(bestMove) else None
  }

  private def calcRMS(img1: StitchImage, img2: StitchImage, ru: Rectangle, pt: Point): Double = {
    var rms = 0.0
    for (x <- ru.x until (ru.x + ru.width)) {
      for (y <- ru.y until (ru.y + ru.height)) {
        val rgb1 = img1.bImg.getRGB(x - img1.bounds.x, y - img1.bounds.y)
        val rgb2 = img2.bImg.getRGB(x - (img2.bounds.x + pt.x), y - (img2.bounds.y + pt.y))
        rms += calcRMS(rgb1, rgb2)
      }
    }
    rms
  }

  private def calcRMS(rgb1: Int, rgb2: Int): Double = {
    val r1 = (rgb1 >> 16) & 0xFF
    val g1 = (rgb1 >>  8) & 0xFF
    val b1 =  rgb1        & 0xFF
    val r2 = (rgb2 >> 16) & 0xFF
    val g2 = (rgb2 >>  8) & 0xFF
    val b2 =  rgb2        & 0xFF
    val dr = r1 - r2
    val dg = g1 - g2
    val db = b1 - b2
    (dr * dr + dg * dg + db * db) / 195075.0 // actually not RMS but faster, so who cares
  }

  def setViewPort(vp: JViewport): Unit =
    viewPort = Some(vp)

  def removeImage(img: StitchImage): Unit = {
    makeDirty()
    collImg -= img
    stitches --= img.stitches
    recalculateBounds()
    repaint()
  }

  def importImage(f: File): Unit =
    try {
      val bImg = ImageIO.read(f)
      val stitch = new StitchImage(f.getAbsolutePath, bImg)
      if (viewPort.isDefined) {
        val pt = viewPort.get.getViewPosition
        stitch.bounds.x = ((pt.x / zoom) + virtualRect.x).toInt
        stitch.bounds.y = ((pt.y / zoom) + virtualRect.y).toInt
      }
      makeDirty()
      collImg.prepend(stitch)
      recalculateBounds()
      repaint() // stitch.x, stitch.y, stitch.bimg.getWidth, stitch.bimg.getHeight
    }
    catch {
      case _: IOException =>
    }

  def screenSizeUpdated(d: Dimension): Unit = {
    setPreferredSize(d)
    setSize(d)
    revalidate()
  }

  private def calcMinimumRectangle: Rectangle = {
    var minX = Int.MaxValue
    var minY = Int.MaxValue
    var maxX = Int.MinValue
    var maxY = Int.MinValue

    collImg.foreach { img =>
      minX = math.min(minX, img.bounds.x)
      minY = math.min(minY, img.bounds.y)
      maxX = math.max(maxX, img.bounds.x + img.bounds.width)
      maxY = math.max(maxY, img.bounds.y + img.bounds.height)
    }

    new Rectangle(minX, minY, maxX - minX, maxY - minY)
  }

  private def recalculateBounds(): Unit = {
    if (collImg.isEmpty) {
      setVirtualBounds(0, 0, 400, 400)
      return
    }

    val r = calcMinimumRectangle

    setVirtualBounds(r.x - 200, r.y - 200, r.width + 400, r.height + 400)
  }

  override def paintComponent(g: Graphics): Unit = {
    val g2 = g.asInstanceOf[Graphics2D]
    paintGraphics(g2, extras = true)
  }

  private var qualityVar = false

  def quality: Boolean = qualityVar

  def quality_=(b: Boolean): Unit =
    if (qualityVar != b) {
      qualityVar = b
      repaint()
    }

  private def paintGraphics(g2: Graphics2D, extras: Boolean): Unit = {
    val atOrig = g2.getTransform

    //println( virtualRect )
    g2.setRenderingHint(RenderingHints.KEY_RENDERING   ,
      if (quality) RenderingHints.VALUE_RENDER_QUALITY else RenderingHints.VALUE_RENDER_SPEED)

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
      if (quality) RenderingHints.VALUE_ANTIALIAS_ON   else RenderingHints.VALUE_ANTIALIAS_OFF)

    g2.scale(zoom, zoom)
    g2.translate(-clipLeftPx, -clipTopPx)
    if (extras) {
      if (beep > System.currentTimeMillis) {
        g2.setColor(beepColor)
        g2.fillRect(0, 0, virtualRect.width, virtualRect.height)
      }
    }
    g2.translate(-virtualRect.x, -virtualRect.y)

    collImg.reverseIterator.foreach { img =>
      g2.drawImage(img.bImg, img.bounds.x, img.bounds.y, this)
    }

    if (extras) {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setColor(Color.yellow)
      val strokeOrig = g2.getStroke
      g2.setStroke(strokeStitch)
      stitches.foreach(s => {
        val ri = s.img2.bounds.intersection(s.img1.bounds)
        val atOrig = g2.getTransform
        g2.translate(ri.getCenterX, ri.getCenterY)
        g2.draw(shpStitch)
        g2.setTransform(atOrig)
      })
      g2.setStroke(strokeOrig)

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
      g2.setColor(Color.red)
      drag.foreach { d =>
        g2.draw(d.img.bounds)
      }
    }
    g2.setTransform(atOrig)
  }

  def imageFiles: Vec[File] = collImg.map(i => new File(i.fileName))(collection.breakOut)
}

case class Stitch(img1: StitchImage, img2: StitchImage)

class StitchDrag(val img: StitchImage, val e: MouseEvent) {
  var started         = false
  val origPos: Point  = img.bounds.getLocation
}

class StitchImage(val fileName: String, val bImg: BufferedImage) {
  val bounds    = new Rectangle(0, 0, bImg.getWidth, bImg.getHeight)
  val stitches  = new ListBuffer[Stitch]()
}