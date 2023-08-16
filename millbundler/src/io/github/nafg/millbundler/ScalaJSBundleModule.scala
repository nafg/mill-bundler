package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.ScalaJSNpmModule

import mill.define.Task
import mill.scalajslib.TestScalaJSModule
import mill.scalajslib.api.{ModuleKind, Report}
import mill.{PathRef, T}

//noinspection ScalaWeakerAccess
trait ScalaJSBundleModule extends ScalaJSNpmModule {
  protected def getReportMainFilePath(report: Report): os.Path =
    report.dest.path / report.publicModules.head.jsFileName

  def bundleFilename = T("out-bundle.js")

  def copyInputFile = T.task { inputFile: os.Path =>
    val dir = jsDepsDir().path
    val copied = dir / inputFile.last
    if (inputFile != copied)
      os.copy.over(inputFile, copied)
    PathRef(copied)
  }

  protected def bundle: Task[BundleParams => Seq[PathRef]]
}
//noinspection ScalaWeakerAccess
object ScalaJSBundleModule {
  // noinspection ScalaUnusedSymbol
  trait Test extends TestScalaJSModule { this: ScalaJSBundleModule =>
    override def fastLinkJSTest = T {
      val report = super.fastLinkJSTest()
      bundle().apply(
        BundleParams(
          report.dest.path / report.publicModules.head.jsFileName,
          opt = false
        )
      )
      val filename = bundleFilename()
      val modules =
        Report.Module(
          moduleID = "main",
          jsFileName = filename,
          sourceMapName = Some(filename + ".map"),
          moduleKind = ModuleKind.NoModule
        ) +:
          report.publicModules.toSeq.drop(1)
      Report(publicModules = modules, dest = jsDepsDir())
    }
  }
}
