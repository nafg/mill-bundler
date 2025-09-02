package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.JsDeps

import geny.Generator
import mill.*
import os.Path

//noinspection ScalaWeakerAccess
trait ScalaJSWebpackModule extends ScalaJSBundleModule {
  // renovate: datasource=npm depName=webpack
  def webpackVersion: Task.Simple[String] = "5.101.3"
  // renovate: datasource=npm depName=webpack-cli
  def webpackCliVersion: Task.Simple[String] = "6.0.1"
  // renovate: datasource=npm depName=webpack-dev-server
  def webpackDevServerVersion: Task.Simple[String] = "5.2.2"

  def webpackLibraryName: Task.Simple[Option[String]]

  override def jsDeps: Task.Simple[JsDeps] =
    super.jsDeps() ++
      JsDeps(
        devDependencies = Map(
          "webpack" -> webpackVersion(),
          "webpack-cli" -> webpackCliVersion(),
          "webpack-dev-server" -> webpackDevServerVersion(),
          // renovate: datasource=npm depName=source-map-loader
          "source-map-loader" -> "5.0.0"
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
      "entry" -> (dir / params.inputFiles.head.last).toString,
      "output" -> ujson.Obj.from(outputCfg.view.mapValues(ujson.Str(_))),
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

  def webpackConfigFilename = Task("webpack.config.js")

  def webpackEnv: Task.Simple[Map[String, String]] = Task {
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

  override protected def bundle = Task.Anon { (params: BundleParams) =>
    val inputfilePathRef = copyInputFile.apply()(params.inputFiles)

    val configPath = Task.dest / webpackConfigFilename()
    os.write.over(
      configPath,
      webpackConfig(Task.dest, params, bundleFilename(), webpackLibraryName())
    )
    val webpackPath =
      npmInstall().path / "node_modules" / "webpack" / "bin" / "webpack"

    try
      os.call(
        Seq("node", webpackPath.toString, "--config", configPath.toString),
        env = webpackEnv(),
        cwd = Task.dest
      )
    catch {
      case e: Exception =>
        throw new RuntimeException("Error running webpack", e)
    }

    List(
      PathRef(Task.dest / bundleFilename()),
      PathRef(Task.dest / (bundleFilename() + ".map"))
    )
  }
}
//noinspection ScalaUnusedSymbol
object ScalaJSWebpackModule {
  // noinspection ScalaUnusedSymbol,ScalaWeakerAccess
  trait AsApplication extends ScalaJSWebpackModule {
    override def webpackLibraryName: Task.Simple[Option[String]] = Task(None)

    def devBundle: Task.Simple[Seq[PathRef]] = Task(persistent = true) {
      bundle.apply()(
        BundleParams(getReportMainFilePath(fastLinkJS()), opt = false)
      )
    }

    def prodBundle: Task.Simple[Seq[PathRef]] = Task(persistent = true) {
      bundle.apply()(
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

    def writeEntrypoint = Task.Anon { (src: os.Path) =>
      val path = Task.dest / "entrypoint.js"
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

    def devBundle: Task.Simple[Seq[PathRef]] = Task {
      val path = getReportMainFilePath(fastLinkJS())
      val entrypoint = writeEntrypoint.apply()(path).path
      bundle.apply()(BundleParams(entrypoint, opt = false)) :+ PathRef(path)
    }

    def prodBundle: Task.Simple[Seq[PathRef]] = Task {
      val path = getReportMainFilePath(fullLinkJS())
      val entrypoint = writeEntrypoint.apply()(path).path
      bundle.apply()(BundleParams(entrypoint, opt = true)) :+ PathRef(path)
    }
  }

  trait Test extends AsApplication with ScalaJSBundleModule.Test
}
