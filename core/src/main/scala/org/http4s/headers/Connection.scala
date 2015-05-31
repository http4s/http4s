package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.{Writer, CaseInsensitiveString}
import org.http4s.util.string._

import scalaz.NonEmptyList

// values should be case insensitive
//http://stackoverflow.com/questions/10953635/are-the-http-connection-header-values-case-sensitive
object Connection extends HeaderKey.Internal[Connection] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[Connection] = {
    new Http4sHeaderParser[Connection](raw.value) {
      def entry = rule (
        oneOrMore(Token).separatedBy(ListSep) ~ EOL ~>
          {xs: Seq[String] => Connection(xs.head.ci, xs.tail.map(_.ci): _*)}
      )
    }.parse.toOption
  }
}

final case class Connection(values: NonEmptyList[CaseInsensitiveString]) extends Header.Recurring {
  override def key = Connection
  type Value = CaseInsensitiveString
  def hasClose = values.list.contains("close".ci)
  def hasKeepAlive = values.list.contains("keep-alive".ci)
  override def renderValue(writer: Writer): writer.type = writer.addStrings(values.list.map(_.toString), ", ")
}

