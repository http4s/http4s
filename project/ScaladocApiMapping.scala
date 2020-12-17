package org.http4s.sbt

import java.io.File
import java.net.URL
import sbt.Keys._
import sbtunidoc.BaseUnidocPlugin.autoImport._
import sbt.librarymanagement._

/** Provides API File -> URL mappings for dependencies which don't expose an
  * `apiURL` in their POM or if they do, they are doing so incorrectly and
  * SBT/Scaladoc can't reconstruct the mapping automatically.
  *
  * If the project for which we are providing a mapping provides a specific
  * Scaladoc link, we prefer that one. Otherwise we use [[https://javadoc.io]]
  * to fill in the gaps.
  *
  * There is a bit of fuzzy logic in here. Scaladoc requires a File -> URL
  * mapping for the linking to work. SBT does not seem to expose a ModuleId ->
  * File mapping in any API, which is what would be ideal to solve this
  * problem. What we do have is the classpath constructed by the Unidoc
  * plugin. Because we are lacking a full ModuleID construct here, we use some
  * regular expressions on the `File` in the classpath as a heuristic to match
  * the given `File` to a dependency module.
  */
object ScaladocApiMapping {

  /** Construct mappings for dependencies for which SBT/Scaladoc can not do so automatically.
    *
    * @param classpaths In practice this will need to be the ScalaUnidoc
    *        classpath, which is the aggregation of all classpaths.
    * @param scalaBinaryVersion The current scala binary version,
    *        e.g. `scalaBinaryVersion.value`.
    */
  def mappings(classpaths: Seq[Classpath], scalaBinaryVersion: String): Map[File, URL] = {
    classpaths.flatten.foldLeft(Map.empty[File, URL]){
      case (acc, value) =>
        val file: File = value.data
        playJsonMapping(scalaBinaryVersion)(file).toMap ++
        vaultMapping(scalaBinaryVersion)(file).toMap ++
        catsEffectMapping(scalaBinaryVersion)(file).toMap ++
        fs2CoreMapping(scalaBinaryVersion)(file).toMap ++
        acc
    }
  }

  private def maybeScalaBinaryVersionToSuffix(value: Option[String]): String =
    value.fold("")(value => s"_${value}")

  private def javadocIOAPIUrl(scalaBinaryVersion: Option[String], moduleId: ModuleID): URL = {
    val suffix: String =
      maybeScalaBinaryVersionToSuffix(scalaBinaryVersion)
    new URL(s"https://javadoc.io/doc/${moduleId.organization}/${moduleId.name}${suffix}/${moduleId.revision}/")
  }

  private def playJsonMapping(scalaBinaryVersion: String)(file: File): Option[(File, URL)] =
    if(file.toString.matches(""".+/play-json_[^/]+\.jar$""")) {
      Some(file -> javadocIOAPIUrl(Some(scalaBinaryVersion), Http4sPlugin.playJson))
    } else {
      None
    }

  private def vaultMapping(scalaBinaryVersion: String)(file: File): Option[(File, URL)] =
    // Be a _little_ more specific, since vault is an overloaded term,
    // e.g. hashicorp vault.
    if(file.toString.matches(""".+io.chrisdavenport.+/vault[^/]+\.jar$""")) {
      Some(file -> javadocIOAPIUrl(Some(scalaBinaryVersion), Http4sPlugin.vault))
    } else {
      None
    }

  private def catsEffectMapping(scalaBinaryVersion: String)(file: File): Option[(File, URL)] =
    if(file.toString.matches(""".+/cats-effect_[^/]+\.jar$""")) {
      Some(file -> new URL("https://typelevel.org/cats-effect/api/"))
    } else {
      None
    }

  private def fs2CoreMapping(scalaBinaryVersion: String)(file: File): Option[(File, URL)] = {
    val fs2Core: ModuleID =
      Http4sPlugin.fs2Core
    if(file.toString.matches(""".+/fs2-core_[^/]+\.jar$""")) {
      Some(file -> new URL(s"https://oss.sonatype.org/service/local/repositories/releases/archive/co/fs2/fs2-core_${scalaBinaryVersion}/${fs2Core.revision}/${fs2Core.name}_${scalaBinaryVersion}-${fs2Core.revision}-javadoc.jar/!/"))
    } else {
      None
    }
  }
}
