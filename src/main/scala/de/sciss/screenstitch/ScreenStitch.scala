/*
 * ScreenStitch.scala
 * (ScreenStitch)
 *
 * Copyright (C) 2009-2016 Hanns Holger Rutz. All rights reserved.
 *
 * Published under the GNU Lesser General Public License (LGPL) v3
 */

package de.sciss.screenstitch

import java.awt.event.{ActionEvent, InputEvent, KeyEvent, MouseAdapter, MouseEvent, WindowAdapter, WindowEvent}
import java.awt.{BorderLayout, Color, Dimension, EventQueue, FileDialog, Font, Graphics2D, Toolkit}
import java.io.{BufferedInputStream, BufferedOutputStream, DataInputStream, DataOutputStream, File, FileInputStream, FileOutputStream, IOException}
import javax.swing.event.{ChangeEvent, ChangeListener}
import javax.swing.{AbstractAction, AbstractButton, Action, JCheckBoxMenuItem, JFrame, JMenu, JMenuBar, JMenuItem, JOptionPane, JPanel, JScrollPane, JSeparator, JSlider, KeyStroke, OverlayLayout, SwingConstants, WindowConstants}

import scala.util.control.NonFatal

object ScreenStitch {
  val cookie  = 0x53544954
  val version = 1

  def main(args: Array[String]): Unit =
    EventQueue.invokeLater(new Runnable {
      def run(): Unit = new ScreenStitch
    })

  def linexp(x: Double, inLo: Double, inHi: Double, outLo: Double, outHi: Double): Double =
    math.pow(outHi / outLo, (x - inLo) / (inHi - inLo)) * outLo
}

class ScreenStitch {

  private val frame       = new JFrame()
  private val view        = new StitchView
  private val poly        = new PolylineZoomView()
  private var docFile     = Option.empty[File]
  private var miEditPoly  = null: JCheckBoxMenuItem

  // constructor
  {
    val cp      = frame.getContentPane
    val checker = new CheckerBackground(32)

    poly.setVisible(false)
    poly.setFillColor(-1,  Color.green)
    poly.setStrokeColor   (Color.green)
    poly.setSelectionColor(Color.red  )
    poly.setThumbSize(-1, 10)
    poly.setStrokeWidth(3)
    poly.setFont(new Font("Helvetica", Font.PLAIN, 21))
    poly.addMouseListener(new MouseAdapter {
      override def mousePressed(e: MouseEvent): Unit =
        if (e.isAltDown) {
          poly.deleteSelected()
        } else if (e.getClickCount == 2) {
          poly.insertFromScreen(e.getPoint, mode = true)
        }
    })

    val ggOverlay = new JPanel()
    ggOverlay.setLayout(new OverlayLayout(ggOverlay))
    // note: add front-to-back
    ggOverlay.add(poly)
    ggOverlay.add(view)
    ggOverlay.add(checker)

    val ggScroll = new JScrollPane(ggOverlay)
    val vp = ggScroll.getViewport
    view.setViewPort(vp)
    view.setDirtyAction(b => setDirty(b))
    view.addSlave(checker)
    view.addSlave(poly)

    cp.add(ggScroll, BorderLayout.CENTER)
    ggScroll.setPreferredSize(new Dimension(400, 400))

    val ggZoom = new JSlider(SwingConstants.HORIZONTAL, 0, 0x10000, 0x10000)
    ggZoom.addChangeListener(new ChangeListener {
      def stateChanged(e: ChangeEvent): Unit =
        view.setZoom(ScreenStitch.linexp(ggZoom.getValue, 0, 0x10000, 0.03125, 1).toFloat)
    })
    cp.add(ggZoom, BorderLayout.SOUTH)

    val ggClipLeft = new JSlider(SwingConstants.HORIZONTAL, 0, 0x10000, 0)
    ggClipLeft.addChangeListener(new ChangeListener {
      def stateChanged(e: ChangeEvent): Unit =
        view.clipLeft(ggClipLeft.getValue.toFloat / 0x10000)
    })
    cp.add(ggClipLeft, BorderLayout.NORTH)

    val ggClipTop = new JSlider(SwingConstants.VERTICAL, 0, 0x10000, 0)
    ggClipTop.addChangeListener(new ChangeListener {
      def stateChanged(e: ChangeEvent): Unit =
        view.clipTop(ggClipTop.getValue.toFloat / 0x10000)
    })
    cp.add(ggClipTop, BorderLayout.WEST)

    val meta  = Toolkit.getDefaultToolkit.getMenuShortcutKeyMask
    val mb    = new JMenuBar
    var m     = new JMenu("File")
    mb.add(m)
    m.add(new JMenuItem(new AbstractAction("New") {
      putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_N, meta))

      def actionPerformed(e: ActionEvent): Unit = clearDoc()
    }))
    m.add(new JMenuItem(new AbstractAction("Open...") {
      putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O, meta))

      def actionPerformed(e: ActionEvent): Unit = openDoc()
    }))
    m.add(new JSeparator)
    m.add(new JMenuItem(new AbstractAction("Save") {
      putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S, meta))

      def actionPerformed(e: ActionEvent): Unit = saveDoc()
    }))
    m.add(new JMenuItem(new AbstractAction("Save As...") {
      putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_N, meta | InputEvent.SHIFT_MASK))

      def actionPerformed(e: ActionEvent): Unit = saveAsDoc()
    }))
    m.add(new JSeparator)
    m.add(new JMenuItem(new AbstractAction("Export to PDF...") {
      def actionPerformed(e: ActionEvent): Unit = exportToPDF()
    }))
    m.add(new JMenuItem(new AbstractAction("Export to PNG Image...") {
      putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_P, meta))

      def actionPerformed(e: ActionEvent): Unit = exportToPNG()
    }))

    m = new JMenu("Tools")
    mb.add(m)
    m.add(new JMenuItem(new AbstractAction("Create Polyline") {
      def actionPerformed(e: ActionEvent): Unit = createPolyLine()
    }))
    m.add(new JMenuItem(new AbstractAction("Add Kilometer Labels") {
      def actionPerformed(e: ActionEvent): Unit = addKilometerLabels()
    }))

    m = new JMenu("View")
    mb.add(m)
    miEditPoly = new JCheckBoxMenuItem(new AbstractAction("Edit Polyline") {
      def actionPerformed(e: ActionEvent): Unit = {
        val but = e.getSource.asInstanceOf[AbstractButton]
        togglePolyVisibility(but.isSelected)
      }
    })
    m.add(miEditPoly)
    val miEditQuality = new JCheckBoxMenuItem(new AbstractAction("High Quality") {
      def actionPerformed(e: ActionEvent): Unit = {
        val but = e.getSource.asInstanceOf[AbstractButton]
        view.quality = but.isSelected
      }
    })
    m.add(miEditQuality)

    frame.setJMenuBar(mb)
    frame.pack()
    frame.setLocationRelativeTo(null)
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE)
    frame.addWindowListener(new WindowAdapter {
      override def windowClosing(e: WindowEvent): Unit =
        if (!view.isDirty || confirmLoss("Quit")) {
          sys.exit()
        }
    })
    updateTitle()
    frame.setVisible(true)
    frame.toFront()
  }

  private def togglePolyVisibility(onOff: Boolean): Unit = {
    poly.setVisible(onOff)
    miEditPoly.setSelected(onOff)
  }

  private def createPolyLine(): Unit = {
    if (poly.getNumNodes > 0) return

    poly.setValues(List(0.0f, 0.25f, 0.5f, 0.75f, 1.0f).toArray,
      List(0.0f, 1.0f, 0.25f, 0.0f, 0.5f).toArray)

    togglePolyVisibility(onOff = true)
  }

  private def addKilometerLabels(): Unit = {
    val numNodes = poly.getNumNodes
    if (numNodes < 2) return
    val totalKMo = queryNumber("Add Kilometer Labels", "Enter the total polyline\nlength in km:", 100)
    if (totalKMo.isEmpty) return
    val totalKM = totalKMo.get
    if (totalKM <= 0.0) return
    val spaceKMo = queryNumber("Add Kilometer Labels", "Enter the spacing between\nlabels in km:", 5)
    if (spaceKMo.isEmpty) return
    val spaceKM = spaceKMo.get
    if (spaceKM <= 0.0 || spaceKM >= totalKM) return

    var n1 = poly.getNode(0)
    var lineSum = 0.0
    for (i <- 1 until numNodes) {
      val n2 = poly.getNode(i)
      val dx = n2.x - n1.x
      val dy = n2.y - n1.y
      val hyp = math.sqrt(dx * dx + dy * dy)
      lineSum += hyp
      n1 = n2
    }
    val scale = totalKM / lineSum

    val fnt = poly.getFont
    val g2  = poly.getGraphics.asInstanceOf[Graphics2D]
    val frc = g2.getFontRenderContext

    lineSum = 0.0
    var labelCnt = 1
    n1 = poly.getNode(0)
    for (i <- 1 until numNodes) {
      val n2 = poly.getNode(i)
      val dx = n2.x - n1.x
      val dy = n2.y - n1.y
      val hyp = math.sqrt(dx * dx + dy * dy)
      lineSum += hyp
      val lineSumSc = lineSum * scale
      if (lineSumSc >= (spaceKM * labelCnt)) {
        val txt = math.round(lineSumSc).toString
        val r = fnt.getStringBounds(txt, frc)
        poly.setThumbWidth(i, r.getWidth.toFloat + 16)
        poly.setThumbHeight(i, r.getHeight.toFloat + 8)
        poly.setFillColor(i, Color.black)
        poly.setLabel(i, txt)
        labelCnt = (lineSumSc / spaceKM).toInt + 1
      }
      n1 = n2
    }

    g2.dispose()
  }

  private def setDirty(b: Boolean): Unit = {
    val rp = frame.getRootPane
    rp.putClientProperty("windowModified", new java.lang.Boolean(b))
  }

  private def confirmLoss(title: String): Boolean = {
    val options = List[Object]("Save", "Cancel", "Don't Save").toArray
    val res = JOptionPane.showOptionDialog(frame,
      "The document contains unsaved changes.",
      title, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
      null, options, options(1))
    //		println( res )
    if (res == 0) {
      saveDoc()
    } else res == 2
  }

  private def queryNumber(title: String, msg: String, num: Double): Option[Double] = {
    val res = JOptionPane.showInputDialog(frame,
      msg, title, JOptionPane.QUESTION_MESSAGE, null, null, num)

    if (res == null) None else Some(res.toString.toDouble)
  }

  private def confirmOverwrite(title: String, f: File): Boolean = {
    val res = JOptionPane.showConfirmDialog(frame,
      s"The file\n${f.getAbsolutePath}\nalready exists. Overwrite?",
      title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)

    println(res)
    res == JOptionPane.YES_OPTION
  }

  def clearDoc(): Unit = {
    if (view.isDirty && !confirmLoss("New")) return
    view.clear()
    docFile = None
    updateTitle()
  }

  def openDoc(): Unit = {
    if (view.isDirty && !confirmLoss("Open")) return
    val df = queryLoadFile("Open")
    if (df.isDefined) {
      try {
        load(df.get)
        docFile = df
        updateTitle()
      }
      catch {
        case NonFatal(e) => displayError("Open", e)
      }
    }
  }

  private def displayError(title: String, e: Throwable): Unit = {
    val m0 = if (e.getMessage == null) "" else e.getMessage
    var msg = s"<HTML><BODY><P><B>${e.getClass.getName}<BR>$m0</B></P><UL>"

    val trace = e.getStackTrace
    val numTraceLines = 4
    for (i <- 0 until math.min(numTraceLines, trace.length)) {
      msg = s"$msg<LI>at ${trace(i)}</LI>"
    }
    if (trace.length > 3) {
      msg = s"$msg<LI>...</LI>"
    }
    msg = s"$msg</UL></BODY></HTML>"
    JOptionPane.showMessageDialog(frame, msg, title, JOptionPane.ERROR_MESSAGE)
  }

  def saveDoc(): Boolean = {
    if (docFile.isEmpty) {
      saveAsDoc()
    } else {
      save(docFile)
    }
  }

  private def save(df: Option[File]): Boolean = {
    if (df.isEmpty) return false
    try {
      save(df.get)
      docFile = df
      updateTitle()
      true
    }
    catch {
      case NonFatal(e) =>
        displayError("Save", e)
        false
    }
  }

  private def save(file: File): Unit = {
    file.delete()
    val oos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))
    oos.writeInt(ScreenStitch.cookie)
    oos.writeInt(ScreenStitch.version)
    view.write(oos)
    // write polyline
    val numNodes = poly.getNumNodes
    oos.writeInt(numNodes)
    for (i <- 0 until numNodes) {
      val n = poly.getNode(i)
      oos.writeFloat(n.x)
      oos.writeFloat(n.y)
    }
    oos.close()
    view.makeTidy()
  }

  def load(file: File): Unit = {
    val ois = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))
    if (ois.readInt != ScreenStitch.cookie) throw new IOException("Unrecognized format")
    val fileVersion = ois.readInt
    if (fileVersion > ScreenStitch.version) throw new IOException(s"File version ($fileVersion) too new")
    view.read(ois)
    // read polyline
    if (fileVersion >= 1) {
      val numNodes = ois.readInt
      val nxs = new Array[Float](numNodes)
      val nys = new Array[Float](numNodes)
      for (i <- 0 until numNodes) {
        nxs(i) = ois.readFloat
        nys(i) = ois.readFloat
      }
      poly.setValues(nxs, nys)
    }
    ois.close()
    view.makeTidy()
  }

  private def updateTitle(): Unit =
    frame.setTitle(s"Screen Stitch : ${if (docFile.isDefined) docFile.get.getName else "Untitled"}")

  private def queryLoadFile(title: String): Option[File] = {
    val dlg = new FileDialog(frame, title, FileDialog.LOAD)
    dlg.setVisible(true)
    val dir = dlg.getDirectory
    val fileName = dlg.getFile
    if (dir == null || fileName == null) {
      None
    } else {
      Some(new File(dir, fileName))
    }
  }

  private def querySaveFile(title: String, ext: String, default: Option[File] = None): Option[File] = {
    val dlg = new FileDialog(frame, title, FileDialog.SAVE)
    default.foreach { f =>
      dlg.setDirectory(f.getParent)
      dlg.setFile(f.getName)
    }
    dlg.setVisible(true)
    val dir = dlg.getDirectory
    val fileName = dlg.getFile
    if (dir == null || fileName == null) return None

    val ext2 = s".$ext"
    val file = new File(dir, s"$fileName${if (fileName.endsWith(ext2)) "" else ext2}")

    if (file.exists && !confirmOverwrite(title, file)) {
      None
    } else {
      Some(file)
    }
  }

  def saveAsDoc(): Boolean = save(querySaveFile("Save", "stitch"))

  def exportToPDF(): Unit =
    querySaveFile("Export to PDF", "pdf").foreach(view.createPDF)

  private def unionFile(n1: String, n2: String): String = {
    if (n1 == n2) return n1
    val beg = n1 zip n2
    val end = beg.reverse
    val i = beg.prefixLength { case (a, b) => a == b }
    val j = end.prefixLength { case (a, b) => a == b }
    n1.substring(0, i) + n1.substring(n1.length() - j)
  }

  private def stripExt(n: String): String = {
    val i = n.lastIndexOf('.')
    if (i < 0) n else n.substring(0, i)
  }

  def exportToPNG(): Unit = {
    val fs = view.imageFiles
    if (fs.isEmpty) return

    val f1      = fs.head
    val dir     = f1.getParentFile
    val name    = fs.tail.foldLeft(stripExt(f1.getName)) { case (n, f2) => unionFile(n, stripExt(f2.getName)) }
    val default = new File(dir, s"$name.png")

    querySaveFile("Export to PNG", "png", default = Some(default)).foreach(view.createPNG)
  }
}