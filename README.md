# ScreenStitch

[![Build Status](https://travis-ci.org/Sciss/ScreenStitch.svg?branch=main)](https://travis-ci.org/Sciss/ScreenStitch)

## statement

ScreenStitch is a desktop application written in the Scala programming language which allows to quickly assemble
multiple bitmap images and export the result as PDF or PNG. It is (C)opyright 2009-2020 by Hanns Holger Rutz. All
rights reserved. ScreenStitch is released under the
[GNU Lesser General Public License](https://raw.github.com/Sciss/ScreenStitch/master/licenses/ScreenStitch-License.txt) 
and comes with absolutely no warranties. To contact the author, send an e-mail to `contact at sciss.de`

## requirements / installation

ScreenStitch currently compiles against Scala 2.13 using sbt. To run use `sbt run`.

## getting started

To add images, drag-and-drop them from the Finder. The left and top slider scroll through the canvas. The bottom 
slider allows to zoom in and out.

If two images overlap, they can be stitched together by double-clicking on any part of their overlapping. 
An algorithm will use cross correlation to find the exact relative positions in which the images fit together. 
The idea is that you can make multiple screenshots of a larger view (e.g. a map, a book page) and fit them perfectly 
together afterwards. It implies that the overlapping shots indeed share the same pixel information. The process may 
take very long when no match is found. In that case, the process can be aborted by pressing the escape key.

To delete an image, alt+click on it.
