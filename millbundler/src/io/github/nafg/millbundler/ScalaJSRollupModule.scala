package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.JsDeps

import mill.api.PathRef
import mill.*
import scala.compiletime.ops.double

//noinspection ScalaWeakerAccess
trait ScalaJSRollupModule extends ScalaJSBundleModule {
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

  def rollupOutputFormat: Task.Simple[ScalaJSRollupModule.OutputFormat] = Task {
    ScalaJSRollupModule.OutputFormat.IIFE
  }

  def rollupOutputName: Task.Simple[Option[String]] = Task(None)

  protected def rollupCliArgs = Task.Anon {
    Seq(
      "--file",
      (Task.dest / bundleFilename()).toString(),
      "--format",
      rollupOutputFormat().value
    ) ++
      rollupOutputName().toSeq.flatMap(name => Seq("--name", name)) ++
      rollupPlugins().flatMap(_.toCliArgs)
  }

  override protected def bundle = Task.Anon { (params: BundleParams) =>
    val copied = copyInputFile.apply()(params.inputFiles)

    for path <- os.list(npmInstall().path) do
      os.remove(Task.dest / path.last)
      os.symlink(Task.dest / path.last, path)

    try
      os.call(
        Seq(
          "npx",
          "rollup",
          copied.head.path.toString
        ) ++ rollupCliArgs(),
        cwd = Task.dest
      )
    catch {
      case e: Exception =>
        throw new RuntimeException("Error running rollup", e)
    }

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
}
object ScalaJSRollupModule {
  case class Plugin(
      packageName: String,
      version: String = "*",
      config: Option[String] = None
  ) {
    def toJsDep = JsDeps(devDependencies = Map(packageName -> version))
    def toCliArgs = Seq("--plugin", packageName + config.fold("")("=" + _))
  }
  object Plugin {
    def core(name: String) = Plugin(s"@rollup/plugin-$name")

    implicit val rw: upickle.default.ReadWriter[Plugin] =
      upickle.default.macroRW[Plugin]
  }

  case class OutputFormat(value: String)
  // noinspection ScalaUnusedSymbol,ScalaWeakerAccess
  object OutputFormat {
    val AMD = OutputFormat("amd")
    val CJS = OutputFormat("cjs")
    val ES = OutputFormat("es")
    val IIFE = OutputFormat("iife")
    val UMD = OutputFormat("umd")
    val System = OutputFormat("system")

    implicit val rw: upickle.default.ReadWriter[OutputFormat] =
      upickle.default.macroRW[OutputFormat]
  }

  // noinspection ScalaWeakerAccess
  trait Test extends ScalaJSRollupModule with ScalaJSBundleModule.Test
}
