package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.JsDeps

import geny.Generator
import os.Path

import mill.*

//noinspection ScalaWeakerAccess
trait ScalaJSWebpackModule extends ScalaJSBundleModule:
  // renovate: datasource=npm depName=webpack
  def webpackVersion: Task.Simple[String] = "5.101.3"
  // renovate: datasource=npm depName=webpack-cli
  def webpackCliVersion: Task.Simple[String] = "6.0.1"
  // renovate: datasource=npm depName=webpack-dev-server
  def webpackDevServerVersion: Task.Simple[String] = "5.2.2"

  def webpackLibraryName: Task.Simple[Option[String]]

  given [T <: "array-push" | "commonjs" | "module"]
      : upickle.default.ReadWriter[T] =
    val x = upickle.default
      .readwriter[ujson.Value]
      .bimap["array-push" | "commonjs" | "module"](
        {
          case v: "array-push" => upickle.default.writeJs(v)
          case v: "commonjs"   => upickle.default.writeJs(v)
          case v: "module"     => upickle.default.writeJs(v)
        },
        json =>
          json.strOpt
            .collect[("array-push" | "commonjs" | "module")] {
              case "array-push" => "array-push"
              case "commonjs"   => "commonjs"
              case "module"     => "module"
            }
            .getOrElse(throw new Exception("Invalid value"))
      )
      .narrow[T]
    upickle.default.ReadWriter.join[T](using x, x)
  end given

  def webpackChunkFormat: T["array-push" | "commonjs" | "module"] = "module"

  override def jsDeps: Task.Simple[JsDeps] =
    super.jsDeps() ++
      JsDeps(
        dependencies = Map(
          "crypto-browserify" -> "*"
        ),
        devDependencies = Map(
          "webpack" -> webpackVersion(),
          "webpack-cli" -> webpackCliVersion(),
          "webpack-dev-server" -> webpackDevServerVersion(),
          // renovate: datasource=npm depName=source-map-loader
          "source-map-loader" -> "5.0.0"
        )
      )

  protected def webpackConfigJson = Task.Anon { (params: BundleParams) =>
    val libraryOutputCfg =
      webpackLibraryName()
        .map(n => ujson.Obj("library" -> n, "libraryTarget" -> "var"))
        .getOrElse(ujson.Obj())
    val (entryCfg, outputPaths) = params.inputFiles match
      case Seq()          => throw new RuntimeException("No input files")
      case Seq(inputFile) =>
        ujson.Str(inputFile.toString) ->
          ujson.Obj(
            "path" -> Task.dest.toString,
            "filename" -> bundleFilename()
          )
      case inputFiles =>
        ujson.Arr(inputFiles.map(_.toString).map(ujson.Str(_)).toSeq*) ->
          ujson.Obj(
            "path" -> Task.dest.toString,
            "filename" -> outputEntryFileNames()
          )
    val outputCfg = ujson.Obj(
      "chunkFormat" -> webpackChunkFormat(),
      (outputPaths.value ++ libraryOutputCfg.value).toSeq*
    )
    ujson.Obj(
      "mode" -> (if params.opt then "production" else "development"),
      "devtool" -> "source-map",
      "entry" -> entryCfg,
      "output" -> outputCfg,
      "context" -> Task.dest.toString,
      "resolve" -> ujson.Obj(
        "fallback" -> ujson.Obj(
          "crypto" -> ujson.Bool(false)
        )
      )
      // "module" -> ujson.Obj(
      //   "rules" -> ujson.Arr(
      //     ujson.Obj(
      //       "test" -> ujson.Str("\\.js$"),
      //       "use" -> ujson.Arr(ujson.Str("source-map-loader")),
      //       "enforce" -> ujson.Str("pre")
      //     )
      //   )
      // )
    )
  }

  protected def webpackConfig = Task.Anon { (params: BundleParams) =>
    "module.exports = " +
      webpackConfigJson()(params)
        .render(2) +
      ";\n"
  }

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

    val configPath = Task.dest / webpackConfigFilename()

    os.write.over(
      configPath,
      webpackConfig()(params)
    )

    linkNpmInstall()

    val webpackPath =
      Task.dest / "node_modules" / "webpack" / "bin" / "webpack"

    try
      os.call(
        Seq("node", webpackPath.toString, "--config", configPath.toString),
        env = webpackEnv(),
        cwd = Task.dest
      )
    catch
      case e: Exception =>
        throw new RuntimeException("Error running webpack", e)

    List(
      PathRef(Task.dest / bundleFilename()),
      PathRef(Task.dest / (bundleFilename() + ".map"))
    )
  }

end ScalaJSWebpackModule

//noinspection ScalaUnusedSymbol
object ScalaJSWebpackModule:

  // noinspection ScalaUnusedSymbol,ScalaWeakerAccess
  trait AsApplication extends ScalaJSWebpackModule:
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

  end AsApplication

  // noinspection ScalaUnusedSymbol,ScalaWeakerAccess
  trait AsLibrary extends ScalaJSWebpackModule:
    private val requireRegex = """require\("([^"]*)"\)""".r

    override def webpackLibraryName = Some("app")

    protected def entrypointScript(depNames: Iterable[String]) =
      val requiresMap =
        depNames
          .map(name => s"'$name': require.resolve('$name')")
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
    end entrypointScript

    def writeEntrypoint = Task.Anon { (srcs: Iterable[os.Path]) =>
      val path = Task.dest / "entrypoint.js"
      val requires =
        srcs.flatMap(src =>
          os.read.lines
            .stream(src)
            .flatMap { line =>
              Generator.from(requireRegex.findAllMatchIn(line).map(_.group(1)))
            }
            .toList
        )

      os.write.over(
        path,
        entrypointScript(requires)
      )
      PathRef(path)
    }

    def devBundle: Task.Simple[Seq[PathRef]] = Task {
      val paths = getReportMainFilePath(fastLinkJS())
      val entrypoint = writeEntrypoint.apply()(paths).path
      bundle.apply()(BundleParams(entrypoint, opt = false)) ++ paths.map(
        PathRef(_)
      )
    }

    def prodBundle: Task.Simple[Seq[PathRef]] = Task {
      val paths = getReportMainFilePath(fullLinkJS())
      val entrypoint = writeEntrypoint.apply()(paths).path
      bundle.apply()(BundleParams(entrypoint, opt = true)) ++ paths.map(
        PathRef(_)
      )
    }

  end AsLibrary

  trait Test extends AsApplication with ScalaJSBundleModule.Test
end ScalaJSWebpackModule
