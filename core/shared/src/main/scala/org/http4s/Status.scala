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

import cats.Order
import cats.Show
import org.http4s.Status.ResponseClass
import org.http4s.internal.CharPredicate
import org.http4s.util.Renderable

/** Representation of the HTTP response code and reason
  *
  * '''Note: ''' the reason is not important to the protocol and is not considered in equality checks.
  *
  * @param code HTTP status code
  * @param reason reason for the response. eg, OK
  * @see [[https://datatracker.ietf.org/doc/html/rfc7231#section-6 RFC 7231, Section 6, Response Status Codes]]
  * @see [[http://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml IANA Status Code Registry]]
  */
sealed abstract case class Status private (code: Int)(
    val reason: String,
    val isEntityAllowed: Boolean,
) extends Ordered[Status]
    with Renderable {

  val responseClass: ResponseClass =
    if (code < 200) Status.Informational
    else if (code < 300) Status.Successful
    else if (code < 400) Status.Redirection
    else if (code < 500) Status.ClientError
    else Status.ServerError

  def compare(that: Status): Int = code - that.code

  def isSuccess: Boolean = responseClass.isSuccess

  @deprecated(
    "Custom status phrases will be removed in 1.0. They are an optional feature, pose a security risk, and already unsupported on some backends.",
    "0.22.6",
  )
  def withReason(reason: String): Status = Status(code, reason, isEntityAllowed)

  /** A sanitized [[reason]] phrase. Blank if reason is invalid per
    * RFC7230, otherwise equivalent to reason.
    */
  def sanitizedReason: String = ""

  override def render(writer: org.http4s.util.Writer): writer.type =
    writer << code << ' ' << sanitizedReason

  /** Helpers for for matching against a [[Response]] */
  def unapply[F[_]](msg: Response[F]): Option[Response[F]] =
    if (msg.status == this) Some(msg) else None
}

object Status {
  import Registry._

  private val ReasonPhrasePredicate =
    CharPredicate("\t ") ++ CharPredicate(0x21.toChar to 0x7e.toChar) ++ CharPredicate(
      0x80.toChar to Char.MaxValue
    )

  @deprecated(
    "Use fromInt(Int). This does not validate the code. Furthermore, custom status phrases will be removed in 1.0. They are an optional feature, pose a security risk, and already unsupported on some backends. For simplicity, we'll now assume that entities are allowed on all custom status codes.",
    "0.22.6",
  )
  def apply(code: Int, reason: String = "", isEntityAllowed: Boolean = true): Status =
    new Status(code)(reason, isEntityAllowed) {
      override lazy val sanitizedReason: String =
        if (this.reason.forall(ReasonPhrasePredicate))
          this.reason
        else
          ""
    }

  @deprecated("Use fromInt(Int). This does not validate the code.", "0.22.6")
  def apply(code: Int): Status =
    apply(code, "", isEntityAllowed = true)

  private def trust(code: Int, reason: String, isEntityAllowed: Boolean = true): Status =
    new Status(code)(reason, isEntityAllowed) {
      override def sanitizedReason: String = this.reason
    }

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

  private[http4s] val MinCode = 100
  private[http4s] val MaxCode = 599

  def fromInt(code: Int): ParseResult[Status] =
    withRangeCheck(code) {
      lookup(code) match {
        case right: Right[_, _] => right
        case _ => ParseResult.success(trust(code, ""))
      }
    }

  @deprecated(
    "Use fromInt. Custom status phrases will be removed in 1.0. They are an optional feature, pose a security risk, and already unsupported on some backends.",
    "0.22.6",
  )
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
  val Continue: Status = register(trust(100, "Continue", isEntityAllowed = false))
  val SwitchingProtocols: Status = register(
    trust(101, "Switching Protocols", isEntityAllowed = false)
  )
  val Processing: Status = register(trust(102, "Processing", isEntityAllowed = false))
  val EarlyHints: Status = register(trust(103, "Early Hints", isEntityAllowed = false))

  val Ok: Status = register(trust(200, "OK"))
  val Created: Status = register(trust(201, "Created"))
  val Accepted: Status = register(trust(202, "Accepted"))
  val NonAuthoritativeInformation: Status = register(trust(203, "Non-Authoritative Information"))
  val NoContent: Status = register(trust(204, "No Content", isEntityAllowed = false))
  val ResetContent: Status = register(trust(205, "Reset Content", isEntityAllowed = false))
  val PartialContent: Status = register(trust(206, "Partial Content"))
  val MultiStatus: Status = register(trust(207, "Multi-Status"))
  val AlreadyReported: Status = register(trust(208, "Already Reported"))
  val IMUsed: Status = register(trust(226, "IM Used"))

  val MultipleChoices: Status = register(trust(300, "Multiple Choices"))
  val MovedPermanently: Status = register(trust(301, "Moved Permanently"))
  val Found: Status = register(trust(302, "Found"))
  val SeeOther: Status = register(trust(303, "See Other"))
  val NotModified: Status = register(trust(304, "Not Modified", isEntityAllowed = false))
  val UseProxy: Status = register(trust(305, "Use Proxy"))
  val TemporaryRedirect: Status = register(trust(307, "Temporary Redirect"))
  val PermanentRedirect: Status = register(trust(308, "Permanent Redirect"))

  val BadRequest: Status = register(trust(400, "Bad Request"))
  val Unauthorized: Status = register(trust(401, "Unauthorized"))
  val PaymentRequired: Status = register(trust(402, "Payment Required"))
  val Forbidden: Status = register(trust(403, "Forbidden"))
  val NotFound: Status = register(trust(404, "Not Found"))
  val MethodNotAllowed: Status = register(trust(405, "Method Not Allowed"))
  val NotAcceptable: Status = register(trust(406, "Not Acceptable"))
  val ProxyAuthenticationRequired: Status = register(trust(407, "Proxy Authentication Required"))
  val RequestTimeout: Status = register(trust(408, "Request Timeout"))
  val Conflict: Status = register(trust(409, "Conflict"))
  val Gone: Status = register(trust(410, "Gone"))
  val LengthRequired: Status = register(trust(411, "Length Required"))
  val PreconditionFailed: Status = register(trust(412, "Precondition Failed"))
  val PayloadTooLarge: Status = register(trust(413, "Payload Too Large"))
  val UriTooLong: Status = register(trust(414, "URI Too Long"))
  val UnsupportedMediaType: Status = register(trust(415, "Unsupported Media Type"))
  val RangeNotSatisfiable: Status = register(trust(416, "Range Not Satisfiable"))
  val ExpectationFailed: Status = register(trust(417, "Expectation Failed"))
  val ImATeapot: Status = register(trust(418, "I'm A Teapot"))
  val MisdirectedRequest: Status = register(trust(421, "Misdirected Request"))
  val UnprocessableEntity: Status = register(trust(422, "Unprocessable Entity"))
  val Locked: Status = register(trust(423, "Locked"))
  val FailedDependency: Status = register(trust(424, "Failed Dependency"))
  val TooEarly: Status = register(trust(425, "Too Early"))
  val UpgradeRequired: Status = register(trust(426, "Upgrade Required"))
  val PreconditionRequired: Status = register(trust(428, "Precondition Required"))
  val TooManyRequests: Status = register(trust(429, "Too Many Requests"))
  val RequestHeaderFieldsTooLarge: Status = register(trust(431, "Request Header Fields Too Large"))
  val UnavailableForLegalReasons: Status = register(trust(451, "Unavailable For Legal Reasons"))

  val InternalServerError: Status = register(trust(500, "Internal Server Error"))
  val NotImplemented: Status = register(trust(501, "Not Implemented"))
  val BadGateway: Status = register(trust(502, "Bad Gateway"))
  val ServiceUnavailable: Status = register(trust(503, "Service Unavailable"))
  val GatewayTimeout: Status = register(trust(504, "Gateway Timeout"))
  val HttpVersionNotSupported: Status = register(trust(505, "HTTP Version not supported"))
  val VariantAlsoNegotiates: Status = register(trust(506, "Variant Also Negotiates"))
  val InsufficientStorage: Status = register(trust(507, "Insufficient Storage"))
  val LoopDetected: Status = register(trust(508, "Loop Detected"))
  val NotExtended: Status = register(trust(510, "Not Extended"))
  val NetworkAuthenticationRequired: Status = register(
    trust(511, "Network Authentication Required")
  )

  implicit val http4sOrderForStatus: Order[Status] = Order.fromOrdering[Status]
  implicit val http4sShowForStatus: Show[Status] = Show.fromToString[Status]
}
