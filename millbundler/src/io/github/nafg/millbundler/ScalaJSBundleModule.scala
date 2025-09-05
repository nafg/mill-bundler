package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.ScalaJSNpmModule

import mill.*
import mill.PathRef
import mill.T
import mill.scalajslib.TestScalaJSModule
import mill.scalajslib.api.ModuleKind
import mill.scalajslib.api.Report

//noinspection ScalaWeakerAccess
trait ScalaJSBundleModule extends ScalaJSNpmModule:

  protected def getReportMainFilePath(report: Report): Iterable[os.Path] =
    report.publicModules.map(module => report.dest.path / module.jsFileName)

  def bundleFilename: T[String] = "out-bundle.js"
  def outputEntryFileNames: T[String] = "out-bundle-[name].js"

  def copyInputFile = Task.Anon { (inputFiles: Iterable[os.Path]) =>
    val copied = inputFiles.map(Task.dest / _.last)
    for (inputFile, copiedFile) <- inputFiles.zip(copied) do
      if inputFile != copiedFile then os.copy.over(inputFile, copiedFile)
    copied.map(PathRef(_))
  }

  protected def linkNpmInstall = Task.Anon {
    for path <- os.list(npmInstall().path) do
      os.remove(Task.dest / path.last)
      os.symlink(Task.dest / path.last, path)
  }

  protected def bundle: Task[BundleParams => Seq[PathRef]]
end ScalaJSBundleModule

//noinspection ScalaWeakerAccess
object ScalaJSBundleModule:

  // noinspection ScalaUnusedSymbol
  trait Test extends TestScalaJSModule:
    this: ScalaJSBundleModule =>

    override def fastLinkJSTest = Task {
      val report = super.fastLinkJSTest()

      linkNpmInstall()

      bundle.apply()(
        BundleParams(
          report.publicModules.map(report.dest.path / _.jsFileName).toSeq,
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
      Report(publicModules = modules, dest = PathRef(Task.dest))
    }

  end Test

end ScalaJSBundleModule
