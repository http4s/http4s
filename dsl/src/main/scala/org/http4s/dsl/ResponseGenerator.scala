package org.http4s
package dsl

import scalaz.concurrent.Task

import impl._
import util.CaseInsensitiveString._

object Continue extends EmptyResponseGenerator {
  val status: Status = Status.Continue
}

object SwitchingProtocols extends EmptyResponseGenerator {
  val status: Status = Status.SwitchingProtocols
  // TODO support Upgrade header
}

object Ok extends EntityResponseGenerator {
  val status: Status = Status.Ok
}

object Created extends EntityResponseGenerator {
  val status: Status = Status.Created
}

object Accepted extends EntityResponseGenerator {
  val status: Status = Status.Accepted
}

object NonAuthoritativeInformation extends EntityResponseGenerator {
  val status: Status = Status.NonAuthoritativeInformation
}

object NoContent extends EmptyResponseGenerator {
  val status: Status = Status.NoContent
}

object ResetContent extends EmptyResponseGenerator {
  val status: Status = Status.ResetContent
}

object PartialContent extends EntityResponseGenerator {
  val status: Status = Status.PartialContent
  // TODO helpers for Content-Range and multipart/byteranges
}

object MultiStatus extends EntityResponseGenerator {
  val status: Status = Status.MultiStatus
}

object AlreadyReported extends EntityResponseGenerator {
  val status: Status = Status.AlreadyReported
}

object IMUsed extends EntityResponseGenerator {
  val status: Status = Status.IMUsed
}

object MultipleChoices extends LocationResponseGenerator {
  val status: Status = Status.MultipleChoices
}

object MovedPermanently extends LocationResponseGenerator {
  val status: Status = Status.MovedPermanently
}

object Found extends LocationResponseGenerator {
  val status: Status = Status.Found
}

object SeeOther extends LocationResponseGenerator {
  val status: Status = Status.SeeOther
}

object NotModified extends EntityResponseGenerator {
  val status: Status = Status.NotModified
}

// Note: UseProxy is deprecated in RFC7231, so we will not ease its creation here.

object TemporaryRedirect extends LocationResponseGenerator {
  val status: Status = Status.TemporaryRedirect
}

object PermanentRedirect extends LocationResponseGenerator {
  val status: Status = Status.PermanentRedirect
}

object BadRequest extends EntityResponseGenerator {
  val status: Status = Status.BadRequest
}

object Unauthorized extends WwwAuthenticateResponseGenerator {
  val status: Status = Status.Unauthorized
}

object PaymentRequired extends EntityResponseGenerator {
  val status: Status = Status.PaymentRequired
}

object Forbidden extends EntityResponseGenerator {
  val status: Status = Status.Forbidden
}

object NotFound extends EntityResponseGenerator {
  val status: Status = Status.NotFound
}

object MethodNotAllowed extends ResponseGenerator {
  val status: Status = Status.MethodNotAllowed
  def apply(method: Method*) = Task.now {
    Response(status).putHeaders(Header.Raw("Allow".ci, method.mkString(",")))
  }
}

object NotAcceptable extends EntityResponseGenerator {
  val status: Status = Status.NotAcceptable
}

object ProxyAuthenticationRequired extends EntityResponseGenerator {
  val status: Status = Status.ProxyAuthenticationRequired
}

object RequestTimeout extends EntityResponseGenerator {
  val status: Status = Status.RequestTimeout
  // TODO send Connection: close?
}

object Conflict extends EntityResponseGenerator {
  val status: Status = Status.Conflict
}

object Gone extends EntityResponseGenerator {
  val status: Status = Status.Gone
}

object LengthRequired extends EntityResponseGenerator {
  val status: Status = Status.LengthRequired
}

object PreconditionFailed extends EntityResponseGenerator {
  val status: Status = Status.PreconditionFailed
}

object PayloadTooLarge extends EntityResponseGenerator {
  val status: Status = Status.PayloadTooLarge
}

object UriTooLong extends EntityResponseGenerator {
  val status: Status = Status.UriTooLong
}

object UnsupportedMediaType extends EntityResponseGenerator {
  val status: Status = Status.UnsupportedMediaType
}

object RangeNotSatisfiable extends EntityResponseGenerator {
  val status: Status = Status.RangeNotSatisfiable
}

object ExpectationFailed extends EntityResponseGenerator {
  val status: Status = Status.ExpectationFailed
}

object UnprocessableEntity extends EntityResponseGenerator {
  val status: Status = Status.UnprocessableEntity
}

object Locked extends EntityResponseGenerator {
  val status: Status = Status.Locked
}

object FailedDependency extends EntityResponseGenerator {
  val status: Status = Status.FailedDependency
}

object UpgradeRequired extends EntityResponseGenerator {
  val status: Status = Status.UpgradeRequired
  // TODO Mandatory upgrade field
}

object PreconditionRequired extends EntityResponseGenerator {
  val status: Status = Status.PreconditionRequired
}

object TooManyRequests extends EntityResponseGenerator {
  val status: Status = Status.TooManyRequests
}

object RequestHeaderFieldsTooLarge extends EntityResponseGenerator {
  val status: Status = Status.RequestHeaderFieldsTooLarge
}

object InternalServerError extends EntityResponseGenerator {
  val status: Status = Status.InternalServerError
}

object NotImplemented extends EntityResponseGenerator {
  val status: Status = Status.NotImplemented
}

object BadGateway extends EntityResponseGenerator {
  val status: Status = Status.BadGateway
}

object ServiceUnavailable extends EntityResponseGenerator {
  val status: Status = Status.ServiceUnavailable
}

object GatewayTimeout extends EntityResponseGenerator {
  val status: Status = Status.GatewayTimeout
}

object HttpVersionNotSupported extends EntityResponseGenerator {
  val status: Status = Status.HttpVersionNotSupported
}

object VariantAlsoNegotiates extends EntityResponseGenerator {
  val status: Status = Status.VariantAlsoNegotiates
}

object InsufficientStorage extends EntityResponseGenerator {
  val status: Status = Status.InsufficientStorage
}

object LoopDetected extends EntityResponseGenerator {
  val status: Status = Status.LoopDetected
}

object NotExtended extends EntityResponseGenerator {
  val status: Status = Status.NotExtended
}

object NetworkAuthenticationRequired extends EntityResponseGenerator {
  val status: Status = Status.NetworkAuthenticationRequired
}
