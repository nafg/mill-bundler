package io.github.nafg.millbundler.jsdeps

import mill.T
import mill.api.PathRef
import mill.define.Target
import mill.scalajslib.ScalaJSModule

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
    resolveDeps(transitiveIvyDeps)().iterator.toList
      .flatMap(pathRef => JsDeps.fromJar(pathRef.path))
  }

  final def allJsDeps: Target[JsDeps] = T {
    JsDeps.combine(ivyJsDeps() ++ transitiveJsDeps() :+ jsDeps())
  }

  def jsDepsDir = T.persistent {
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
