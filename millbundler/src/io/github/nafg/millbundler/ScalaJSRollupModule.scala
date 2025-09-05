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
      ScalaJSRollupModule.Plugin.core("commonjs"),
      ScalaJSRollupModule.Plugin.core("json")
    )
  }

  override def jsDeps: Task.Simple[JsDeps] =
    super.jsDeps() ++
      JsDeps(devDependencies = Map("rollup" -> rollupVersion())) ++
      JsDeps.combine(rollupPlugins().map(_.toJsDep))

  def rollupConfigFilename = Task("rollup.config.js")

  def rollupOutputFormat: T[ScalaJSRollupModule.OutputFormat] = Task {
    ScalaJSRollupModule.OutputFormat.IIFE
  }

  def rollupConfig = Task.Anon { (params: BundleParams) =>
    val (input: String, output: String) = params.inputFiles match
      case Seq()          => throw new RuntimeException("No input files")
      case Seq(inputFile) =>
        ujson.Str(inputFile.toString).render() ->
          s"""
            "file": "${(Task.dest / bundleFilename()).toString}"
          """.stripMargin
      case inputFiles =>
        ujson.Arr(
          inputFiles.map(_.toString).map(ujson.Str(_)).toSeq*
        ).render() ->
          s"""
            "dir": "${Task.dest.toString}",
            "entryFileNames": "${outputEntryFileNames()}"
          """.stripMargin

    val resolveOptions =
      s"""{ modulePaths: ['${npmInstall().path.toString}/node_modules'] }"""

    s"""
    import { nodeResolve } from '@rollup/plugin-node-resolve';
    import commonjs from '@rollup/plugin-commonjs';
    import json from '@rollup/plugin-json';
    
    export default {
      input: $input,
      output: {
        format: "${rollupOutputFormat().value}",
        globals: {},
        $output
      },
      plugins: [nodeResolve($resolveOptions), commonjs(), json()]
    }\n""".stripMargin
  }

  def rollupOutputName: T[Option[String]] = Task(None)

  protected def rollupCliArgs = Task.Anon {
    rollupOutputName().toSeq.flatMap(name => Seq("--name", name))
    // ++
    //   rollupPlugins().flatMap(_.toCliArgs)
  }

  override def fastLinkJS = Task {
    val report = super.fastLinkJS()
    linkNpmInstall()
    report
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

    // val NODE_PATH =
    //   s"${npmInstall().path.toString}/node_modules;${Task.dest.toString}/node_modules"
    // println(s"NODE_PATH: $NODE_PATH")

    try
      os.call(
        Seq(
          "node",
          rollupPath.toString,
          "--config",
          configPath.toString
        ) ++
          Seq("--environment", "INCLUDE_DEPS,BUILD:production").filter(_ =>
            params.opt
          )
        // ++
        // rollupCliArgs()
        ,
        // env = Map("NODE_PATH" -> NODE_PATH),
        cwd = Task.dest
      )
    catch
      case e: Exception =>
        throw new RuntimeException("Error running rollup", e)
    end try

    // unlinkNpmInstall()

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
