package io.github.nafg.millbundler.jsdeps

import mill.api.PathRef
import mill.scalajslib.ScalaJSModule
import mill.{T, Target, Task}

//noinspection ScalaUnusedSymbol,ScalaWeakerAccess
trait ScalaJSDepsModule extends ScalaJSModule {

  /** JS dependencies, explicitly defined for this module
    */
  def jsDeps = T(JsDeps())

  /** JS dependencies of moduleDeps, transitively.
    */
  def transitiveJsDeps = T.sequence(
    recursiveModuleDeps.collect { case mod: ScalaJSDepsModule => mod.allJsDeps }
  )

  /** JS dependencies collected from ivyDeps jars
    */
  def ivyJsDeps = T {
    compileClasspath().toSeq.flatMap {
      case pathRef if pathRef.path.ext == "jar" =>
        JsDeps.fromJar(pathRef.path)
      case _ =>
        Seq.empty
    }
  }

  final def allJsDeps: Target[JsDeps] = T {
    JsDeps.combine(ivyJsDeps() ++ transitiveJsDeps() :+ jsDeps())
  }

  def jsDepsDir = Task(persistent = true) {
    val dir = T.ctx().dest
    os.makeDir.all(dir)
    PathRef(dir)
  }

  def copySources = T {
    val dir = jsDepsDir().path
    for ((path, contents) <- allJsDeps().jsSources)
      os.write.over(dir / os.RelPath(path), contents)
  }
}
