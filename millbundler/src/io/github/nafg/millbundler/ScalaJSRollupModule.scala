package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.JsDeps

import mill.api.PathRef
import mill.define.Target
import mill.{T, Task}

//noinspection ScalaWeakerAccess
trait ScalaJSRollupModule extends ScalaJSBundleModule {
  def rollupVersion: Target[String] = "*"

  def rollupPlugins: Target[Seq[ScalaJSRollupModule.Plugin]] = T {
    Seq[ScalaJSRollupModule.Plugin](
      ScalaJSRollupModule.Plugin.core("node-resolve"),
      ScalaJSRollupModule.Plugin.core("commonjs")
    )
  }

  override def jsDeps: Target[JsDeps] =
    super.jsDeps() ++
      JsDeps(devDependencies = Map("rollup" -> rollupVersion())) ++
      JsDeps.combine(rollupPlugins().map(_.toJsDep))

  def rollupOutputFormat: Target[ScalaJSRollupModule.OutputFormat] = T {
    ScalaJSRollupModule.OutputFormat.IIFE
  }

  def rollupCliArgs = T {
    Seq(
      "--file",
      (npmInstall().path / bundleFilename()).toString(),
      "--format",
      rollupOutputFormat().value
    ) ++
      rollupPlugins().flatMap(_.toCliArgs)
  }

  override protected def bundle = Task.Anon { params: BundleParams =>
    copySources()

    val bundleName = bundleFilename()
    val copied = copyInputFile().apply(params.inputFile).path
    val dir = npmInstall().path

    val rollupPath =
      dir / "node_modules" / "rollup" / "dist" / "bin" / "rollup"

    try
      os.call(
        Seq("node", rollupPath.toString, copied.toString) ++ rollupCliArgs(),
        cwd = dir
      )
    catch {
      case e: Exception =>
        throw new RuntimeException("Error running rollup", e)
    }

    List(
      PathRef(dir / bundleName),
      PathRef(dir / (bundleName + ".map"))
    )
  }

  // noinspection ScalaUnusedSymbol
  def devBundle: Target[Seq[PathRef]] = T {
    bundle().apply(
      BundleParams(getReportMainFilePath(fastLinkJS()), opt = false)
    )
  }

  // noinspection ScalaUnusedSymbol
  def prodBundle: Target[Seq[PathRef]] = T {
    bundle().apply(
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
