package org.http4s

import scala.Product
import org.http4s.util.{Writer, Renderable}

sealed trait CacheDirective extends Product with Renderable {
  val name = productPrefix.replace("$minus", "-")
  override def value: String = name
  override def toString = value
  def render[W <: Writer](writer: W) = writer.append(value)
}

object CacheDirective {
  sealed trait RequestDirective extends CacheDirective
  sealed trait ResponseDirective extends CacheDirective

  /* Requests and Responses */
//  case object `no-cache` extends RequestDirective with ResponseDirective
  case object `no-store` extends RequestDirective with ResponseDirective
  case object `no-transform` extends RequestDirective with ResponseDirective
  case class `max-age`(deltaSeconds: Long) extends RequestDirective with ResponseDirective {
    override def value = name + "=" + deltaSeconds
  }
  case class CustomCacheDirective(name_ :String, content: Option[String])  extends RequestDirective with ResponseDirective {
    override val name = name_
    override def value = name + content.map("=\"" + _ + '"').getOrElse("")
  }

  /* Requests only */
  case class `max-stale`(deltaSeconds: Option[Long]) extends RequestDirective {
    override def value = name + deltaSeconds.map("=" + _).getOrElse("")
  }
  case class `min-fresh`(deltaSeconds: Long) extends RequestDirective {
    override def value = name + "=" + deltaSeconds
  }
  case object `only-if-cached` extends RequestDirective

  /* Responses only */
  case object `public` extends ResponseDirective
  case class `private`(fieldNames :Seq[String] = Nil) extends ResponseDirective {
    override def value = name + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }
  case class `no-cache`(fieldNames: Seq[String] = Nil) extends ResponseDirective {
    override def value = name + (if (fieldNames.isEmpty) "" else fieldNames.mkString("=\"", ",", "\""))
  }
  case object `must-revalidate` extends ResponseDirective
  case object `proxy-revalidate` extends ResponseDirective
  case class `s-maxage`(deltaSeconds: Long)  extends ResponseDirective {
    override def value = name + "=" + deltaSeconds
  }
}