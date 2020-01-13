package org.http4s

import org.http4s.SameSite._
import org.http4s.util.{Renderable, Writer}

/**
  * RFC6265 SameSite cookie attribute values.
  */
sealed trait SameSite extends Renderable {
  override def render(writer: Writer): writer.type = {
    val str = this match {
      case Strict => "Strict"
      case Lax => "Lax"
      case None => "None"
    }
    writer.append(str)
  }
}

object SameSite {
  case object Strict extends SameSite
  case object Lax extends SameSite
  case object None extends SameSite
}
