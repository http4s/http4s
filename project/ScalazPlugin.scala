package org.http4s.build

import sbt._, Keys._
import scala.language.implicitConversions

object ScalazPlugin extends AutoPlugin {
  object autoImport {
    val scalazVersion = settingKey[String]("The version of Scalaz used for building.")
    val scalazVersionRewriter = settingKey[(String, String) => String]("Takes a base version and Scalaz version and returns a modified version")

    implicit def scalazPluginModuleIdSyntax(moduleId: ModuleID): ModuleIdOps =
      new ModuleIdOps(moduleId)
  }

  import autoImport._

  override def requires = verizon.build.RigPlugin

  override def trigger = allRequirements

  override lazy val projectSettings = Seq(
    scalazVersion := sys.env.get("SCALAZ_VERSION").getOrElse("7.2.15"),
    scalazVersionRewriter := scalazVersionRewriters.default,
    version := scalazVersionRewriter.value(version.value, scalazVersion.value),
    unmanagedSourceDirectories in Compile +=
      (sourceDirectory in Compile).value / scalazSourceDirectory(scalazVersion.value),
    unmanagedSourceDirectories in Test +=
      (sourceDirectory in Test).value / scalazSourceDirectory(scalazVersion.value)
  )

  type ScalazVersionRewriter = (String, String) => String

  object scalazVersionRewriters {
    /**
     * Appends a `-scalaz-x.y` qualifier to the version, based on the
     * binary version of scalaz.
     */
    object default extends ScalazVersionRewriter {
      def apply(version: String, scalazVersion: String) = {
        val qualifier = "scalaz-"+binaryScalazVersion(scalazVersion)
        version match {
          case VersionNumber(numbers, tags, extras) =>
            VersionNumber(numbers, qualifier +: tags, extras).toString
          case _ =>
            // version is something weird. Do our best.
            s"${version}-${qualifier}"
        }
      }
    }

    /**
     * Takes a function of scalaz version to a suffix to apply
     * directly after the version.  This is in contrast to a
     * qualifier, which begins with a '-'.  This convention is
     * discouraged, but common in the scalaz ecosystem.
     */
    def suffixed(suffixer: String => String): ScalazVersionRewriter =
      new ScalazVersionRewriter {
        def apply(version: String, scalazVersion: String) = {
          val suffix = suffixer(scalazVersion)
          version match {
            case VersionNumber(numbers, tags, extras) =>
              numbers.mkString(".") + suffix + (tags match {
                case Seq() => ""
                case ts => ts.mkString("-", "-", "")
              }) + extras.mkString("")
          }
        }
      }

    /**
     * Takes a function of scalaz binary version to a suffix to apply
     * directly after the version.  This is in contrast to a
     * qualifier, which begins with a '-'.  This convention is
     * discouraged, but common in the scalaz ecosystem.
     */
    def suffixedBinary(suffixer: String => String): ScalazVersionRewriter =
      suffixed((binaryScalazVersion _) andThen suffixer)

    val scalazStream_0_8 = suffixedBinary {
      case "7.2" => "a"
      case _ => ""
    }

    val scalazStream_0_7 = suffixedBinary {
      case "7.1" => "a"
      case _ => ""
    }

    val noRewrite = suffixed(_ => "")
  }

  class ModuleIdOps(val moduleId: ModuleID) extends AnyVal {
    import scalazVersionRewriters._

    private def nopeNopeNope(scalazVersion: String) =
      throw new NoSuchElementException("No version of ${moduleId} known for scalaz-${scalazVersion}. Try being explicit without `forScalaz`.")

    /*
     * Attempts to version your Scalaz cross-built dependencies according
     * to their own evolving conventions.  We track them so you don't
     * have to.
     */
    def forScalaz(scalazVersion: String) = {
      import scala.math.Ordered.orderingToOrdered
      try {
        val rewriter = moduleId match {
          case m if m.organization == "io.verizon.helm" =>
            default
          case m if m.organization == "io.verizon.knobs" =>
            m.revision match {
              case VersionNumber(Seq(x, _*), _, _) if x >= 4 =>
                default
              case VersionNumber(Seq(x, _*), _, _) if x == 3 =>
                scalazStream_0_8
            }
          case m if m.organization == "io.verizon.quiver" =>
            m.revision match {
              case VersionNumber(Seq(x, y, _*), _, _) if (x, y) >= (5, 5) =>
                default
            }
          case m if m.organization == "org.http4s" & m.name.startsWith("http4s-") =>
            m.revision match {
              case VersionNumber(Seq(x, y, _*), _, _) if (x, y) >= (0, 16) =>
                default
              case VersionNumber(Seq(0, y, _*), _, _) if y >= 13 =>
                scalazStream_0_8
            }
          case m if m.organization == "org.http4s" & m.name == "jawn-streamz" =>
            m.revision match {
              case VersionNumber(Seq(x, y, _*), _, _) if (x, y) >= (0, 9) =>
                scalazStream_0_8
            }
          case m if m.organization == "org.scalaz.stream" =>
            m.revision match {
              case VersionNumber(Seq(x, y, _*), _, _) if (x, y) >= (0, 8) =>
                scalazStream_0_8
              case VersionNumber(Seq(0, 7, _*), _, _) =>
                scalazStream_0_7
            }
          case m if m.organization == "org.specs2" =>
            m.revision match {
              case VersionNumber(Seq(x, y, z, _*), _, _) if (x, y, z) >= (3, 8, 1) =>
                suffixedBinary {
                  case "7.2" => ""
                  case "7.1" => "-scalaz-7.1"
                }
            }
        }
        moduleId.copy(revision = rewriter(moduleId.revision, scalazVersion))
      }
      catch {
        case _: MatchError => nopeNopeNope(scalazVersion)
      }
    }
  }

  def binaryScalazVersion(scalazVersion: String): String = {
    scalazVersion match {
      case VersionNumber(Seq(x, y, _*), Seq(), Seq()) if x >= 7 => s"${x}.${y}"
      case _ => scalazVersion
    }
  }

  def scalazSourceDirectory(scalazVersion: String): String =
    "scalaz-" + binaryScalazVersion(scalazVersion)
}
