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

import cats.{Order, Show}
import org.http4s.Status.ResponseClass
import org.http4s.util.Renderable

/** Representation of the HTTP response code and reason
  *
  * '''Note: ''' the reason is not important to the protocol and is not considered in equality checks.
  *
  * @param code HTTP status code
  * @param reason reason for the response. eg, OK
  * @see [[http://tools.ietf.org/html/rfc7231#section-6 RFC 7231, Section 6, Response Status Codes]]
  * @see [[http://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml IANA Status Code Registry]]
  */
sealed abstract class Status private (val reason: String)
    extends Product
    with Serializable
    with Ordered[Status]
    with Renderable {

  type Code <: Int
  val code: Code

  type IsEntityAllowed <: Boolean
  val isEntityAllowed: IsEntityAllowed

  val responseClass: ResponseClass =
    if (code < 200) Status.Informational
    else if (code < 300) Status.Successful
    else if (code < 400) Status.Redirection
    else if (code < 500) Status.ClientError
    else Status.ServerError

  def compare(that: Status): Int = code - that.code

  def isSuccess: Boolean = responseClass.isSuccess

  def withReason(reason: String): Status.Aux[Code, IsEntityAllowed] =
    Status.aux(code, reason, isEntityAllowed)

  override def render(writer: org.http4s.util.Writer): writer.type = writer << code << ' ' << reason

  /** Helpers for for matching against a [[Response]] */
  def unapply[F[_]](msg: Response[F]): Option[Response[F]] =
    if (msg.status == this) Some(msg) else None
}

object Status extends StatusCompanionCompat {
  import Registry._

  type Aux[Code0 <: Int, IsEntityAllowed0 <: Boolean] = Status {
    type Code = Code0
    type IsEntityAllowed = IsEntityAllowed0
  }

  private[http4s] final case class Impl[Code0 <: Int, IsEntityAllowed0 <: Boolean] private (
      code: Code0)(override val reason: String, val isEntityAllowed: IsEntityAllowed0)
      extends Status(reason) {
    type Code = Code0
    type IsEntityAllowed = IsEntityAllowed0
  }

  def apply(code: Int, reason: String = "", isEntityAllowed: Boolean = true): Status =
    aux(code, reason, isEntityAllowed)

  def unapply(status: Status): Option[Int] = Some(status.code)

  def aux[Code0 <: Int, IsEntityAllowed0 <: Boolean](
      code: Code0,
      reason: String = "",
      isEntityAllowed: IsEntityAllowed0): Status.Aux[Code0, IsEntityAllowed0] =
    Impl(code)(reason, isEntityAllowed)

  sealed trait ResponseClass {
    def isSuccess: Boolean

    /** Match a [[Response]] based on [[Status]] category */
    final def unapply[F[_]](resp: Response[F]): Option[Response[F]] =
      resp match {
        case Response(status, _, _, _, _) if status.responseClass == this => Some(resp)
        case _ => None
      }
  }

  case object Informational extends ResponseClass { val isSuccess = true }
  case object Successful extends ResponseClass { val isSuccess = true }
  case object Redirection extends ResponseClass { val isSuccess = true }
  case object ClientError extends ResponseClass { val isSuccess = false }
  case object ServerError extends ResponseClass { val isSuccess = false }

  object ResponseClass {
    @deprecated("Moved to org.http4s.Status.Informational", "0.16")
    val Informational = Status.Informational
    @deprecated("Moved to org.http4s.Status.Successful", "0.16")
    val Successful = Status.Successful
    @deprecated("Moved to org.http4s.Status.Redirection", "0.16")
    val Redirection = Status.Informational
    @deprecated("Moved to org.http4s.Status.ClientError", "0.16")
    val ClientError = Status.Informational
    @deprecated("Moved to org.http4s.Status.ServerError", "0.16")
    val ServerError = Status.Informational
  }

  private[http4s] val MinCode = 100
  private[http4s] val MaxCode = 599

  def fromInt(code: Int): ParseResult[Status] =
    withRangeCheck(code) {
      lookup(code) match {
        case right: Right[_, _] => right
        case _ => ParseResult.success(Status(code, ""))
      }
    }

  def fromIntAndReason(code: Int, reason: String): ParseResult[Status] =
    withRangeCheck(code) {
      lookup(code, reason) match {
        case right: Right[_, _] => right
        case _ => ParseResult.success(Status(code, reason))
      }
    }

  private def withRangeCheck(code: Int)(onSuccess: => ParseResult[Status]): ParseResult[Status] =
    if (code >= MinCode && code <= MaxCode) onSuccess
    else ParseResult.fail("Invalid code", s"$code is not between $MinCode and $MaxCode.")

  private[http4s] def registered: List[Status] = all

  private object Registry {
    private val registry: Array[ParseResult[Status]] =
      Array.fill[ParseResult[Status]](MaxCode + 1) {
        ParseResult.fail("Unregistered", "Unregistered")
      }

    def lookup(code: Int): ParseResult[Status] = registry(code)

    def lookup(code: Int, reason: String): ParseResult[Status] = {
      val lookupResult = lookup(code)
      lookupResult match {
        case Right(r) if r.reason == reason => lookupResult
        case _ => ParseResult.fail("Reason did not match", s"Nonstandard reason: $reason")
      }
    }

    def register(status: Status): status.type = {
      registry(status.code) = Right(status)
      status
    }

    def all: List[Status] = registry.collect { case Right(status) => status }.toList
  }

  /** Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
    */
  // No type annotations because types are less specific in Scala 2.12
  // scalastyle:off magic.number
  val Continue = register(Status.entityNotAllowed(100, "Continue"))
  val SwitchingProtocols = register(Status.entityNotAllowed(101, "Switching Protocols"))
  val Processing = register(Status.entityNotAllowed(102, "Processing"))
  val EarlyHints = register(Status.entityNotAllowed(103, "Early Hints"))

  val Ok = register(Status.entityAllowed(200, "OK"))
  val Created = register(Status.entityAllowed(201, "Created"))
  val Accepted = register(Status.entityAllowed(202, "Accepted"))
  val NonAuthoritativeInformation = register(
    Status.entityAllowed(203, "Non-Authoritative Information"))
  val NoContent = register(Status.entityNotAllowed(204, "No Content"))
  val ResetContent = register(Status.entityNotAllowed(205, "Reset Content"))
  val PartialContent = register(Status.entityAllowed(206, "Partial Content"))
  val MultiStatus = register(Status.entityAllowed(207, "Multi-Status"))
  val AlreadyReported = register(Status.entityAllowed(208, "Already Reported"))
  val IMUsed = register(Status.entityAllowed(226, "IM Used"))

  val MultipleChoices = register(Status.entityAllowed(300, "Multiple Choices"))
  val MovedPermanently = register(Status.entityAllowed(301, "Moved Permanently"))
  val Found = register(Status.entityAllowed(302, "Found"))
  val SeeOther = register(Status.entityAllowed(303, "See Other"))
  val NotModified = register(Status.entityNotAllowed(304, "Not Modified"))
  val UseProxy = register(Status.entityAllowed(305, "Use Proxy"))
  val TemporaryRedirect = register(Status.entityAllowed(307, "Temporary Redirect"))
  val PermanentRedirect = register(Status.entityAllowed(308, "Permanent Redirect"))

  val BadRequest = register(Status.entityAllowed(400, "Bad Request"))
  val Unauthorized = register(Status.entityAllowed(401, "Unauthorized"))
  val PaymentRequired = register(Status.entityAllowed(402, "Payment Required"))
  val Forbidden = register(Status.entityAllowed(403, "Forbidden"))
  val NotFound = register(Status.entityAllowed(404, "Not Found"))
  val MethodNotAllowed = register(Status.entityAllowed(405, "Method Not Allowed"))
  val NotAcceptable = register(Status.entityAllowed(406, "Not Acceptable"))
  val ProxyAuthenticationRequired = register(
    Status.entityAllowed(407, "Proxy Authentication Required"))
  val RequestTimeout = register(Status.entityAllowed(408, "Request Timeout"))
  val Conflict = register(Status.entityAllowed(409, "Conflict"))
  val Gone = register(Status.entityAllowed(410, "Gone"))
  val LengthRequired = register(Status.entityAllowed(411, "Length Required"))
  val PreconditionFailed = register(Status.entityAllowed(412, "Precondition Failed"))
  val PayloadTooLarge = register(Status.entityAllowed(413, "Payload Too Large"))
  val UriTooLong = register(Status.entityAllowed(414, "URI Too Long"))
  val UnsupportedMediaType = register(Status.entityAllowed(415, "Unsupported Media Type"))
  val RangeNotSatisfiable = register(Status.entityAllowed(416, "Range Not Satisfiable"))
  val ExpectationFailed = register(Status.entityAllowed(417, "Expectation Failed"))
  val ImATeapot = register(Status.entityAllowed(418, "I'm A Teapot"))
  val MisdirectedRequest = register(Status.entityAllowed(421, "Misdirected Request"))
  val UnprocessableEntity = register(Status.entityAllowed(422, "Unprocessable Entity"))
  val Locked = register(Status.entityAllowed(423, "Locked"))
  val FailedDependency = register(Status.entityAllowed(424, "Failed Dependency"))
  val TooEarly = register(Status.entityAllowed(425, "Too Early"))
  val UpgradeRequired = register(Status.entityAllowed(426, "Upgrade Required"))
  val PreconditionRequired = register(Status.entityAllowed(428, "Precondition Required"))
  val TooManyRequests = register(Status.entityAllowed(429, "Too Many Requests"))
  val RequestHeaderFieldsTooLarge = register(
    Status.entityAllowed(431, "Request Header Fields Too Large"))
  val UnavailableForLegalReasons = register(
    Status.entityAllowed(451, "Unavailable For Legal Reasons"))

  val InternalServerError = register(Status.entityAllowed(500, "Internal Server Error"))
  val NotImplemented = register(Status.entityAllowed(501, "Not Implemented"))
  val BadGateway = register(Status.entityAllowed(502, "Bad Gateway"))
  val ServiceUnavailable = register(Status.entityAllowed(503, "Service Unavailable"))
  val GatewayTimeout = register(Status.entityAllowed(504, "Gateway Timeout"))
  val HttpVersionNotSupported = register(Status.entityAllowed(505, "HTTP Version not supported"))
  val VariantAlsoNegotiates = register(Status.entityAllowed(506, "Variant Also Negotiates"))
  val InsufficientStorage = register(Status.entityAllowed(507, "Insufficient Storage"))
  val LoopDetected = register(Status.entityAllowed(508, "Loop Detected"))
  val NotExtended = register(Status.entityAllowed(510, "Not Extended"))
  val NetworkAuthenticationRequired = register(
    Status.entityAllowed(511, "Network Authentication Required"))
  // scalastyle:on magic.number

  implicit val http4sOrderForStatus: Order[Status] = Order.fromOrdering[Status]
  implicit val http4sShowForStatus: Show[Status] = Show.fromToString[Status]
}
