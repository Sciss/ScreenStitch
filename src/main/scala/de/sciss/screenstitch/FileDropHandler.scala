/*
 * FileDropHandler.scala
 * (ScreenStitch)
 *
 * Copyright (C) 2009-2020 Hanns Holger Rutz. All rights reserved.
 *
 * Published under the GNU Lesser General Public License (LGPL) v3
 */

package de.sciss.screenstitch

import java.awt.datatransfer.{DataFlavor, Transferable}
import java.io.File

import javax.swing.{JComponent, TransferHandler}

import scala.jdk.CollectionConverters._

class FileDropHandler(acceptAction: File => Unit)
  extends TransferHandler {

  private val fileFlavor = DataFlavor.javaFileListFlavor

  override def canImport(c: JComponent, f: Array[DataFlavor]): Boolean = f.contains(fileFlavor)

  override def importData(c: JComponent, t: Transferable): Boolean = {
    if (!t.isDataFlavorSupported(fileFlavor)) return false

    val data = t.getTransferData(fileFlavor).asInstanceOf[java.util.List[File]]
    data.asScala.foreach(acceptAction(_))
    true
  }
}
