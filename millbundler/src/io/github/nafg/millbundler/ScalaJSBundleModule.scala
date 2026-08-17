package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.ScalaJSNpmModule

import mill.*
import mill.scalajslib.TestScalaJSModule
import mill.scalajslib.api.{ModuleKind, Report}
import mill.{PathRef, T}

//noinspection ScalaWeakerAccess
trait ScalaJSBundleModule extends ScalaJSNpmModule {
  protected def getReportMainFilePath(report: Report): os.Path =
    report.dest.path / report.publicModules.head.jsFileName

  def bundleFilename = Task("out-bundle.js")

  def copyInputFile = Task.Anon { (inputFiles: Seq[os.Path]) =>
    val copied = inputFiles.map(Task.dest / _.last)
    for ((inputFile, copiedFile) <- inputFiles.zip(copied))
      if (inputFile != copiedFile)
        os.copy.over(inputFile, copiedFile)
    copied.map(PathRef(_))
  }

  /** Make the npm install available in the current task's dest so that bundlers
    * running there can resolve node modules.
    */
  protected def linkNodeModules = Task.Anon {
    os.copy.over(
      npmInstall().path / "package.json",
      Task.dest / "package.json"
    )

    for (name <- Seq("node_modules", "package-lock.json")) {
      val target = npmInstall().path / name
      val link = Task.dest / name
      if (!os.isLink(link))
        try os.symlink(link, target)
        catch {
          // e.g. Windows without symlink privileges, or a leftover real
          // file/dir from a previous fallback; fall back to copying
          case _: java.io.IOException | _: UnsupportedOperationException =>
            os.copy.over(target, link)
        }
    }
  }

  protected def bundle: Task[BundleParams => Seq[PathRef]]
}
//noinspection ScalaWeakerAccess
object ScalaJSBundleModule {
  // noinspection ScalaUnusedSymbol
  trait Test extends TestScalaJSModule { this: ScalaJSBundleModule =>
    override def fastLinkJSTest = Task {
      val report = super.fastLinkJSTest()

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
  }
}
