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
  *       instance for [[RequestInfo]], limiting some of its utility, e.g. it
  *       could not be used in a [[cats.data.NonEmptySet]].
  */
sealed abstract class RequestInfo extends Product with Serializable {
  def headers: Headers
  def httpVersion: HttpVersion
  def method: Method
  def uri: Uri

  def withHeaders(value: Headers): RequestInfo
  def withHttpVersion(value: HttpVersion): RequestInfo
  def withMethod(value: Method): RequestInfo
  def withUri(value: Uri): RequestInfo

  // final //

  final def mapHeaders(f: Headers => Headers): RequestInfo =
    withHeaders(f(headers))

  final def mapHttpVersion(f: HttpVersion => HttpVersion): RequestInfo =
    withHttpVersion(f(httpVersion))

  final def mapMethod(f: Method => Method): RequestInfo =
    withMethod(f(method))

  final def mapUri(f: Uri => Uri): RequestInfo =
    withUri(f(uri))

  final override def toString: String =
    s"RequestInfo(headers = ${headers
      .redactSensitive()}, httpVersion = ${httpVersion}, method = ${method}, uri = ${uri})"
}

object RequestInfo {
  private[this] final case class RequestInfoImpl(
      override final val headers: Headers,
      override final val httpVersion: HttpVersion,
      override final val method: Method,
      override final val uri: Uri
  ) extends RequestInfo {
    override final def withHeaders(value: Headers): RequestInfo =
      this.copy(headers = value)

    override final def withHttpVersion(value: HttpVersion): RequestInfo =
      this.copy(httpVersion = value)

    override final def withMethod(value: Method): RequestInfo =
      this.copy(method = value)

    override final def withUri(value: Uri): RequestInfo =
      this.copy(uri = value)
  }

  def apply(
      headers: Headers,
      httpVersion: HttpVersion,
      method: Method,
      uri: Uri
  ): RequestInfo =
    RequestInfoImpl(
      headers,
      httpVersion,
      method,
      uri
    )

  def fromRequest[F[_]](value: Request[F]): RequestInfo =
    RequestInfoImpl(
      value.headers,
      value.httpVersion,
      value.method,
      value.uri
    )

  implicit val catsHashAndOrderForRequestInfo: Hash[RequestInfo] with Order[RequestInfo] =
    new Hash[RequestInfo] with Order[RequestInfo] {
      override def hash(x: RequestInfo): Int = x.hashCode

      override def compare(x: RequestInfo, y: RequestInfo): Int =
        reduceComparisons(
          x.headers.compare(y.headers),
          Eval.later(x.httpVersion.compare(y.httpVersion)),
          Eval.later(x.method.compare(y.method)),
          Eval.later(x.uri.compare(y.uri))
        )
    }

  implicit val catsShowForRequestInfo: Show[RequestInfo] =
    Show.fromToString

  implicit def stdLibOrdering: Ordering[RequestInfo] =
    catsHashAndOrderForRequestInfo.toOrdering
}
