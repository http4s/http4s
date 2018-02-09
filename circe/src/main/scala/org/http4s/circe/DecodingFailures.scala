package org.http4s.circe

import cats.data.NonEmptyList
import io.circe.DecodingFailure
import cats.syntax.show._

/**
  * Wraps a list of decoding failures as an [[Exception]] when using [[accumulatingJsonOf]] to
  * decode JSON messages.
  */
final case class DecodingFailures(failures: NonEmptyList[DecodingFailure]) extends Exception {
  override def getMessage: String = failures.toList.map(_.show).mkString("\n")
}
