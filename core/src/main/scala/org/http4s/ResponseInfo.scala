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
  *       instance for [[ResponseInfo]], limiting some of its utility, e.g. it
  *       could not be used in a [[cats.data.NonEmptySet]].
  */
sealed abstract class ResponseInfo extends Product with Serializable {
  def headers: Headers
  def httpVersion: HttpVersion
  def status: Status

  def withHeaders(value: Headers): ResponseInfo
  def withHttpVersion(value: HttpVersion): ResponseInfo
  def withStatus(value: Status): ResponseInfo

  // final //

  final def mapHeaders(f: Headers => Headers): ResponseInfo =
    withHeaders(f(headers))

  final def mapHttpVersion(f: HttpVersion => HttpVersion): ResponseInfo =
    withHttpVersion(f(httpVersion))

  final def mapStatus(f: Status => Status): ResponseInfo =
    withStatus(f(status))

  final override def toString: String =
    s"ResponseInfo(headers = ${headers.redactSensitive()}, httpVersion = ${httpVersion}, status = ${status})"
}

object ResponseInfo {
  private[this] final case class ResponseInfoImpl(
    override final val headers: Headers,
    override final val httpVersion: HttpVersion,
    override final val status: Status
  ) extends ResponseInfo {
    override final def withHeaders(value: Headers): ResponseInfo =
      this.copy(headers = value)

    override final def withHttpVersion(value: HttpVersion): ResponseInfo =
      this.copy(httpVersion = value)

    override final def withStatus(value: Status): ResponseInfo =
      this.copy(status = value)
  }

  def apply(
    headers: Headers,
    httpVersion: HttpVersion,
    status: Status
  ): ResponseInfo =
    ResponseInfoImpl(
      headers,
      httpVersion,
      status
    )

  def fromResponse[F[_]](value: Response[F]): ResponseInfo =
    ResponseInfoImpl(
      value.headers,
      value.httpVersion,
      value.status
    )

  implicit val catsHashAndOrderForResponseInfo: Hash[ResponseInfo] with Order[ResponseInfo] =
    new Hash[ResponseInfo] with Order[ResponseInfo] {
      override def hash(x: ResponseInfo): Int = x.hashCode

      override def compare(x: ResponseInfo, y: ResponseInfo): Int =
        reduceComparisons(
          x.headers.compare(y.headers),
          Eval.later(x.httpVersion.compare(y.httpVersion)),
          Eval.later(x.status.compare(y.status))
        )
    }

  implicit val catsShowFOrResponseInfo: Show[ResponseInfo] =
    Show.fromToString

  implicit val stdLibOrdering: Ordering[ResponseInfo] =
    catsHashAndOrderForResponseInfo.toOrdering
}
