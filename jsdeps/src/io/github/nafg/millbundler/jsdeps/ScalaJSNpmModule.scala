package io.github.nafg.millbundler.jsdeps

import mill._
import mill.modules.Jvm

//noinspection ScalaUnusedSymbol,ScalaWeakerAccess
trait ScalaJSNpmModule extends ScalaJSDepsModule {
  def npmCommand = T {
    if (System.getProperty("os.name").toLowerCase.contains("windows"))
      "npm.cmd"
    else
      "npm"
  }

  def npmInstallCommand = T(Seq(npmCommand(), "install", "--force"))

  protected def packageJson(deps: JsDeps) =
    ujson.Obj(
      "dependencies" -> deps.dependencies,
      "devDependencies" -> deps.devDependencies
    )

  def npmInstall = T {
    val dir = jsDepsDir().path
    val pkgJson = packageJson(allJsDeps())

    os.write.over(dir / "package.json", pkgJson.render(2) + "\n")

    try
      Jvm.runSubprocess(
        commandArgs = npmInstallCommand(),
        envArgs = Map.empty,
        workingDir = dir
      )
    catch {
      case e: Exception =>
        throw new RuntimeException("Error running npm install", e)
    }

    PathRef(dir)
  }
}
