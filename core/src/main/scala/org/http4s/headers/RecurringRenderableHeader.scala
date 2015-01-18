package org.http4s.headers

import org.http4s.util.{Writer, Renderable}

/** Simple helper trait that provides a default way of rendering the value */
trait RecurringRenderableHeader extends RecurringHeader {
  type Value <: Renderable

  override def renderValue(writer: Writer): writer.type = {
    values.head.render(writer)
    values.tail.foreach( writer << ", " << _ )
    writer
  }
}
