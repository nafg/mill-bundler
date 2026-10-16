package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.JsDeps

import mill.*
import mill.api.PathRef
import mill.scalajslib.api.ModuleKind

//noinspection ScalaWeakerAccess
trait ScalaJSRollupModule extends ScalaJSBundleModule:
  def rollupVersion: Task.Simple[String] = "*"

  def rollupPlugins: Task.Simple[Seq[ScalaJSRollupModule.Plugin]] = Task {
    Seq[ScalaJSRollupModule.Plugin](
      ScalaJSRollupModule.Plugin.core("node-resolve"),
      ScalaJSRollupModule.Plugin.core("commonjs"),
      ScalaJSRollupModule.Plugin.core("json")
    )
  }

  override def jsDeps: Task.Simple[JsDeps] =
    super.jsDeps() ++
      JsDeps(devDependencies = Map("rollup" -> rollupVersion())) ++
      JsDeps.combine(rollupPlugins().map(_.toJsDep))

  def rollupConfigFilename = Task("rollup.config.mjs")

  def rollupOutputFormat: T[ScalaJSRollupModule.OutputFormat] = Task {
    ScalaJSRollupModule.OutputFormat.IIFE
  }

  def rollupConfig = Task.Anon { (params: BundleParams) =>
    val inputFiles = params.inputFiles.toSeq
    val (input: String, output: String) = inputFiles match
      case Seq()          => throw new RuntimeException("No input files")
      case Seq(inputFile) =>
        ujson.Str(inputFile.toString).render() ->
          s"""
            "file": ${ujson.Str((Task.dest / bundleFilename()).toString).render()}
          """.stripMargin
      case files =>
        ujson.Arr(
          files.map(_.toString).map(ujson.Str(_)).toSeq*
        ).render() ->
          s"""
            "dir": ${ujson.Str(Task.dest.toString).render()},
            "entryFileNames": ${ujson.Str(outputEntryFileNames()).render()}
          """.stripMargin

    val outputFormat =
      if inputFiles.size == 1 then rollupOutputFormat().value
      else
        moduleKind() match
          case ModuleKind.ESModule       => ScalaJSRollupModule.OutputFormat.ES.value
          case ModuleKind.CommonJSModule =>
            ScalaJSRollupModule.OutputFormat.CJS.value
          case ModuleKind.NoModule => ScalaJSRollupModule.OutputFormat.ES.value

    val resolveOptions =
      s"""{ modulePaths: [${ujson.Str((npmInstall().path / "node_modules").toString).render()}] }"""

    val plugins = rollupPlugins().distinctBy(_.packageName)
    val imports = plugins.map(_.rollupImport).mkString("\n    ")
    val pluginCalls =
      plugins.map(_.rollupCall(resolveOptions)).mkString(", ")
    val outputName =
      if inputFiles.size == 1 then
        rollupOutputName().fold("")(name =>
          s""""name": ${ujson.Str(name).render()},\n        """
        )
      else ""

    s"""
    $imports
    
    export default {
      input: $input,
      output: {
        format: "$outputFormat",
        sourcemap: true,
        globals: {},
        $outputName$output
      },
      plugins: [$pluginCalls]
    }\n""".stripMargin
  }

  def rollupOutputName: T[Option[String]] = Task(None)

  override protected def bundle = Task.Anon { (params: BundleParams) =>
    val configPath = Task.dest / rollupConfigFilename()

    os.write.over(
      configPath,
      rollupConfig()(params)
    )

    linkNodeModules()

    val rollupPath =
      npmInstall().path / "node_modules" / "rollup" / "dist" / "bin" / "rollup"

    try
      os.call(
        Seq(
          "node",
          rollupPath.toString,
          "--config",
          configPath.toString
        ),
        cwd = Task.dest
      )
    catch
      case e: Exception =>
        throw new RuntimeException("Error running rollup", e)
    end try

    bundlePaths()(params.inputFiles).toSeq
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

    def rollupImport: String = packageName match
      case "@rollup/plugin-node-resolve" =>
        s"import { nodeResolve } from '$packageName';"
      case _ =>
        s"import ${Plugin.bindingName(packageName)} from '$packageName';"

    def rollupCall(resolveOptions: String): String = packageName match
      case "@rollup/plugin-node-resolve" =>
        s"nodeResolve(${config.getOrElse(resolveOptions)})"
      case _ =>
        val binding = Plugin.bindingName(packageName)
        config.fold(s"$binding()")(c => s"$binding($c)")

  object Plugin:
    def core(name: String) = Plugin(s"@rollup/plugin-$name")

    private def bindingName(packageName: String): String =
      packageName match
        case "@rollup/plugin-commonjs" => "commonjs"
        case "@rollup/plugin-json"     => "json"
        case s"@rollup/plugin-$name"   => name.replace("-", "")
        case _ =>
          packageName.split('/').last.replaceAll("[^a-zA-Z0-9]", "")

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
