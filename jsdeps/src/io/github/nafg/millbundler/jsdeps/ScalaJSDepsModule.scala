package io.github.nafg.millbundler.jsdeps

import mill.*
import mill.api.PathRef
import mill.scalajslib.ScalaJSModule
import os.Path

//noinspection ScalaUnusedSymbol,ScalaWeakerAccess
trait ScalaJSDepsModule extends ScalaJSModule {

  /** JS dependencies, explicitly defined for this module
    */
  def jsDeps = Task(JsDeps())

  /** JS dependencies of moduleDeps, transitively.
    */
  def transitiveJsDeps = Task.sequence(
    recursiveModuleDeps.collect { case mod: ScalaJSDepsModule =>
      mod.allJsDeps
    // case mod: ScalaJsModule => mod.recursiveModuleDeps
    }
  )

  /** JS dependencies collected from ivyDeps jars
    */
  def mvnJsDeps = Task {
    compileClasspath().toSeq.flatMap {
      case pathRef if pathRef.path.ext == "jar" =>
        JsDeps.fromJar(pathRef.path)
      case _ =>
        Seq.empty
    }
  }

  final def allJsDeps: Task.Simple[JsDeps] = Task {
    JsDeps.combine(mvnJsDeps() ++ transitiveJsDeps() :+ jsDeps())
  }
}
