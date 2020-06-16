name          := "ScreenStitch"
version       := "0.12.0-SNAPSHOT"
organization  := "de.sciss"
scalaVersion  := "2.13.2"
description   := "Arrange various screenshots (typically from maps) and glue the parts together"
homepage      := Some(url(s"https://git.iem.at/sciss/${name.value}"))
licenses      := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt"))

libraryDependencies +=
  "com.itextpdf" % "itextpdf" % "5.5.13.1"

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Xsource:2.13", "-Xlint")

// ---- publishing ----

publishMavenStyle := true

publishTo :=
  Some(if (isSnapshot.value)
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := { val n = name.value
<scm>
  <url>git@git.iem.at:sciss/{n}.git</url>
  <connection>scm:git:git@git.iem.at:sciss/{n}.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>
}
