package io.github.nafg.millbundler.jsdeps

import mill._

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
    val logger = T.ctx().log
    val dir = jsDepsDir().path
    val pkgJson = packageJson(allJsDeps())
    os.write.over(dir / "package.json", pkgJson.render(2) + "\n")
    os.proc(npmInstallCommand())
      .call(
        cwd = dir,
        stdout = os.ProcessOutput.Readlines { line =>
          logger.debug("[npm install] " + line)
        }
      )
    PathRef(dir)
  }
}
