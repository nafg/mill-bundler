package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.JsDeps

import geny.Generator
import mill._
import mill.define.Target
import mill.modules.Jvm
import os.Path

//noinspection ScalaWeakerAccess
trait ScalaJSWebpackModule extends ScalaJSBundleModule {
  def webpackVersion: Target[String] = "4.17.1"
  def webpackCliVersion: Target[String] = "3.1.0"
  def webpackDevServerVersion: Target[String] = "3.1.7"

  def webpackLibraryName: Target[Option[String]]

  override def jsDeps: Target[JsDeps] =
    super.jsDeps() ++
      JsDeps(
        devDependencies = Map(
          "webpack" -> webpackVersion(),
          "webpack-cli" -> webpackCliVersion(),
          "webpack-dev-server" -> webpackDevServerVersion(),
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

  override protected def bundle = T.task { params: BundleParams =>
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
      Jvm.runSubprocess(
        commandArgs = Seq(
          "node",
          webpackPath.toString,
          "--config",
          webpackConfigFilename()
        ),
        envArgs = Map("NODE_OPTIONS" -> "--openssl-legacy-provider"),
        workingDir = dir
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

    def devBundle: Target[Seq[PathRef]] = T.persistent {
      bundle().apply(BundleParams(fastOpt().path, opt = false))
    }

    def prodBundle: Target[Seq[PathRef]] = T.persistent {
      bundle().apply(BundleParams(fullOpt().path, opt = true))
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

    def writeEntrypoint = T.task { src: os.Path =>
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
      val entrypoint = writeEntrypoint().apply(fastOpt().path).path
      bundle().apply(BundleParams(entrypoint, opt = false)) :+ fastOpt()
    }

    def prodBundle: Target[Seq[PathRef]] = T {
      val entrypoint = writeEntrypoint().apply(fullOpt().path).path
      bundle().apply(BundleParams(entrypoint, opt = true)) :+ fullOpt()
    }
  }

  trait Test extends AsApplication with ScalaJSBundleModule.Test
}
