package org.http4s
package headers

import org.http4s.util.Writer

import scalaz.NonEmptyList

object `If-None-Match` extends HeaderKey.Internal[`If-None-Match`] with HeaderKey.Singleton {

  /** Match any existing entity */
  val `*` = `If-None-Match`(None)

  def apply(first: ETag.EntityTag, rest: ETag.EntityTag*): `If-None-Match` = {
    `If-None-Match`(Some(NonEmptyList(first, rest:_*)))
  }
}

case class `If-None-Match`(tags: Option[NonEmptyList[ETag.EntityTag]]) extends Header.Parsed {
  override def key: HeaderKey = `If-None-Match`
  override def value: String = tags match {
    case None       => "*"
    case Some(tags) => tags.list.mkString(",")
  }
  override def renderValue(writer: Writer): writer.type = writer.append(value)
}

