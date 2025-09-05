package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.JsDeps

import mill.*
import mill.api.PathRef

//noinspection ScalaWeakerAccess
trait ScalaJSRollupModule extends ScalaJSBundleModule:
  def rollupVersion: Task.Simple[String] = "*"

  def rollupPlugins: Task.Simple[Seq[ScalaJSRollupModule.Plugin]] = Task {
    Seq[ScalaJSRollupModule.Plugin](
      ScalaJSRollupModule.Plugin.core("node-resolve"),
      ScalaJSRollupModule.Plugin.core("commonjs")
    )
  }

  override def jsDeps: Task.Simple[JsDeps] =
    super.jsDeps() ++
      JsDeps(devDependencies = Map("rollup" -> rollupVersion())) ++
      JsDeps.combine(rollupPlugins().map(_.toJsDep))

  protected def rollupConfigJson = Task.Anon { (params: BundleParams) =>
    val (input, output) = params.inputFiles match
      case Seq()          => throw new RuntimeException("No input files")
      case Seq(inputFile) =>
        ujson.Str(inputFile.toString) ->
          ujson.Obj(
            "file" -> (Task.dest / bundleFilename()).toString
          )
      case inputFiles =>
        ujson.Arr(inputFiles.map(_.toString).map(ujson.Str(_)).toSeq*) ->
          ujson.Obj(
            "dir" -> Task.dest.toString,
            "entryFileNames" -> outputEntryFileNames()
          )

    ujson.Obj(
      "input" -> input,
      "output" -> ujson.Obj(
        "format" -> rollupOutputFormat().value,
        output.value.toSeq*
      )
    )
  }

  def rollupConfigFilename = Task("rollup.config.js")

  def rollupOutputFormat: Task.Simple[ScalaJSRollupModule.OutputFormat] = Task {
    ScalaJSRollupModule.OutputFormat.IIFE
  }

  def rollupConfig = Task.Anon { (params: BundleParams) =>
    "export default " +
      rollupConfigJson()(params).render(2) + ";\n"
  }

  def rollupOutputName: Task.Simple[Option[String]] = Task(None)

  protected def rollupCliArgs = Task.Anon {
    rollupOutputName().toSeq.flatMap(name => Seq("--name", name)) ++
      rollupPlugins().flatMap(_.toCliArgs)
  }

  override protected def bundle = Task.Anon { (params: BundleParams) =>

    val configPath = Task.dest / rollupConfigFilename()

    os.write.over(
      configPath,
      rollupConfig()(params)
    )

    linkNpmInstall()

    val rollupPath =
      npmInstall().path / "node_modules" / "rollup" / "dist" / "bin" / "rollup"

    try
      os.call(
        Seq(
          "node",
          rollupPath.toString,
          "--config",
          configPath.toString
        ) ++ rollupCliArgs(),
        cwd = Task.dest
      )
    catch
      case e: Exception =>
        throw new RuntimeException("Error running rollup", e)
    end try

    List(
      PathRef(Task.dest / bundleFilename()),
      PathRef(Task.dest / (bundleFilename() + ".map"))
    )
  }

  // noinspection ScalaUnusedSymbol
  def devBundle: Task.Simple[Seq[PathRef]] = Task {
    bundle.apply()(
      BundleParams(getReportMainFilePath(fastLinkJS()), opt = false)
    )
  }

  // noinspection ScalaUnusedSymbol
  def prodBundle: Task.Simple[Seq[PathRef]] = Task {
    bundle.apply()(
      BundleParams(getReportMainFilePath(fullLinkJS()), opt = true)
    )
  }

end ScalaJSRollupModule

object ScalaJSRollupModule:

  case class Plugin(
      packageName: String,
      version: String = "*",
      config: Option[String] = None
  ):
    def toJsDep = JsDeps(devDependencies = Map(packageName -> version))
    def toCliArgs = Seq("--plugin", packageName + config.fold("")("=" + _))

  object Plugin:
    def core(name: String) = Plugin(s"@rollup/plugin-$name")

    implicit val rw: upickle.default.ReadWriter[Plugin] =
      upickle.default.macroRW[Plugin]

  case class OutputFormat(value: String)

  // noinspection ScalaUnusedSymbol,ScalaWeakerAccess
  object OutputFormat:
    val AMD = OutputFormat("amd")
    val CJS = OutputFormat("cjs")
    val ES = OutputFormat("es")
    val IIFE = OutputFormat("iife")
    val UMD = OutputFormat("umd")
    val System = OutputFormat("system")

    implicit val rw: upickle.default.ReadWriter[OutputFormat] =
      upickle.default.macroRW[OutputFormat]

  end OutputFormat

  // noinspection ScalaWeakerAccess
  trait Test extends ScalaJSRollupModule with ScalaJSBundleModule.Test
end ScalaJSRollupModule
