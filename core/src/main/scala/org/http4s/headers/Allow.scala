package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.Writer

import scalaz.NonEmptyList

object Allow extends HeaderKey.Internal[Allow] with HeaderKey.Singleton {

  def apply(m: Method, ms: Method*): Allow = Allow(NonEmptyList(m, ms:_*))

  override protected def parseHeader(raw: Raw): Option[Allow] = {
    new Http4sHeaderParser[Allow](raw.value) {
      def entry = rule {
        oneOrMore(Token).separatedBy(ListSep) ~ EOL ~>  { ts: Seq[String] =>
          val ms = ts.map(Method.fromString(_).getOrElse(sys.error("Impossible. Please file a bug report.")))
          Allow(NonEmptyList(ms.head, ms.tail:_*))
        }
      }
    }.parse.toOption
  }
}

case class Allow(methods: NonEmptyList[Method]) extends Header.Parsed {
  override def key = Allow
  override def renderValue(writer: Writer): writer.type =
    writer.addStrings(methods.list.map(_.name), ", ")
}
