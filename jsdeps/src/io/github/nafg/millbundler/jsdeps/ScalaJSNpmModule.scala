package io.github.nafg.millbundler.jsdeps

import mill._
import mill.scalajslib.TestScalaJSModule
import mill.scalajslib.api.JsEnvConfig

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
      os.call(npmInstallCommand(), cwd = dir)
    catch {
      case e: Exception =>
        throw new RuntimeException("Error running npm install", e)
    }

    PathRef(dir)
  }
}
//noinspection ScalaWeakerAccess
object ScalaJSNpmModule {
  trait Test extends TestScalaJSModule with ScalaJSNpmModule {
    override def jsEnvConfig = T {
      val path = npmInstall().path / "node_modules"
      println("path: " + path)
      os.proc("ls", "react-phone-number-input")
        .call(cwd = path, stdout = os.Inherit)
      JsEnvConfig.NodeJs(env = Map("NODE_PATH" -> path.toString))
    }
  }
}
