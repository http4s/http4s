package org.http4s
package headers

import org.http4s.util.Writer

object Authorization extends HeaderKey.Internal[Authorization] with HeaderKey.Singleton

final case class Authorization(credentials: Credentials) extends Header.Parsed {
  override def key = `Authorization`
  override def renderValue(writer: Writer): writer.type = credentials.render(writer)
}
