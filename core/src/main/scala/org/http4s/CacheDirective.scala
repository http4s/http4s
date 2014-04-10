package org.http4s

import scala.Product
import org.http4s.util.{CaseInsensitiveString, Writer, Renderable}
import scala.concurrent.duration.Duration
import util.string._

sealed trait CacheDirective extends Product with Renderable {
  val name = productPrefix.replace("$minus", "-").ci
  override def value: String = name.toString
  override def toString = value
  def render[W <: Writer](writer: W) = writer.append(value)
}

/**
 * A registry of cache-directives, as listed in
 * http://www.iana.org/assignments/http-cache-directives/http-cache-directives.xhtml
 */
object CacheDirective {
  case class `max-age`(deltaSeconds: Duration) extends CacheDirective {
    override def value = name + "=" + deltaSeconds.toSeconds
  }

  case class `max-stale`(deltaSeconds: Option[Duration] = None) extends CacheDirective {
    override def value = name + deltaSeconds.fold("")("=" + _.toSeconds)
  }

  case class `min-fresh`(deltaSeconds: Duration) extends CacheDirective {
    override def value = name + "=" + deltaSeconds.toSeconds
  }

  case object `must-revalidate` extends CacheDirective

  case class `no-cache`(fieldNames: Seq[CaseInsensitiveString] = Seq.empty) extends CacheDirective {
    override def value = name + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }

  case object `no-store` extends CacheDirective

  case object `no-transform` extends CacheDirective

  case object `only-if-cached` extends CacheDirective

  case class `private`(fieldNames: Seq[CaseInsensitiveString] = Nil) extends CacheDirective {
    override def value = name + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }

  case object `proxy-revalidate` extends CacheDirective

  case object public extends CacheDirective

  case class `s-maxage`(deltaSeconds: Duration) extends CacheDirective {
    override def value = name + "=" + deltaSeconds.toSeconds
  }

  case class `stale-if-error`(deltaSeconds: Duration) extends CacheDirective {
    override def value = name + "=" + deltaSeconds.toSeconds
  }

  case class `stale-while-revalidate`(deltaSeconds: Duration) extends CacheDirective {
    override def value = name + "=" + deltaSeconds.toSeconds
  }

  def apply(name: CaseInsensitiveString, argument: Option[String] = None): CacheDirective =
    new CustomCacheDirective(name, argument)

  def apply(name: String, argument: Option[String]): CacheDirective =
    apply(name.ci, argument)

  def apply(name: String): CacheDirective = apply(name, None)

  private case class CustomCacheDirective(override val name: CaseInsensitiveString, argument: Option[String] = None)
    extends CacheDirective
  {
    override def value = name + argument.fold("")("=\"" + _ + '"')
  }
}