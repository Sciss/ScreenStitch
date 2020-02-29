name          := "ScreenStitch"
version       := "0.12.0-SNAPSHOT"
organization  := "de.sciss"
scalaVersion  := "2.12.10"
description   := "Arrange various screenshots (typically from maps) and glue the parts together"
homepage      := Some(url(s"https://github.com/Sciss/${name.value}"))
licenses      := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt"))

libraryDependencies +=
  "com.itextpdf" % "itextpdf" % "5.5.13"

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Xfuture", "-Xlint")

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
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>
}
