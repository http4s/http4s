/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats._
import cats.syntax.all._
import org.http4s.internal.reduceComparisons

/** A projection of a [[Response]] without the body.
  *
  * @note The [[Response#attributes]] are omitted in this encoding because they
  *       do not (and can not) have a [[cats.kernel.Order]] instance. If they
  *       were included here, then we could not write a [[cats.kernel.Order]]
  *       instance for [[ResponsePrelude]], limiting some of its utility, e.g. it
  *       could not be used in a [[cats.data.NonEmptySet]].
  */
sealed abstract class ResponsePrelude extends Product with Serializable {
  def headers: Headers
  def httpVersion: HttpVersion
  def status: Status

  def withHeaders(value: Headers): ResponsePrelude
  def withHttpVersion(value: HttpVersion): ResponsePrelude
  def withStatus(value: Status): ResponsePrelude

  // final //

  final def mapHeaders(f: Headers => Headers): ResponsePrelude =
    withHeaders(f(headers))

  final def mapHttpVersion(f: HttpVersion => HttpVersion): ResponsePrelude =
    withHttpVersion(f(httpVersion))

  final def mapStatus(f: Status => Status): ResponsePrelude =
    withStatus(f(status))

  override final def toString: String =
    s"ResponsePrelude(headers = ${headers.redactSensitive()}, httpVersion = ${httpVersion}, status = ${status})"
}

object ResponsePrelude {
  private[this] final case class ResponsePreludeImpl(
      override final val headers: Headers,
      override final val httpVersion: HttpVersion,
      override final val status: Status,
  ) extends ResponsePrelude {
    override final def withHeaders(value: Headers): ResponsePrelude =
      this.copy(headers = value)

    override final def withHttpVersion(value: HttpVersion): ResponsePrelude =
      this.copy(httpVersion = value)

    override final def withStatus(value: Status): ResponsePrelude =
      this.copy(status = value)
  }

  def apply(
      headers: Headers,
      httpVersion: HttpVersion,
      status: Status,
  ): ResponsePrelude =
    ResponsePreludeImpl(
      headers,
      httpVersion,
      status,
    )

  def fromResponse[F[_]](value: Response[F]): ResponsePrelude =
    ResponsePreludeImpl(
      value.headers,
      value.httpVersion,
      value.status,
    )

  implicit val catsHashAndOrderForResponsePrelude
      : Hash[ResponsePrelude] with Order[ResponsePrelude] =
    new Hash[ResponsePrelude] with Order[ResponsePrelude] {
      override def hash(x: ResponsePrelude): Int = x.hashCode

      override def compare(x: ResponsePrelude, y: ResponsePrelude): Int =
        reduceComparisons(
          x.headers.compare(y.headers),
          Eval.later(x.httpVersion.compare(y.httpVersion)),
          Eval.later(x.status.compare(y.status)),
        )
    }

  implicit val catsShowForResponsePrelude: Show[ResponsePrelude] =
    Show.fromToString

  implicit def stdLibOrdering: Ordering[ResponsePrelude] =
    catsHashAndOrderForResponsePrelude.toOrdering
}
