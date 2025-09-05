package io.github.nafg.millbundler.jsdeps

import java.io.*
import java.util.zip.ZipInputStream

case class JsDeps(
    /** NPM dependencies, as a map from package name to version
      */
    dependencies: Map[String, String] = Map.empty,

    /** NPM devDependencies, as a map from package name to version
      */
    devDependencies: Map[String, String] = Map.empty,

    /** Javascript source files, as a map from path to contents
      */
    jsSources: Map[String, String] = Map.empty
):

  def ++(that: JsDeps): JsDeps =
    JsDeps(
      dependencies ++ that.dependencies,
      devDependencies ++ that.devDependencies,
      jsSources ++ that.jsSources
    )

end JsDeps

object JsDeps:

  def apply(dependencies: (String, String)*): JsDeps =
    JsDeps(dependencies = dependencies.toMap)

  def combine(deps: Iterable[JsDeps]): JsDeps = deps.foldLeft(JsDeps())(_ ++ _)

  implicit def rw: upickle.default.ReadWriter[JsDeps] = upickle.default.macroRW

  @scala.annotation.tailrec
  private def readAllBytes(
      in: InputStream,
      buffer: Array[Byte] = new Array[Byte](8192),
      out: ByteArrayOutputStream = new ByteArrayOutputStream
  ): String =
    val byteCount = in.read(buffer)
    if byteCount < 0 then out.toString
    else
      out.write(buffer, 0, byteCount)
      readAllBytes(in, buffer, out)
  end readAllBytes

  def fromJar(jar: os.Path): Seq[JsDeps] =
    val stream = new ZipInputStream(
      new BufferedInputStream(new FileInputStream(jar.toIO))
    )
    try
      Iterator
        .continually(stream.getNextEntry)
        .takeWhile(_ != null)
        .collect {
          case z if z.getName == "NPM_DEPENDENCIES" =>
            val contentsAsJson = ujson.read(readAllBytes(stream)).obj

            def dependenciesOfType(key: String): Map[String, String] =
              contentsAsJson
                .getOrElse(key, ujson.Arr())
                .arr
                .flatMap(_.obj.map { case (s, v) => s -> v.str })
                .toMap

            JsDeps(
              dependenciesOfType("compileDependencies") ++ dependenciesOfType(
                "compile-dependencies"
              ),
              dependenciesOfType(
                "compileDevDependencies"
              ) ++ dependenciesOfType("compile-devDependencies")
            )
          case z
              if z.getName.endsWith(".js") && !z.getName.startsWith("scala/") =>
            JsDeps(jsSources = Map(z.getName -> readAllBytes(stream)))
        }
        .toList
    finally
      stream.close()
    end try
  end fromJar

end JsDeps
