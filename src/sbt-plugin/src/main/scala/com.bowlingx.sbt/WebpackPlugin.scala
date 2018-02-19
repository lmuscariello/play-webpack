package com.bowlingx.sbt

import sbt.plugins.JvmPlugin
import sbt.{AutoPlugin, Configuration, Def, File, SettingKey, _}
import Keys._

object WebpackPlugin extends AutoPlugin {

  object autoImport {
    lazy val webpackManifest = settingKey[Seq[File]](
      "JSON file(s) that has been generated by the webpack assets plugin."
    )

    lazy val webpackAssetPrefix = settingKey[Option[String]]("Optional prefix that will be applied to each file.")
  }

  import autoImport._

  override def requires: Plugins = JvmPlugin

  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Def.Setting[_]] = buildInfoDefaultSettings ++ buildInfoScopedSettings(Compile)

  def buildInfoScopedSettings(conf: Configuration): Seq[Def.Setting[_]] = inConfig(conf)(Seq(
    watchSources ++= webpackManifest.value,
    sourceGenerators in Compile += task[Seq[File]] {
      val file = (sourceManaged in Compile).value / "com" / "bowlingx" / "webpack" / "WebpackManifest.scala"
      val manifest = webpackManifest.value
      if (manifest.nonEmpty) {
        val code = ManifestCompiler(manifest, webpackAssetPrefix.value).generate()
        IO write(file, code)
        Seq(file)
      } else {
        Seq.empty
      }
    }
  ))

  def buildInfoDefaultSettings: Seq[Setting[_]] = Seq(
    webpackManifest := file("conf/webpack-assets.json").some.filter(_.exists).toSeq,
    webpackAssetPrefix := None
  )
}
