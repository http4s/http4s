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

/** A projection of a [[Request]] without the body.
  *
  * @note The [[Request#attributes]] are omitted in this encoding because they
  *       do not (and can not) have a [[cats.kernel.Order]] instance. If they
  *       were included here, then we could not write a [[cats.kernel.Order]]
  *       instance for [[RequestPrelude]], limiting some of its utility, e.g. it
  *       could not be used in a [[cats.data.NonEmptySet]].
  */
sealed abstract class RequestPrelude extends Product with Serializable {
  def headers: Headers
  def httpVersion: HttpVersion
  def method: Method
  def uri: Uri

  def withHeaders(value: Headers): RequestPrelude
  def withHttpVersion(value: HttpVersion): RequestPrelude
  def withMethod(value: Method): RequestPrelude
  def withUri(value: Uri): RequestPrelude

  // final //

  final def mapHeaders(f: Headers => Headers): RequestPrelude =
    withHeaders(f(headers))

  final def mapHttpVersion(f: HttpVersion => HttpVersion): RequestPrelude =
    withHttpVersion(f(httpVersion))

  final def mapMethod(f: Method => Method): RequestPrelude =
    withMethod(f(method))

  final def mapUri(f: Uri => Uri): RequestPrelude =
    withUri(f(uri))

  override final def toString: String =
    s"RequestPrelude(headers = ${headers
        .redactSensitive()}, httpVersion = ${httpVersion}, method = ${method}, uri = ${uri})"
}

object RequestPrelude {
  private[this] final case class RequestPreludeImpl(
      override final val headers: Headers,
      override final val httpVersion: HttpVersion,
      override final val method: Method,
      override final val uri: Uri,
  ) extends RequestPrelude {
    override final def withHeaders(value: Headers): RequestPrelude =
      this.copy(headers = value)

    override final def withHttpVersion(value: HttpVersion): RequestPrelude =
      this.copy(httpVersion = value)

    override final def withMethod(value: Method): RequestPrelude =
      this.copy(method = value)

    override final def withUri(value: Uri): RequestPrelude =
      this.copy(uri = value)
  }

  def apply(
      headers: Headers,
      httpVersion: HttpVersion,
      method: Method,
      uri: Uri,
  ): RequestPrelude =
    RequestPreludeImpl(
      headers,
      httpVersion,
      method,
      uri,
    )

  def fromRequest[F[_]](value: Request[F]): RequestPrelude =
    RequestPreludeImpl(
      value.headers,
      value.httpVersion,
      value.method,
      value.uri,
    )

  implicit val catsHashAndOrderForRequestPrelude: Hash[RequestPrelude] with Order[RequestPrelude] =
    new Hash[RequestPrelude] with Order[RequestPrelude] {
      override def hash(x: RequestPrelude): Int = x.hashCode

      override def compare(x: RequestPrelude, y: RequestPrelude): Int =
        reduceComparisons(
          x.headers.compare(y.headers),
          Eval.later(x.httpVersion.compare(y.httpVersion)),
          Eval.later(x.method.compare(y.method)),
          Eval.later(x.uri.compare(y.uri)),
        )
    }

  implicit val catsShowForRequestPrelude: Show[RequestPrelude] =
    Show.fromToString

  implicit def stdLibOrdering: Ordering[RequestPrelude] =
    catsHashAndOrderForRequestPrelude.toOrdering
}
