import sbt.Keys._
import sbt._
import com.bowlingx.meta.BuildInfo._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import scala.xml.Group
import play.sbt.PlayImport._
import com.typesafe.sbt.SbtPgp.autoImportImpl._
import xerial.sbt.Sonatype.SonatypeKeys._

object PlayWebpackBuild {

  private[this] val projectStartYear = 2017

  private[this] val sonatypeUsername = Option(
    System.getenv("SONATYPE_USERNAME")
  ).getOrElse("")

  private[this] val sonatypePassword = Option(
    System.getenv("SONATYPE_PASSWORD")
  ).getOrElse("")

  private[this] val envPassphrase = Option(System.getenv("PGP_PASSPHRASE")).map(_.toCharArray)

  def sharedSettings: Seq[Setting[_]] = {
    Seq(
      credentials += getCredentials,
      organization := "com.bowlingx",
      scalacOptions ++= Seq(
        "-deprecation",
        "-unchecked",
        "-encoding", "UTF-8"
      )
    )
  }

  def pluginSettings: Seq[Setting[_]] = {
    sharedSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.spray" %% "spray-json" % "1.3.3"
      ))
  }

  def mainSettings: Seq[Setting[_]] = {
    sharedSettings ++ Seq(
      scalacOptions ++= Seq(
        "-target:jvm-1.8",
        "-Xfuture",
        "-Yno-adapted-args",
        "-Ywarn-dead-code",
        "-Ywarn-numeric-widen",
        "-Ywarn-value-discard",
        "-Ywarn-unused"
      )
    )
  }

  def librarySettings: Seq[Setting[_]] = Seq(
    scalaVersion := scala212Version,
    crossScalaVersions := Seq(scala212Version, scala211Version)
  )

  def playModuleSettings: Seq[Setting[_]] = {
    mainSettings ++ Seq(
      scalaVersion := scala212Version,
      crossScalaVersions := Seq(scala212Version, scala211Version),
      libraryDependencies += filters,
      libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.0.0-RC1" % Test,
      sourceGenerators in Test += task[Seq[File]] {
        val file = (sourceManaged in Test).value / "com" / "bowlingx" / "webpack" / "Manifest.scala"
        val code =
          s"""
             |package com.bowlingx.webpack
             |
             |object WebpackManifest extends WebpackManifestType {
             |  val entries:Map[String, Either[WebpackEntry, String]] = Map(
             |  "vendor" -> Left(WebpackEntry(Some("https://localhost:8080/assets/scripts/vendor.js"), None)),
             |  "polyfills" -> Left(WebpackEntry(Some("/assets/scripts/polyfills.js"), None)),
             |  "server" -> Left(WebpackEntry(Some("/assets/scripts/server.js"), None))
             |  )
             |}
     """.stripMargin
        IO write(file, code)
        Seq(file)
      }
    )
  }

  def releaseSettings: Seq[Setting[_]] = {
    Seq(
      releaseCrossBuild := false,
      releaseProcess := Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        runClean,
        releaseStepCommandAndRemaining("+test"),
        releaseStepCommandAndRemaining("+publishLocal"),
        releaseStepCommandAndRemaining("+play-webpack-plugin/scripted"),
        setReleaseVersion,
        commitReleaseVersion,
        tagRelease,
        releaseStepCommandAndRemaining("+publishSigned"),
        setNextVersion,
        commitNextVersion,
        releaseStepCommandAndRemaining("sonatypeReleaseAll"),
        pushChanges
      )
    )
  }

  def getCredentials: Credentials = {
    Credentials(
      "Sonatype Nexus Repository Manager",
      "oss.sonatype.org",
      sonatypeUsername,
      sonatypePassword
    )
  }

  def publishSettings: Seq[Setting[_]] = {
    Seq(
      pgpPassphrase := envPassphrase,
      publishTo := sonatypePublishTo.value,
      publishMavenStyle := true,
      publishArtifact in Test := false,
      packageOptions += {
        Package.ManifestAttributes(
          "Created-By" -> "Simple Build Tool",
          "Built-By" -> System.getProperty("user.name"),
          "Build-Jdk" -> System.getProperty("java.version"),
          "Specification-Title" -> name.value,
          "Specification-Vendor" -> "David Heidrich",
          "Specification-Version" -> version.value,
          "Implementation-Title" -> name.value,
          "Implementation-Version" -> version.value,
          "Implementation-Vendor-Id" -> organization.value,
          "Implementation-Vendor" -> "David Heidrich",
          "Implementation-Url" -> "https://github.com/BowlingX/play-webpack"
        )
      },
      homepage := Some(url("https://github.com/BowlingX/play-webpack")),
      startYear := Some(projectStartYear),
      licenses := Seq(("MIT", url("https://raw.githubusercontent.com/BowlingX/play-webpack/master/LICENSE.md"))),
      pomExtra := {
        Group(
          <scm>
            <connection>scm:git:git://github.com/BowlingX/play-webpack.git</connection>
            <developerConnection>scm:git:git@github.com:BowlingX/play-webpack.git</developerConnection>
            <url>https://github.com/BowlingX/play-webpack</url>
          </scm>
          <developers>
            <developer>
              <id>BowlingX</id>
              <name>David Heidrich</name>
              <url>http://bowlingx.com</url>
            </developer>
          </developers>
        )
      }
    )
  }

}
