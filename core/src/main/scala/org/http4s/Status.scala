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
sealed abstract case class Status private (code: Int)(val reason: String)
    extends Ordered[Status]
    with Renderable {

  type IsEntityAllowed <: Boolean
  protected val _isEntityAllowed: IsEntityAllowed
  def isEntityAllowed: Boolean = _isEntityAllowed

  val responseClass: ResponseClass =
    if (code < 200) Status.Informational
    else if (code < 300) Status.Successful
    else if (code < 400) Status.Redirection
    else if (code < 500) Status.ClientError
    else Status.ServerError

  def compare(that: Status): Int = code - that.code

  def isSuccess: Boolean = responseClass.isSuccess

  def withReason(reason: String): Status.Aux[IsEntityAllowed] =
    Status.apply1(code, reason, _isEntityAllowed)

  override def render(writer: org.http4s.util.Writer): writer.type = writer << code << ' ' << reason

  /** Helpers for for matching against a [[Response]] */
  def unapply[F[_]](msg: Response[F]): Option[Response[F]] =
    if (msg.status == this) Some(msg) else None
}

object Status {
  import Registry._

  type Aux[IsEntityAllowed0 <: Boolean] = Status { type IsEntityAllowed = IsEntityAllowed0 }

  def apply(code: Int, reason: String = "", isEntityAllowed: Boolean = true): Status =
    apply1[Boolean](code, reason, isEntityAllowed)

  def apply1[IsEntityAllowed0 <: Boolean](
      code: Int,
      reason: String = "",
      isEntityAllowed: IsEntityAllowed0): Status.Aux[IsEntityAllowed0] = {
    val __isEntityAllowed = isEntityAllowed
    new Status(code)(reason) {
      type IsEntityAllowed = IsEntityAllowed0
      val _isEntityAllowed = __isEntityAllowed
    }
  }

  def apply0(code: Int, reason: String = ""): Status.Aux[true] = apply1[true](code, reason, true)

  def applyNoEnt(code: Int, reason: String = ""): Status.Aux[false] =
    apply1[false](code, reason, false)

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

    def register(status: Status): Status = {
      registry(status.code) = Right(status)
      status
    }

    def all: List[Status] = registry.collect { case Right(status) => status }.toList
  }

  /** Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
    */
  // scalastyle:off magic.number
  val Continue = register(Status.applyNoEnt(100, "Continue"))
  val SwitchingProtocols = register(Status.applyNoEnt(101, "Switching Protocols"))
  val Processing = register(Status.applyNoEnt(102, "Processing"))
  val EarlyHints = register(Status.applyNoEnt(103, "Early Hints"))

  val Ok = register(Status.apply0(200, "OK"))
  val Created = register(Status.apply0(201, "Created"))
  val Accepted = register(Status.apply0(202, "Accepted"))
  val NonAuthoritativeInformation = register(Status.apply0(203, "Non-Authoritative Information"))
  val NoContent = register(Status.applyNoEnt(204, "No Content"))
  val ResetContent = register(Status.applyNoEnt(205, "Reset Content"))
  val PartialContent = register(Status.apply0(206, "Partial Content"))
  val MultiStatus = register(Status.apply0(207, "Multi-Status"))
  val AlreadyReported = register(Status.apply0(208, "Already Reported"))
  val IMUsed = register(Status.apply0(226, "IM Used"))

  val MultipleChoices = register(Status.apply0(300, "Multiple Choices"))
  val MovedPermanently = register(Status.apply0(301, "Moved Permanently"))
  val Found = register(Status.apply0(302, "Found"))
  val SeeOther = register(Status.apply0(303, "See Other"))
  val NotModified = register(Status.applyNoEnt(304, "Not Modified"))
  val UseProxy = register(Status.apply0(305, "Use Proxy"))
  val TemporaryRedirect = register(Status.apply0(307, "Temporary Redirect"))
  val PermanentRedirect = register(Status.apply0(308, "Permanent Redirect"))

  val BadRequest = register(Status.apply0(400, "Bad Request"))
  val Unauthorized = register(Status.apply0(401, "Unauthorized"))
  val PaymentRequired = register(Status.apply0(402, "Payment Required"))
  val Forbidden = register(Status.apply0(403, "Forbidden"))
  val NotFound = register(Status.apply0(404, "Not Found"))
  val MethodNotAllowed = register(Status.apply0(405, "Method Not Allowed"))
  val NotAcceptable = register(Status.apply0(406, "Not Acceptable"))
  val ProxyAuthenticationRequired = register(Status.apply0(407, "Proxy Authentication Required"))
  val RequestTimeout = register(Status.apply0(408, "Request Timeout"))
  val Conflict = register(Status.apply0(409, "Conflict"))
  val Gone = register(Status.apply0(410, "Gone"))
  val LengthRequired = register(Status.apply0(411, "Length Required"))
  val PreconditionFailed = register(Status.apply0(412, "Precondition Failed"))
  val PayloadTooLarge = register(Status.apply0(413, "Payload Too Large"))
  val UriTooLong = register(Status.apply0(414, "URI Too Long"))
  val UnsupportedMediaType = register(Status.apply0(415, "Unsupported Media Type"))
  val RangeNotSatisfiable = register(Status.apply0(416, "Range Not Satisfiable"))
  val ExpectationFailed = register(Status.apply0(417, "Expectation Failed"))
  val ImATeapot = register(Status.apply0(418, "I'm A Teapot"))
  val MisdirectedRequest = register(Status.apply0(421, "Misdirected Request"))
  val UnprocessableEntity = register(Status.apply0(422, "Unprocessable Entity"))
  val Locked = register(Status.apply0(423, "Locked"))
  val FailedDependency = register(Status.apply0(424, "Failed Dependency"))
  val TooEarly = register(Status.apply0(425, "Too Early"))
  val UpgradeRequired = register(Status.apply0(426, "Upgrade Required"))
  val PreconditionRequired = register(Status.apply0(428, "Precondition Required"))
  val TooManyRequests = register(Status.apply0(429, "Too Many Requests"))
  val RequestHeaderFieldsTooLarge = register(Status.apply0(431, "Request Header Fields Too Large"))
  val UnavailableForLegalReasons = register(Status.apply0(451, "Unavailable For Legal Reasons"))

  val InternalServerError = register(Status.apply0(500, "Internal Server Error"))
  val NotImplemented = register(Status.apply0(501, "Not Implemented"))
  val BadGateway = register(Status.apply0(502, "Bad Gateway"))
  val ServiceUnavailable = register(Status.apply0(503, "Service Unavailable"))
  val GatewayTimeout = register(Status.apply0(504, "Gateway Timeout"))
  val HttpVersionNotSupported = register(Status.apply0(505, "HTTP Version not supported"))
  val VariantAlsoNegotiates = register(Status.apply0(506, "Variant Also Negotiates"))
  val InsufficientStorage = register(Status.apply0(507, "Insufficient Storage"))
  val LoopDetected = register(Status.apply0(508, "Loop Detected"))
  val NotExtended = register(Status.apply0(510, "Not Extended"))
  val NetworkAuthenticationRequired = register(
    Status.apply0(511, "Network Authentication Required"))
  // scalastyle:on magic.number

  implicit val http4sOrderForStatus: Order[Status] = Order.fromOrdering[Status]
  implicit val http4sShowForStatus: Show[Status] = Show.fromToString[Status]
}
