package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.JsDeps

import geny.Generator
import mill._
import mill.define.Target
import os.Path

//noinspection ScalaWeakerAccess
trait ScalaJSWebpackModule extends ScalaJSBundleModule {
  // renovate: datasource=npm depName=webpack
  def webpackVersion: Target[String] = "4.17.1"
  // renovate: datasource=npm depName=webpack-cli
  def webpackCliVersion: Target[String] = "3.1.0"
  // renovate: datasource=npm depName=webpack-dev-server
  def webpackDevServerVersion: Target[String] = "3.1.7"

  def webpackLibraryName: Target[Option[String]]

  override def jsDeps: Target[JsDeps] =
    super.jsDeps() ++
      JsDeps(
        devDependencies = Map(
          "webpack" -> webpackVersion(),
          "webpack-cli" -> webpackCliVersion(),
          "webpack-dev-server" -> webpackDevServerVersion(),
          // renovate: datasource=npm depName=source-map-loader
          "source-map-loader" -> "0.2.3"
        )
      )

  protected def webpackConfigJson(
      dir: os.Path,
      params: BundleParams,
      bundleFilename: String,
      libraryName: Option[String]
  ) = {
    val libraryOutputCfg =
      libraryName
        .map(n => Map("library" -> n, "libraryTarget" -> "var"))
        .getOrElse(Map.empty)
    val outputCfg =
      libraryOutputCfg ++ Map(
        "path" -> dir.toString,
        "filename" -> bundleFilename
      )
    ujson.Obj(
      "mode" -> (if (params.opt) "production" else "development"),
      "devtool" -> "source-map",
      "entry" -> (dir / params.inputFile.last).toString,
      "output" -> ujson.Obj.from(outputCfg.view.mapValues(ujson.Str)),
      "context" -> dir.toString
    )
  }

  protected def webpackConfig(
      dir: Path,
      params: BundleParams,
      bundleFilename: String,
      webpackLibraryName: Option[String]
  ) =
    "module.exports = " +
      webpackConfigJson(dir, params, bundleFilename, webpackLibraryName)
        .render(2) +
      ";\n"

  def webpackConfigFilename = T("webpack.config.js")

  def webpackEnv: Target[Map[String, String]] = T {
    val nodeMajorVersion = os
      .proc("node", "--version")
      .call()
      .out
      .text()
      .trim
      .stripPrefix("v")
      .split('.')
      .head
      .toInt
    val webpackMajorVersion = webpackVersion().split('.').head.toInt
    Option
      .when(nodeMajorVersion >= 18 && webpackMajorVersion == 4)(
        "NODE_OPTIONS" -> "--openssl-legacy-provider"
      )
      .toMap
  }

  override protected def bundle = Task.Anon { params: BundleParams =>
    copySources()
    val bundleName = bundleFilename()
    copyInputFile().apply(params.inputFile)
    val dir = npmInstall().path
    os.write.over(
      dir / webpackConfigFilename(),
      webpackConfig(dir, params, bundleName, webpackLibraryName())
    )
    val webpackPath = dir / "node_modules" / "webpack" / "bin" / "webpack"
    try
      os.call(
        Seq("node", webpackPath.toString, "--config", webpackConfigFilename()),
        env = webpackEnv(),
        cwd = dir
      )
    catch {
      case e: Exception =>
        throw new RuntimeException("Error running webpack", e)
    }

    List(
      PathRef(dir / bundleName),
      PathRef(dir / (bundleName + ".map"))
    )
  }
}
//noinspection ScalaUnusedSymbol
object ScalaJSWebpackModule {
  // noinspection ScalaUnusedSymbol,ScalaWeakerAccess
  trait AsApplication extends ScalaJSWebpackModule {
    override def webpackLibraryName: Target[Option[String]] = None

    def devBundle: Target[Seq[PathRef]] = Task(persistent = true) {
      bundle().apply(
        BundleParams(getReportMainFilePath(fastLinkJS()), opt = false)
      )
    }

    def prodBundle: Target[Seq[PathRef]] = Task(persistent = true) {
      bundle().apply(
        BundleParams(getReportMainFilePath(fullLinkJS()), opt = true)
      )
    }
  }

  // noinspection ScalaUnusedSymbol,ScalaWeakerAccess
  trait AsLibrary extends ScalaJSWebpackModule {
    private val requireRegex = """require\("([^"]*)"\)""".r

    override def webpackLibraryName = Some("app")

    protected def entrypointScript(depNames: Iterable[String]) = {
      val requiresMap =
        depNames
          .map { name => s"'$name': require('$name')" }
          .mkString(",\n      ")
      s"""
         |module.exports = {
         |  "require": (function(moduleName) {
         |    return {
         |      $requiresMap
         |    }[moduleName]
         |  })
         |}
         |""".stripMargin.trim
    }

    def writeEntrypoint = Task.Anon { src: os.Path =>
      val path = jsDepsDir().path / "entrypoint.js"
      val requires =
        os.read.lines
          .stream(src)
          .flatMap { line =>
            Generator.from(requireRegex.findAllMatchIn(line).map(_.group(1)))
          }
          .toList
      os.write.over(
        path,
        entrypointScript(requires)
      )
      PathRef(path)
    }

    def devBundle: Target[Seq[PathRef]] = T {
      val path = getReportMainFilePath(fastLinkJS())
      val entrypoint = writeEntrypoint().apply(path).path
      bundle().apply(BundleParams(entrypoint, opt = false)) :+ PathRef(path)
    }

    def prodBundle: Target[Seq[PathRef]] = T {
      val path = getReportMainFilePath(fullLinkJS())
      val entrypoint = writeEntrypoint().apply(path).path
      bundle().apply(BundleParams(entrypoint, opt = true)) :+ PathRef(path)
    }
  }

  trait Test extends AsApplication with ScalaJSBundleModule.Test
}
