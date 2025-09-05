package io.github.nafg.millbundler

import io.github.nafg.millbundler.jsdeps.JsDeps
import io.github.nafg.millbundler.jsdeps.ScalaJSNpmModule

import mill.*
import mill.PathRef
import mill.T
import mill.scalajslib.TestScalaJSModule
import mill.scalajslib.api.ModuleKind
import mill.scalajslib.api.Report

//noinspection ScalaWeakerAccess
trait ScalaJSBundleModule extends ScalaJSNpmModule:

  override protected def packageJson: Task[JsDeps => ujson.Obj] = Task.Anon {
    val tpe = moduleKind() match
      case ModuleKind.CommonJSModule => Some("commonjs")
      case ModuleKind.NoModule       => None
      case ModuleKind.ESModule       => Some("module")
    (deps: JsDeps) =>
      ujson.Obj.from(
        ujson.Obj(
          "name" -> moduleCtx.enclosing,
          tpe.map(ujson.Str(_)).map("type" -> _).toSeq*
        ).value.toSeq ++
          super.packageJson()(deps).value
      )
  }

  protected def getReportMainFilePath(report: Report): Iterable[os.Path] =
    report.publicModules.map(module => report.dest.path / module.jsFileName)

  def bundleFilename: T[String] = "out-bundle.js"
  def outputEntryFileNames: T[String] = "out-bundle-[name].js"

  def copyInputFiles = Task.Anon { (inputFiles: Iterable[os.Path]) =>
    val copied = inputFiles.map(Task.dest / _.last)
    for (inputFile, copiedFile) <- inputFiles.zip(copied) do
      if inputFile != copiedFile then os.copy.over(inputFile, copiedFile)
    copied
  }

  protected def linkInputFiles = Task.Anon { (inputFiles: Iterable[os.Path]) =>
    for path <- inputFiles do
      val linkPath = Task.dest / path.last
      os.remove(linkPath)
      os.symlink(linkPath, path)
  }

  protected def linkNpmInstall = Task.Anon {
    for path <- os.list(npmInstall().path) do
      os.remove(Task.dest / path.last)
      os.symlink(Task.dest / path.last, path)
  }

  protected def unlinkNpmInstall = Task.Anon {
    for path <- os.list(npmInstall().path) do
      os.remove(Task.dest / path.last)
  }

  protected def bundlePaths = Task.Anon { (bundles: Iterable[os.Path]) =>
    bundles match
      case Seq(inputFile) =>
        List(
          PathRef(Task.dest / bundleFilename()),
          PathRef(Task.dest / (bundleFilename() + ".map"))
        )
      case inputFiles =>
        val (head, tail) = outputEntryFileNames().split("\\[name\\]") match
          case Array(head, tail) => (head, tail)
          case _                 => throw new RuntimeException(
              "Invalid output bundle file names, must contain [name]"
            )

        inputFiles.map(inputFile =>
          val name = inputFile.last.stripSuffix(".js").stripSuffix(".map")
          PathRef(Task.dest / (head + name + tail))
        )
    end match
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

      val params = BundleParams(
        report.publicModules.map(report.dest.path / _.jsFileName).toSeq,
        opt = false
      )

      val bundles = bundle.apply()(params)
      val groupedBundles = bundles.groupBy(_.path.last.stripSuffix(".map"))
      val filename = bundleFilename()
      val modules = groupedBundles.map {
        case (filename, bundles) =>
          Report.Module(
            moduleID = "main",
            jsFileName = filename,
            sourceMapName =
              Some(filename + ".map").filter(_ => bundles.size > 1),
            moduleKind = ModuleKind.NoModule
          )
      }
      Report(publicModules = modules, dest = PathRef(Task.dest))
    }

  end Test

end ScalaJSBundleModule
