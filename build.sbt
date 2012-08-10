name := "ScreenStitch"

version := "0.11"

organization := "de.sciss"

scalaVersion := "2.9.2"

description := "Arrange various screenshots (typically from maps) and glue the parts together"

homepage := Some( url( "https://github.com/Sciss/ScreenStitch" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

libraryDependencies ++= Seq(
   "com.itextpdf" % "itextpdf" % "5.1.1"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" )

// ---- publishing ----

publishMavenStyle := true

publishTo <<= version { (v: String) =>
   Some( if( v.endsWith( "-SNAPSHOT" ))
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra :=
<scm>
  <url>git@github.com:Sciss/ScreenStitch.git</url>
  <connection>scm:git:git@github.com:Sciss/ScreenStitch.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>

// ---- packaging ----

seq( appbundle.settings: _* )

appbundle.icon := Some( file( "application.png" ))

appbundle.target <<= baseDirectory


