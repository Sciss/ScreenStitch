## ScreenStitch

### statement

ScreenStitch is a desktop application written in the Scala programming language which allows to quickly assemble multiple bitmap images and export the result as PDF or PNG. It is (C)opyright 2004-2012 by Hanns Holger Rutz. All rights reserved. ScreenStitch is released under the [GNU Lesser General Public License](https://raw.github.com/Sciss/ScreenStitch/master/licenses/ScreenStitch-License.txt) and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`

### requirements / installation

ScreenStitch currently compiles against Scala 2.9.2 using sbt 0.12. To run use `sbt run`.

### getting started

To add images, drag-and-drop them from the Finder. The left and top slider scroll throw the canvas. The bottom slider allows to zoom in and out.

If two images overlap, they can be stitched together by double-clicking on any part of their overlapping. An algorithm will use cross correlation to find the exact relative positions in which the images fit together. The idea is that you can make multiple screenshots of a larger view (e.g. a map, a book page) and fit them perfectly together afterwards. It implies that the overlapping shots indeed share the same pixel information. The process may take very long when no match is found. In that case, the process can be aborted by pressing the escape key.

To delete an image, alt+click on it.

### creating an IntelliJ IDEA project

If you want to develop the tool, you can set up an IntelliJ IDEA project, using the sbt-idea plugin yet. Have the following contents in `~/.sbt/plugins/build.sbt`:

    resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"
    
    addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")

Then to create the IDEA project, run `sbt gen-idea`.

