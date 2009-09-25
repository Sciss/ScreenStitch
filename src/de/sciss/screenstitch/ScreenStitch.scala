package de.sciss.screenstitch

import _root_.java.awt.{ BorderLayout, Dimension, EventQueue, FileDialog, Toolkit }
import _root_.java.awt.event.{ ActionEvent, InputEvent, KeyEvent, WindowAdapter,
							   WindowEvent }
import _root_.java.io.{ File, IOException }
import _root_.javax.swing.{ AbstractAction, Action, JFrame, JMenu, JMenuBar, JMenuItem,
							JOptionPane, JScrollPane, JSeparator, JSlider, KeyStroke,
							WindowConstants }
import _root_.javax.swing.event.{ ChangeEvent, ChangeListener }

object ScreenStitch {
	def main( args: Array[ String ]) {
		System.setProperty( "apple.laf.useScreenMenuBar", "true" )
		EventQueue.invokeLater( new Runnable { def run { new ScreenStitch }})
	}

	def linexp( x: Double, inLo: Double, inHi: Double, outLo: Double, outHi: Double ) : Double = {
		Math.pow( outHi / outLo, (x - inLo) / (inHi - inLo) ) * outLo
	}
}

class ScreenStitch {
	
	private val frame	= new JFrame()
	private val view	= new StitchView
	private var docFile: Option[ File ] = None
	
	// constructor
	{
		val cp = frame.getContentPane
		val ggScroll = new JScrollPane( view )
		view.setViewPort( ggScroll.getViewport )
		view.setDirtyAction( b => setDirty( b ))
		
		cp.add( ggScroll, BorderLayout.CENTER )
		ggScroll.setPreferredSize( new Dimension( 400, 400 ))
		
		val ggZoom = new JSlider( 0, 0x10000 )
		ggZoom.setValue( ggZoom.getMaximum )
		ggZoom.addChangeListener( new ChangeListener {
			def stateChanged( e: ChangeEvent ) {
				view.setZoom( ScreenStitch.linexp( ggZoom.getValue, 0, 0x10000, 0.125, 1 ).toFloat )
			}
		})
		
		cp.add( ggZoom, BorderLayout.SOUTH )
		
		val meta = Toolkit.getDefaultToolkit.getMenuShortcutKeyMask()
		val mb = new JMenuBar
		val m  = new JMenu( "File" )
		mb.add( m )
		m.add( new JMenuItem( new AbstractAction( "New" ) {
			putValue( Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_N, meta ))
			def actionPerformed( e: ActionEvent ) {
				clearDoc
			}
		}))
		m.add( new JMenuItem( new AbstractAction( "Open..." ) {
			putValue( Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_O, meta ))
			def actionPerformed( e: ActionEvent ) {
				openDoc
			}
		}))
		m.add( new JSeparator )
		m.add( new JMenuItem( new AbstractAction( "Save" ) {
			putValue( Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_S, meta ))
			def actionPerformed( e: ActionEvent ) {
				saveDoc
			}
		}))
		m.add( new JMenuItem( new AbstractAction( "Save As..." ) {
			putValue( Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke( KeyEvent.VK_N, meta | InputEvent.SHIFT_MASK ))
			def actionPerformed( e: ActionEvent ) {
				saveAsDoc
			}
		}))
		m.add( new JMenuItem( new AbstractAction( "Export to PDF..." ) {
			def actionPerformed( e: ActionEvent ) {
				exportToPDF
			}
		}))
		
		frame.setJMenuBar( mb )
		frame.pack
		frame.setLocationRelativeTo( null )
		frame.setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE )
		frame.addWindowListener( new WindowAdapter {
			override def windowClosing( e: WindowEvent ) {
				if( !view.isDirty || confirmLoss( "Quit" )) {
					System.exit( 0 )
				}
			}
		})
		updateTitle
		frame.setVisible( true )
		frame.toFront
	}
	
	private def setDirty( b: Boolean ) {
		val rp = frame.getRootPane
		rp.putClientProperty( "windowModified", new java.lang.Boolean( b ))
	}
	
	private def confirmLoss( title: String ) : Boolean = {
		val options = List[ Object ]( "Save", "Cancel", "Don't Save" ).toArray
		val res = JOptionPane.showOptionDialog( frame,
			"The document contains unsaved changes.",
			title, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
			null, options, options( 1 ))
//		println( res )
		if( res == 0 ) {
			saveDoc
		} else res == 2
	}
	
	private def confirmOverwrite( title: String, f: File ) : Boolean = {
		val res = JOptionPane.showConfirmDialog( frame,
			"The file\n" + f.getAbsolutePath + "\nalready exists. Overwrite?",
			title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE )
		
		println( res )
		res == JOptionPane.YES_OPTION
	}

	def clearDoc {
		if( view.isDirty && !confirmLoss( "New" )) return
		view.clear
		docFile = None
		updateTitle
	}
	
	def openDoc {
		if( view.isDirty && !confirmLoss( "Open" )) return
		val df = queryLoadFile( "Open" )
		if( df.isDefined ) {
			try {
				view.load( df.get )
				docFile = df
				updateTitle
			}
			catch {
				case e => displayError( "Open", e )
			}
		}
	}
	
	private def displayError( title: String, e: Throwable ) {
		val msg = e.getClass.toString + "\n" +
			(if( e.getMessage == null ) "" else e.getMessage + "\n")
			
		JOptionPane.showMessageDialog( frame, msg, title, JOptionPane.ERROR_MESSAGE )
	}
	
	def saveDoc : Boolean = {
		if( docFile.isEmpty ) {
			saveAsDoc
		} else {
			save( docFile )
		}
	}
		
	private def save( df: Option[ File ]) : Boolean = {
		if( df.isEmpty ) return false
		try {
			view.save( df.get )
			docFile = df
			updateTitle
			true
		}
		catch {
			case e => { displayError( "Save", e ); false }
		}
	}
	
	private def updateTitle {
		frame.setTitle( "Screen Stitch : " + (if( docFile.isDefined ) docFile.get.getName else "Untitled") )
	}
	
	private def queryLoadFile( title: String ) : Option[ File ] = {
		val dlg = new FileDialog( frame, title, FileDialog.LOAD )
		dlg.setVisible( true )
		val dir      = dlg.getDirectory
		val fileName = dlg.getFile
		if( dir == null || fileName == null ) {
			None
		} else {
			Some( new File( dir, fileName ))
		}
	}
		
	private def querySaveFile( title: String, ext: String ) : Option[ File ] = {
		val dlg = new FileDialog( frame, title, FileDialog.SAVE )
		dlg.setVisible( true )
		val dir      = dlg.getDirectory
		val fileName = dlg.getFile
		if( dir == null || fileName == null ) return None
	
		val ext2 = "." + ext
		val file = new File( dir, fileName +
		                     (if( fileName.endsWith( ext2 )) "" else ext2) )
		
		if( file.exists && !confirmOverwrite( title, file )) {
			None
		} else {
			Some( file )
		}
	}
	
	def saveAsDoc : Boolean  = {
		save( querySaveFile( "Save", "stitch" ))
	}
	
	def exportToPDF {
	    querySaveFile( "Export to PDF", "pdf" ).foreach( view.createPDF( _ ))
	}
}