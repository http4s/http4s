package org.http4s

import org.http4s.util.{Renderable, Writer}

sealed trait ProductIdOrComment extends Renderable

final case class ProductId(value: String, version: Option[String] = None)
    extends ProductIdOrComment {

  override def render(writer: Writer): writer.type = {
    writer << value
    version.foreach { v =>
      writer << '/' << v
    }
    writer
  }
}

final case class ProductComment(value: String) extends ProductIdOrComment {
  override def render(writer: Writer): writer.type = {
    writer << '(' << value << ')'
    writer
  }
}
