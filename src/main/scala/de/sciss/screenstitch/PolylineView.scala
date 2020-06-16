/*
 * PolylineView.scala
 * (ScreenStitch)
 *
 * Copyright (C) 2009-2020 Hanns Holger Rutz. All rights reserved.
 *
 * Published under the GNU Lesser General Public License (LGPL) v3
 */
package de.sciss.screenstitch

import java.awt.event.{ActionEvent, ActionListener, MouseEvent}
import java.awt.geom.{GeneralPath, Point2D, Rectangle2D, RoundRectangle2D}
import java.awt.{AWTEventMulticaster, BasicStroke, Color, Graphics, Graphics2D, Point, Rectangle, RenderingHints, Stroke}
import javax.swing.JComponent
import javax.swing.event.MouseInputAdapter

object PolylineView {
  /** Horizontal editing mode: Points can be freely moved unconstrainted */
  val kHEditFree = 0

  /** Horizontal editing mode: Points can be moved only between adjectant nodes */
  val kHEditClamp = 1

  /** Horizontal editing mode: Points can be freely moved, but the node indices
    * are relayed in order to maintain "causality"
    */
  val kHEditRelay = 2
}

/** Quick hack to translate from java (SwingOSC). */
class PolylineView
  extends JComponent {
  // AbstractMultiSlider
  private var nodes           = new Array[Node](0)

  // when setValues is called and the number of values
  // increases, the new nodes are created from the prototype
  // (thumb size + colour)
  private val protoNode       = new Node(-1)

  private var clipThumbs      = true

  private var selectionColor  = Color.black
  private var recentWidth     = -1
  private var recentHeight    = -1

  private var index           = -1

  private var connectionsUsed = false
  private var horizEditMode   = PolylineView.kHEditFree
  private var lockBounds      = false

  private val strkRubber      = new Array[Stroke](8)
  private var strkRubberIdx   = 0
  private val rubberRect      = new Rectangle()
  private val colrRubber      = new Color(0x40, 0x40, 0x40)

  // helper constants for shape calculation
  private val EXPM1           = math.exp(-1).toFloat
  private val EXPM1R          = (1.0 - math.exp(-1)).toFloat

  private var dirtyNodes      = new Array[Node](0)
  private var numDirty        = 0
  private var lastIndex       = -1
  private var drawLines       = true
  private var drawRects       = true

  private var stroke          = null: Stroke
  private var strokeColor     = Color.black
  private val hasStroke       = true

  private var al              = null: ActionListener
  private var stepSize        = 0.0f

  // ---- constructor ----
  {
    val dash = Array[Float](4f, 4f)
    for (i <- 0 until 8) {
      strkRubber(i) = new BasicStroke(1f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10f, dash, i)
    }

    val ma = new MouseAdapter
    addMouseListener(ma)
    addMouseMotionListener(ma)

    setOpaque(false)
  }

  def setHorizontalEditMode(mode: Int): Unit = horizEditMode = mode

  def setLockBounds(onOff: Boolean): Unit = lockBounds = onOff

  /** Registers a new <code>ActionListener</code> with
    * the component. <code>ActionEvent</code>s are fired
    * when the user adjusts the knob.
    *
    * @param	l	the listener to register
    */
  def addActionListener(l: ActionListener): Unit =
    al = AWTEventMulticaster.add(al, l)

  /** Unregisters a <code>ActionListener</code> from
    * the component.
    *
    * @param	l	the listener to remove from being notified
    */
  def removeActionListener(l: ActionListener): Unit =
    al = AWTEventMulticaster.remove(al, l)

  def setDrawLines(onOff: Boolean): Unit = {
    drawLines = onOff
    repaint()
  }

  def getDrawLines: Boolean = drawLines

  def setDrawRects(onOff: Boolean): Unit = {
    drawRects = onOff
    repaint()
  }

  def getDrawRects: Boolean = drawRects

  override def paintComponent(g: Graphics): Unit = {
    val g2 = g.asInstanceOf[Graphics2D]
    val ins = getInsets
    val cw = getWidth - ins.left - ins.right
    val ch = getHeight - ins.top - ins.bottom
    val atOrig = g2.getTransform

    g2.translate(ins.left, ins.top)
    paintKnob(g2, cw, ch)
    g2.setTransform(atOrig)
  }

  protected def fireActionPerformed(): Unit = {
    val l = al
    if (l != null) {
      l.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null))
    }
  }

  protected def paintKnob(g2: Graphics2D, cw1: Int, ch1: Int): Unit = {
    val clipOrig        = g2.getClip
    val numValues       = nodes.length
    val reallyDrawLines = drawLines && hasStroke && (numValues > 0)
    val invalidAll      = (cw1 != recentWidth) || (ch1 != recentHeight)
    val drawORects      = drawRects && hasStroke
    val fm              = g2.getFontMetrics

    if (invalidAll) {
      recentWidth   = cw1
      recentHeight  = ch1
    }

    val cw = cw1 - 2
    val ch = ch1 - 2
    g2.translate(1, 1)
    g2.clipRect(0, 0, cw, ch)

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val w = cw
    val h = ch

    val gpLines   = if (reallyDrawLines)        new GeneralPath() else null
    val gpOutline = if (drawRects && hasStroke) new GeneralPath() else null

    nodes.foreach { n =>
      if (invalidAll || n.invalid) {
        val thumbWidthH   = n.thumbWidth / 2
        val thumbHeightH  = n.thumbHeight / 2
        var bx = 0f
        var by = 0f
        if (clipThumbs) {
          n.cx  = w * n.x
          n.cy  = h * (1f - n.y)
          bx    = n.cx - thumbWidthH
          by    = n.cy - thumbHeightH
        } else {
          bx    = (w - n.thumbWidth) * n.x
          by    = (h - n.thumbHeight) * (1f - n.y)
          n.cx  = bx + thumbWidthH
          n.cy  = by + thumbHeightH
        }
        n.r.setFrame(bx, by, n.thumbWidth, n.thumbHeight)
        n.rr.setFrame(bx - 0.5f, by - 0.5f, n.thumbWidth, n.thumbHeight)

        if (n.label != null) {
          n.tx = n.cx - fm.stringWidth(n.label) / 2
          n.ty = n.cy + (fm.getAscent - fm.getDescent) / 2 - 1
        }

        n.invalid = false
      }
      if (drawORects) {
        gpOutline.append(n.rr, false)
      }
    }

    if (reallyDrawLines) {
      // implies numValues >= 1
      if (connectionsUsed) {
        // -------------------- custom connections --------------------
        for (i <- 0 until numValues) {
          val n = nodes(i)
          n.shape match {
            case Node.SHP_STEP =>
              for (j <- n.connections.indices) {
                val n2 = n.connections(j)
                gpLines.moveTo(n.cx, n.cy)
                gpLines.lineTo(n.cx, n2.cy)
                gpLines.lineTo(n2.cx, n2.cy)
              }
            case Node.SHP_LINEAR =>
              for (j <- n.connections.indices) {
                val n2 = n.connections(j)
                gpLines.moveTo(n.cx, n.cy)
                gpLines.lineTo(n2.cx, n2.cy)
              }
            case Node.SHP_SINE =>
              for (j <- n.connections.indices) {
                val n2 = n.connections(j)
                gpLines.moveTo(n.cx, n.cy)
                gpLines.curveTo(n.cx * EXPM1R + n2.cx * EXPM1, n.cy, n.cx * EXPM1 + n2.cx * EXPM1R, n2.cy, n2.cx, n2.cy)
              }
            case _ =>
              for (j <- n.connections.indices) {
                val n2 = n.connections(j)
                if (n.x == n2.x) {
                  gpLines.moveTo(n.cx, n.cy)
                  gpLines.lineTo(n2.cx, n2.cy)
                } else {
                  val n1s = if (n.x < n2.x) n else n2
                  val n2s = if (n.x < n2.x) n2 else n
                  val dx = n2s.cx - n1s.cx
                  val oy1 = if (clipThumbs) 0f else n1s.thumbHeight / 2
                  val oy2 = if (clipThumbs) 0f else n2s.thumbHeight / 2
                  val sy1: Float = if (clipThumbs) h else h - n1s.thumbHeight
                  val sy2: Float = if (clipThumbs) h else h - n2s.thumbHeight

                  var ix = 0f // n.cx - n1s.cx;
                  gpLines.moveTo(n1s.cx, n1s.cy)
                  do {
                    ix = math.min(dx, ix + 2)
                    val rx = ix / dx
                    val ry = (1.0 - envAt(n1s, n2s, rx)).toFloat
                    gpLines.lineTo(ix + n1s.cx, (sy1 * ry + oy1) * (1 - rx) +
                      (sy2 * ry + oy2) * rx)
                  } while (ix != dx)
                }
              }
          }
        }
      } else {
        // -------------------- sequential connections --------------------
        var n = nodes(0)
        for (i <- 1 until numValues) {
          val n2 = nodes(i)
          n.shape match {
            case Node.SHP_STEP =>
              gpLines.moveTo(n.cx, n.cy)
              gpLines.lineTo(n.cx, n2.cy)
              gpLines.lineTo(n2.cx, n2.cy)

            case Node.SHP_LINEAR =>
              gpLines.moveTo(n.cx, n.cy)
              gpLines.lineTo(n2.cx, n2.cy)

            case Node.SHP_SINE =>
              gpLines.moveTo(n.cx, n.cy)
              gpLines.curveTo(n.cx * EXPM1R + n2.cx * EXPM1, n.cy, n.cx * EXPM1 + n2.cx * EXPM1R, n2.cy, n2.cx, n2.cy)

            case _ =>
              if (n.x == n2.x) {
                gpLines.moveTo(n.cx, n.cy)
                gpLines.lineTo(n2.cx, n2.cy)
              } else {
                val n1s = if (n.x < n2.x) n else n2
                val n2s = if (n.x < n2.x) n2 else n
                val dx = n2s.cx - n1s.cx
                val oy1 = if (clipThumbs) 0f else n1s.thumbHeight / 2
                val oy2 = if (clipThumbs) 0f else n2s.thumbHeight / 2
                val sy1: Float = if (clipThumbs) h else h - n1s.thumbHeight
                val sy2: Float = if (clipThumbs) h else h - n2s.thumbHeight
                var ix = 0f // n.cx - n1s.cx;
                gpLines.moveTo(n1s.cx, n1s.cy)
                do {
                  ix = math.min(dx, ix + 2)
                  val rx = ix / dx
                  val ry = (1.0 - envAt(n1s, n2s, rx)).toFloat
                  gpLines.lineTo(ix + n1s.cx, (sy1 * ry + oy1) * (1 - rx) +
                    (sy2 * ry + oy2) * rx)
                } while (ix != dx)
              }
          }
          n = n2
        }
      }
      g2.setColor(strokeColor)
      val strkOrig = g2.getStroke
      if (stroke != null) g2.setStroke(stroke)
      g2.draw(gpLines)
      g2.setStroke(strkOrig)
    }

    if (drawRects) {
      for (i <- 0 until numValues) {
        val n = nodes(i)
        if (n.selected) {
          g2.setColor(selectionColor)
          g2.fill(n.r)
        } else {
          if (n.fillColor != null) {
            g2.setColor(n.fillColor)
            g2.fill(n.r)
          }
        }
        if (n.label != null) {
          g2.setColor(strokeColor)
          val subClip = g2.getClip
          g2.clip(n.r)
          g2.drawString(n.label, n.tx, n.ty)
          g2.setClip(subClip)
        }
      }
      if (hasStroke) {
        g2.setColor(strokeColor)
        val strkOrig = g2.getStroke
        if (stroke != null) g2.setStroke(stroke)
        g2.draw(gpOutline)
        g2.setStroke(strkOrig)
      }
    }

    if (!rubberRect.isEmpty) {
      val strkOrig = g2.getStroke
      g2.setColor(colrRubber)
      g2.setStroke(strkRubber(strkRubberIdx & 0x07))
      g2.draw(rubberRect)
      g2.setStroke(strkOrig)
      strkRubberIdx += 1
    }

    g2.setClip(clipOrig)
  }

  def setClipThumbs(onOff: Boolean): Unit =
    if (onOff != clipThumbs) {
      clipThumbs = onOff
      recentWidth = -1 // triggers re-calculations
      recentHeight = -1
      repaint()
    }

  def setIndex(idx: Int): Unit = index = idx

  def getLastIndex: Int = lastIndex

  // quick hacks to add editing capacities....
  def deleteSelected(): Unit = {
    val keep = nodes.filter(!_.selected)
    setValues(keep.map(_.x), keep.map(_.y),
      keep.map(_.shape), keep.map(_.curve))
  }

  /**
    * @param mode	false for index based on x value,
    *              true for index based on line projections
    */
  def insertFromScreen(scrPt: Point, mode: Boolean): Unit = {
    val vPt = screenToVirtual(inset(scrPt))
    val nx = (vPt.getX / recentWidth).toFloat
    val ny = (1.0 - (vPt.getY / recentHeight)).toFloat

    val idx = if (mode) {
      var lnP1 = nodes(0)
      var bestIdx = 1
      var minDistSq = Double.PositiveInfinity
      for (i <- 1 until nodes.length) {
        val lnP2      = nodes(i)
        val dx        = lnP2.x - lnP1.x
        val dy        = lnP2.y - lnP1.y
        val lineLenSq = (dx * dx) + (dy * dy)
        val dist      = (((nx - lnP1.x) * dx) + ((ny - lnP1.y) * dy)) / lineLenSq
        var projX     = lnP1.x + (dist * dx)
        var projY     = lnP1.y + (dist * dy)
        val linePos   = if (lnP1.x != lnP2.x) {
          (projX - lnP1.x) / dx
        } else {
          (projY - lnP1.y) / dy
        }
        if (linePos < 0) {
          projX = lnP1.x
          projY = lnP1.y
        } else if (linePos > 1) {
          projX = lnP2.x
          projY = lnP2.y
        }
        val distSq = (projX - nx) * (projX - nx) + (projY - ny) * (projY - ny)
        if (distSq < minDistSq) {
          minDistSq = distSq
          bestIdx = if (i == 1 && linePos < 0) 0
          else if ((i == nodes.length - 1) && linePos > 1) nodes.length
          else i
        }
        lnP1 = lnP2
      }
      bestIdx
    } else {
      val idx1 = nodes.indexWhere(_.x >= nx)
      if (idx1 >= 0) idx1 else nodes.length
    }
    val buf = new scala.collection.mutable.ListBuffer[Node]()
    buf.appendAll(nodes)
    val newNode = new Node(idx, protoNode) // note: idx doesn't matter
    newNode.x = nx
    newNode.y = ny
    buf.insert(idx, newNode)

    setValues(buf.map(_.x).toArray, buf.map(_.y).toArray,
      buf.map(_.shape).toArray, buf.map(_.curve).toArray)
  }

  def getNumNodes: Int = nodes.length

  def getNode(idx: Int): Node = nodes(idx)

  def setX(x: Float): Unit =
    if ((index >= 0) && (index < nodes.length)) {
      nodes(index).x = x
      nodes(index).invalid = true
      repaint()
    }

  def setY(y: Float): Unit =
    if ((index >= 0) && (index < nodes.length)) {
      nodes(index).y = y
      nodes(index).invalid = true
      repaint()
    }

  def setSelectionColor(c: Color): Unit = selectionColor = c

  def setLabel(index: Int, label: String): Unit = {
    if (index == -1) for (i <- nodes.indices) {
      nodes(i).label = label
      nodes(i).invalid = true
    } else {
      nodes(index).label = label
      nodes(index).invalid = true
    }
    repaint()
  }

  def setShape(index: Int, shape: Int, curve: Float): Unit = {
    if (index == -1) for (i <- nodes.indices) {
      nodes(i).shape = shape
      nodes(i).curve = curve
    } else {
      nodes(index).shape = shape
      nodes(index).curve = curve
    }
    protoNode.shape = shape
    protoNode.curve = curve
    repaint()
  }

  def setStepSize(stepSize: Float): Unit = {
    this.stepSize = stepSize

    if (stepSize > 0f) {
      for (i <- nodes.indices) {
        val n = nodes(i)
        n.x = snap(n.x)
        n.y = snap(n.y)
        n.invalid = true
      }
      repaint()
    }
  }

  def setReadOnly(index: Int, readOnly: Boolean): Unit =
    if (index == -1) for (i <- nodes.indices) {
      nodes(i).readOnly = readOnly
    } else {
      nodes(index).readOnly = readOnly
    }

  def setSelected(index: Int, selected: Boolean): Unit = {
    if (index == -1) {
      for (i <- nodes.indices) {
        nodes(i).selected = selected
      }
    } else {
      nodes(index).selected = selected
    }
    if (drawRects) repaint()
  }

  def setFillColor(index: Int, c: Color): Unit = {
    val c2 = if (c.getAlpha == 0) null else c
    if (index == -1) {
      for (i <- nodes.indices) {
        nodes(i).fillColor = c2
      }
    } else {
      nodes(index).fillColor = c2
    }
    protoNode.fillColor = c2
    if (drawRects) repaint()
  }

  def setStrokeColor(c: Color): Unit = {
    strokeColor = c
    repaint()
  }

  def setStrokeWidth(px: Float): Unit = {
    stroke = if (px != 1) new BasicStroke(px) else null
    repaint()
  }

  def setThumbSize(index: Int, size: Float): Unit = {
    if (index == -1) {
      for (i <- nodes.indices) {
        nodes(i).thumbWidth = size
        nodes(i).thumbHeight = size
        nodes(i).invalid = true
      }
    } else {
      nodes(index).thumbWidth = size
      nodes(index).thumbHeight = size
      nodes(index).invalid = true
    }
    protoNode.thumbWidth = size
    protoNode.thumbHeight = size
    repaint()
  }

  def setThumbWidth(index: Int, w: Float): Unit = {
    if (index == -1) {
      for (i <- nodes.indices) {
        nodes(i).thumbWidth = w
        nodes(i).invalid = true
      }
    } else {
      nodes(index).thumbWidth = w
      nodes(index).invalid = true
    }
    protoNode.thumbWidth = w
    repaint()
  }

  def setThumbHeight(index: Int, h: Float): Unit = {
    if (index == -1) {
      for (i <- nodes.indices) {
        nodes(i).thumbHeight = h
        nodes(i).invalid = true
      }
    } else {
      nodes(index).thumbHeight = h
      nodes(index).invalid = true
    }
    protoNode.thumbHeight = h
    repaint()
  }

  def setValues(x: Array[Float], y: Array[Float]): Unit = {
    val newNumVals = x.length
    val oldNumVals = nodes.length

    if (y.length != newNumVals) throw new IllegalArgumentException()

    if (oldNumVals != newNumVals) {
      var tmp     = new Array[Node](newNumVals)
      val minNum  = math.min(oldNumVals, newNumVals)
      System.arraycopy(nodes, 0, tmp, 0, minNum)
      var i = minNum
      while (i < newNumVals) {
        tmp(i) = new Node(i, protoNode)
        i += 1
      }
      nodes       = tmp
      tmp         = new Array[Node](newNumVals)
      System.arraycopy(dirtyNodes, 0, tmp, 0, minNum)
      dirtyNodes  = tmp
      numDirty    = math.min(numDirty, newNumVals)
    }

    for (i <- 0 until newNumVals) {
      nodes(i).x = x(i)
      nodes(i).y = y(i)
      nodes(i).invalid = true
    }

    repaint()
  }

  def setValues(x: Array[Float], y: Array[Float], shapes: Array[Int], curves: Array[Float]): Unit = {
    val newNumVals = x.length
    val oldNumVals = nodes.length

    if (y.length != newNumVals) throw new IllegalArgumentException()

    if (oldNumVals != newNumVals) {
      var tmp = new Array[Node](newNumVals)
      val minNum = math.min(oldNumVals, newNumVals)
      System.arraycopy(nodes, 0, tmp, 0, minNum)
      var i = minNum
      while (i < newNumVals) {
        tmp(i) = new Node(i, protoNode)
        i += 1
      }
      nodes = tmp
      tmp = new Array[Node](newNumVals)
      System.arraycopy(dirtyNodes, 0, tmp, 0, minNum)
      dirtyNodes = tmp
      numDirty = math.min(numDirty, newNumVals)
    }

    for (i <- 0 until newNumVals) {
      nodes(i).x = x(i)
      nodes(i).y = y(i)
      nodes(i).shape = shapes(i)
      nodes(i).curve = curves(i)
      nodes(i).invalid = true
    }

    repaint()
  }

  def setConnections(index: Int, targets: Array[Int]): Unit = {
    val nTargets = new Array[Node](targets.length)
    for (i <- targets.indices) {
      nTargets(i) = nodes(targets(i))
    }
    nodes(index).connections = nTargets
    if (drawLines && connectionsUsed) repaint()
  }

  def setConnectionsUsed(onOff: Boolean): Unit =
    if (onOff != connectionsUsed) {
      connectionsUsed = onOff
      repaint()
    }

  def getDirtySize: Int = numDirty

  def clearDirty(): Unit = {
    for (i <- 0 until numDirty) {
      dirtyNodes(i).dirty = false
    }
    numDirty = 0
  }

  /** Applies the grid raster to a coordinate
    *
    * @param	n	a coordinate in the range [0...1]
    * @return	the coordiante snapped to the nearest grid
    *          position or the originally coordinate if snapping
    *          is not used
    */
  def snap(n: Float): Float =
    if (stepSize <= 0f) {
      n
    } else {
      math.round(n / stepSize) * stepSize
    }

  protected def dirty(n: Node): Unit =
    if (!n.dirty) {
      n.dirty = true
      dirtyNodes(numDirty) = n
      numDirty += 1
    }

  /* Analoguous to the code in PyrArrayPrimitives.cpp.
   *
   * @param	x	a relative position between 0.0 (at n1)
   * 				and 1.0 (at n2)
   */
  private def envAt(n1: Node, n2: Node, pos: Float): Double = {
    if (nodes.length == 0) return 0f

    if (pos <= 0f) return n1.y
    if (pos >= 1f) return n2.y

    n1.shape match {
      case Node.SHP_STEP    => n2.y
      case Node.SHP_LINEAR  => pos * (n2.y - n1.y) + n1.y

      case Node.SHP_EXPONENTIAL =>
        val y1Lim = math.max(0.0001f, n1.y)
        y1Lim * math.pow(n2.y / y1Lim, pos)

      case Node.SHP_SINE =>
        n1.y + (n2.y - n1.y) * (-math.cos(math.Pi * pos) * 0.5 + 0.5)

      case Node.SHP_WELCH =>
        if (n1.y < n2.y) {
          n1.y + (n2.y - n1.y) * math.sin(math.Pi * 0.5 * pos)
        } else {
          n2.y - (n2.y - n1.y) * math.sin(math.Pi * 0.5 * (1 - pos))
        }

      case Node.SHP_CURVE =>
        if (math.abs(n1.curve) < 0.0001f) {
          pos * (n2.y - n1.y) + n1.y
        } else {
          val denom = 1.0 - math.exp(n1.curve)
          val numer = 1.0 - math.exp(pos * n1.curve)
          n1.y + (n2.y - n1.y) * (numer / denom)
        }

      case Node.SHP_SQUARED =>
        val y1Pow2 = math.sqrt(n1.y)
        val y2Pow2 = math.sqrt(n2.y)
        val yPow2 = pos * (y2Pow2 - y1Pow2) + y1Pow2
        yPow2 * yPow2

      case Node.SHP_CUBED =>
        val y1Pow3 = math.pow(n1.y, 0.3333333)
        val y2Pow3 = math.pow(n2.y, 0.3333333)
        val yPow3 = pos * (y2Pow3 - y1Pow3) + y1Pow3
        yPow3 * yPow3 * yPow3
    }
  }

  protected def screenToVirtual(pt: Point2D): Point2D = pt

  protected def screenToVirtual(r: Rectangle2D): Rectangle2D = r

  protected def inset(pt: Point): Point = {
    val ins = getInsets
    new Point(pt.x - (ins.left + 1), pt.y - (ins.top + 1))
  }

  // --------------- internal classes ---------------

  private class MouseAdapter
    extends MouseInputAdapter {

    private var shiftDrag = false
    private var dragFirstPt: Point = null
    private var dragRubber = false
    private val oldRubberRect = new Rectangle()

    private def findNode(pt: Point): Node = {
      // note that we do a little outline since we check against n.r not n.rr!
      val screenRect = new Rectangle(pt.x - 1, pt.y - 1, 4, 4)
      val vRect = screenToVirtual(screenRect)

      for (i <- nodes.indices) {
        val n = nodes(i)
        if (!n.invalid && vRect.intersects(n.r)) return n
      }
      null
    }

    override def mousePressed(e: MouseEvent): Unit = {
      if (!isEnabled || e.isControlDown) return

      requestFocus()

      dragFirstPt = inset(e.getPoint)
      val n = findNode(dragFirstPt)
      var repaint = false
      var action = false

      shiftDrag = e.isShiftDown
      dragRubber = n == null

      if (shiftDrag) {
        if (dragRubber) return
        n.selected = !n.selected
        dirty(n)
        repaint = true
        action  = true
      } else {
        if (dragRubber || !n.selected) {
          lastIndex = -1
          for (i <- nodes.indices) {
            val n2 = nodes(i)
            if ((n != n2) && n2.selected) {
              n2.selected = false
              dirty(n2)
              repaint = true
              action  = true
            }
          }
        }
        if (!dragRubber && !n.selected) {
          n.selected = true
          dirty(n)
          repaint = true
          action  = true
        }
      }
      if (!dragRubber) {
        lastIndex = n.idx
        for (i <- nodes.indices) {
          val n2 = nodes(i)
          n2.oldX = n2.x
          n2.oldY = n2.y
        }
      }
      if (repaint) PolylineView.this.repaint()
      if (action) fireActionPerformed()
    }

    override def mouseReleased(e: MouseEvent): Unit = {
      dragFirstPt = null
      if (dragRubber) {
        rubberRect.setBounds(0, 0, 0, 0)
        repaint()
      }
    }

    override def mouseDragged(e: MouseEvent): Unit = {
      if (!isEnabled || (dragFirstPt == null)) return

      val dragCurrentPt = inset(e.getPoint)
      var repaint = false
      var action = false
      var nPred: Node = null
      var nSucc: Node = null

      val vpFirst = screenToVirtual(dragFirstPt)
      val vpCurrent = screenToVirtual(dragCurrentPt)

      if (dragRubber) {
        // ------------------ rubber band ------------------
        oldRubberRect.setBounds(rubberRect)
        rubberRect.setFrameFromDiagonal(vpFirst, vpCurrent)
        for (i <- nodes.indices) {
          val n = nodes(i)
          if (!n.invalid && (oldRubberRect.intersects(n.r) != rubberRect.intersects(n.r))) {
            n.selected = !n.selected
            dirty(n)
            action = true
          }
        }
        repaint = true
      } else if ((recentWidth > 0) && (recentHeight > 0)) {
        // ------------------ move ------------------

        val dx: Float = if (clipThumbs) {
          ((vpCurrent.getX - vpFirst.getX) / recentWidth).toFloat
        } else {
          (vpCurrent.getX - vpFirst.getX).toFloat
        }
        val dy: Float = if (clipThumbs) {
          ((vpFirst.getY - vpCurrent.getY) / recentHeight).toFloat
        } else {
          (vpFirst.getY - vpCurrent.getY).toFloat
        }

        val nodesBak = new Array[Node](nodes.length)
        for (i <- nodes.indices) {
          nodes(i).done = false
          nodesBak(i) = new Node(nodes(i).idx, nodes(i))
          nodesBak(i).x = nodes(i).x
          nodesBak(i).y = nodes(i).y
          nodesBak(i).selected = nodes(i).selected
        }
        for (i <- nodes.indices) {
          val n = nodes(i)
          if (!n.selected || n.done || n.readOnly) {
            n.done = true
            //						continue
          }
          if (!n.done) {
            var x = 0f
            var y = 0f
            val reallyNotLocked = !lockBounds || ((i > 0) && (i < nodes.length - 1))
            if (clipThumbs) {
              if (reallyNotLocked) {
                x = snap(math.max(0f, math.min(1f, n.oldX + dx)))
              } else {
                x = n.x
              }
              y = snap(math.max(0f, math.min(1f, n.oldY + dy)))
            } else {
              if (reallyNotLocked) {
                x = snap(math.max(0f, math.min(1f, n.oldX +
                  dx / math.max(1, recentWidth - n.thumbWidth))))
              } else {
                x = n.x
              }
              y = snap(math.max(0f, math.min(1f, n.oldY +
                dy / math.max(1, recentHeight - n.thumbHeight))))
            }
            var n2 = n
            if (reallyNotLocked && (horizEditMode != PolylineView.kHEditFree)) {
              if (horizEditMode == PolylineView.kHEditClamp) {
                ///////////////////
                if (x < n.x) {
                  var j = i - 1
                  var keep = true
                  while ((j >= 0) && keep) {
                    if (!nodes(j).selected) {
                      nPred = nodes(j)
                      x = math.max(x, nPred.x)
                      keep = false
                    }
                    j -= 1
                  }
                } else if (x > n.x) {
                  var j = i + 1
                  var keep = true
                  while ((j < nodes.length) && keep) {
                    if (!nodes(j).selected) {
                      nSucc = nodes(j)
                      x = math.min(x, nSucc.x)
                      keep = false
                    }
                    j += 1
                  }
                } // else nothing (no x change)

              } else if (horizEditMode == PolylineView.kHEditRelay) {
                ////////////////
                var pos = i
                var j = i + 1
                var keep = true
                while ((j < nodes.length) && keep) {
                  if (!nodes(j).selected || nodes(j).readOnly) {
                    if (nodes(j).x >= x) {
                      keep = false
                    } else {
                      pos = j
                    }
                  }
                  j += 1
                }
                if (pos > i) {
                  // shift left
                  nPred = n
                  val oldX = n.oldX
                  val oldY = n.oldY
                  var j = i + 1
                  while (j <= pos) {
                    nSucc = nodes(j)
                    nPred.x = nSucc.x
                    nPred.y = nSucc.y
                    nPred.selected = nSucc.selected
                    nPred.oldX = nSucc.oldX
                    nPred.oldY = nSucc.oldY
                    dirty(nPred)
                    nPred.invalid = true
                    nPred = nSucc
                    j += 1
                  }
                  nPred.x = x
                  nPred.y = y
                  nPred.selected = true
                  nPred.oldX = oldX
                  nPred.oldY = oldY
                  nPred.done = true
                  dirty(nPred)
                  nPred.invalid = true
                  repaint = true
                  action = true
                  n2 = nPred

                } else {
                  // check backwards
                  var j = i - 1
                  var keep = true
                  while ((j >= 0) && keep) {
                    if (!nodes(j).selected || nodes(j).readOnly) {
                      if (nodes(j).x <= x) {
                        keep = false
                      } else {
                        pos = j
                      }
                    }
                    j -= 1
                  }
                  if (pos < i) {
                    // shift right
                    nSucc = n
                    val oldX = n.oldX
                    val oldY = n.oldY
                    var j = i - 1
                    while (j >= pos) {
                      nPred = nodes(j)
                      nSucc.x = nPred.x
                      nSucc.y = nPred.y
                      nSucc.selected = nPred.selected
                      nSucc.oldX = nPred.oldX
                      nSucc.oldY = nPred.oldY
                      dirty(nSucc)
                      nSucc.invalid = true
                      nSucc = nPred
                      j -= 1
                    }
                    nSucc.x = x
                    nSucc.y = y
                    nSucc.selected = true
                    nSucc.oldX = oldX
                    nSucc.oldY = oldY
                    nSucc.done = true
                    dirty(nSucc)
                    nSucc.invalid = true
                    repaint = true
                    action = true
                    n2 = nSucc
                  }
                }
              }
            }
            if ((x != n2.x) || (y != n2.y)) {
              n2.x = x
              n2.y = y
              dirty(n2)
              n2.invalid = true
              repaint = true
              action = true
            }
          }
        }
      }

      if (repaint) PolylineView.this.repaint()
      if (action) fireActionPerformed()
    }
  }

  object Node {
    val NO_CONNECTIONS  = new Array[Node](0)
    val SHP_STEP        = 0
    val SHP_LINEAR      = 1
    val SHP_EXPONENTIAL = 2
    val SHP_SINE        = 3
    val SHP_WELCH       = 4
    val SHP_CURVE       = 5
    val SHP_SQUARED     = 6
    val SHP_CUBED       = 7
  }

  protected class Node(val idx: Int) {
    var x                         = 0f
    var y                         = 0f
    var shape       : Int         = Node.SHP_LINEAR
    var curve                     = 0f
    var fillColor   : Color       = Color.black
    var thumbWidth                = 5f
    // 12f;
    var thumbHeight               = 5f // 12f;

    var readOnly                  = false
    var selected                  = false
    var connections : Array[Node] = Node.NO_CONNECTIONS
    var label       : String      = null
    val r                         = new Rectangle2D.Float()
    val rr                        = new RoundRectangle2D.Float(0f, 0f, 0f, 0f, 2f, 2f)
    var invalid                   = true
    var done                      = false
    // for dnd
    var cx                        = 0f
    var cy                        = 0f
    var tx                        = 0f
    var ty                        = 0f

    // dnd
    var dirty                     = false
    var oldX                      = 0f
    var oldY                      = 0f

    def this(idx: Int, orig: Node) = {
      this(idx)
      fillColor   = orig.fillColor
      thumbWidth  = orig.thumbWidth
      thumbHeight = orig.thumbHeight
      shape       = orig.shape
      curve       = orig.curve
    }
  }
}