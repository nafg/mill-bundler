package io.github.nafg.millbundler.jsdeps

import mill.*
import mill.api.BuildCtx
import mill.scalajslib.TestScalaJSModule
import mill.scalajslib.api.JsEnvConfig

//noinspection ScalaUnusedSymbol,ScalaWeakerAccess
trait ScalaJSNpmModule extends ScalaJSDepsModule:

  def npmCommand = Task {
    if System.getProperty("os.name").toLowerCase.contains("windows") then
      "npm.cmd"
    else "npm"
  }

  def npmInstallCommand =
    Task(Seq(npmCommand(), "install", "--force"))

  protected def packageJson: Task[JsDeps => ujson.Obj] = Task.Anon {
    (deps: JsDeps) =>
      ujson.Obj(
        "dependencies" -> deps.dependencies,
        "devDependencies" -> deps.devDependencies
      )
  }

  def npmInstall = Task {
    val pkgJson = packageJson()(allJsDeps())

    os.write.over(Task.dest / "package.json", pkgJson.render(2) + "\n")

    try os.call(npmInstallCommand(), cwd = Task.dest)
    catch
      case e: Exception =>
        throw new RuntimeException("Error running npm install", e)

    PathRef(Task.dest)
  }

  def printJsUpdates() =
    Task.Command {
      import scala.io.AnsiColor.{BOLD, RESET}
      val logger = Task.log
      def pad(s: String, len: Int): String = s.padTo(len, 32.toChar)

      val deps =
        (jsDeps().dependencies ++ jsDeps().devDependencies).toSeq.sortBy(_._1)
      val maxPkgLen = deps.map(_._1.length).max
      val maxVerLen = deps.map(_._2.length).max
      val proj = this.toString()
      val latestVersions =
        for ((pkg, ver) <- deps)
          yield (
            pkg,
            ver,
            os.call(Seq("npm", "view", pkg, "dist-tags.latest")).out.trim()
          )
      val updates = latestVersions.filter(x => x._2 != x._3)
      if updates.nonEmpty then
        logger.info(
          s"$BOLD$proj $RESET${updates.length} new npm package versions"
        )
        for (pkg, cur, latest) <- updates do
          logger.info(
            s"  $BOLD${pad(pkg, maxPkgLen)}$RESET ${pad(cur, maxVerLen)} -> $latest"
          )
    }

end ScalaJSNpmModule

//noinspection ScalaWeakerAccess
object ScalaJSNpmModule:

  trait Test extends TestScalaJSModule with ScalaJSNpmModule:

    override def jsEnvConfig = Task {
      val path = npmInstall().path / "node_modules"
      JsEnvConfig.NodeJs(env = Map("NODE_PATH" -> path.toString))
    }
