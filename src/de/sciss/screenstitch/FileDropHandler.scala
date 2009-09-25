package de.sciss.screenstitch

import _root_.java.awt.datatransfer.{ DataFlavor, Transferable }
import _root_.java.awt.image.{ BufferedImage }
import _root_.java.io.{ File }
import _root_.javax.swing.{ JComponent, TransferHandler }

class FileDropHandler( acceptAction: (File) => Unit )
extends TransferHandler {
	
	private val fileFlavor = DataFlavor.javaFileListFlavor
	
	override def canImport( c: JComponent, f: Array[ DataFlavor ]) : Boolean = {
		f.contains( fileFlavor )
	}
	
	override def importData( c: JComponent, t: Transferable ) : Boolean = {
		if( !t.isDataFlavorSupported( fileFlavor )) return false
		
		val data = t.getTransferData( fileFlavor ).asInstanceOf[ java.util.List[ File ]]
		import scala.collection.JavaConversions._
		data.foreach( acceptAction( _ ))
		true
	}
}
