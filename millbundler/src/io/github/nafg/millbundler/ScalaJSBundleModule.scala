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

  protected def getReportMainFilePath(report: Report): Seq[os.Path] =
    report.publicModules
      .map(module => report.dest.path / module.jsFileName)
      .toSeq

  def bundleFilename: T[String] = "out-bundle.js"
  def outputEntryFileNames: T[String] = "out-bundle-[name].js"

  protected def expectedBundleFilename(
      inputFile: os.Path,
      multiEntry: Boolean,
      singleFilename: String,
      entryFileNames: String
  ): String =
    if multiEntry then
      val (head, tail) = entryFileNames.split("\\[name\\]", -1) match
        case Array(head, tail) => (head, tail)
        case _                 =>
          throw new RuntimeException(
            "Invalid output bundle file names, must contain [name]"
          )
      val name = inputFile.last.stripSuffix(".js").stripSuffix(".map")
      head + name + tail
    else singleFilename

  protected def bundlePaths = Task.Anon { (bundles: Iterable[os.Path]) =>
    val inputFiles = bundles.toSeq
    val singleFilename = bundleFilename()
    val entryFileNames = outputEntryFileNames()
    inputFiles match
      case Seq(_) =>
        List(
          PathRef(Task.dest / singleFilename),
          PathRef(Task.dest / (singleFilename + ".map"))
        )
      case inputFiles =>
        inputFiles.flatMap { inputFile =>
          val outName = expectedBundleFilename(
            inputFile,
            multiEntry = true,
            singleFilename,
            entryFileNames
          )
          Seq(
            PathRef(Task.dest / outName),
            PathRef(Task.dest / (outName + ".map"))
          )
        }
    end match
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
end ScalaJSBundleModule

//noinspection ScalaWeakerAccess
object ScalaJSBundleModule:

  // noinspection ScalaUnusedSymbol
  trait Test extends TestScalaJSModule:
    this: ScalaJSBundleModule =>

    override def fastLinkJSTest = Task {
      val report = super.fastLinkJSTest()

      val inputModules = report.publicModules.toSeq
      val inputPaths =
        inputModules.map(module => report.dest.path / module.jsFileName)
      val multiEntry = inputPaths.size > 1
      val singleFilename = bundleFilename()
      val entryFileNames = outputEntryFileNames()

      val bundles = bundle.apply()(
        BundleParams(inputPaths, opt = false)
      )
      val groupedBundles = bundles.groupBy(_.path.last.stripSuffix(".map"))

      val modules = inputModules.zip(inputPaths).map { case (orig, path) =>
        val outName = expectedBundleFilename(
          path,
          multiEntry,
          singleFilename,
          entryFileNames
        )
        val bundleGroup = groupedBundles.getOrElse(outName, Nil)
        Report.Module(
          moduleID = orig.moduleID,
          jsFileName = outName,
          sourceMapName =
            Some(outName + ".map").filter(_ => bundleGroup.size > 1),
          moduleKind = ModuleKind.NoModule
        )
      }
      Report(publicModules = modules, dest = PathRef(Task.dest))
    }

  end Test

end ScalaJSBundleModule
