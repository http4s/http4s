package org.http4s
package dsl
package impl

import cats.Applicative
import org.http4s.Status._
import org.http4s.headers.`Content-Length`

trait Responses {
  import Responses._

  implicit class http4sContinueSyntax(status: Continue.type) {
    def apply[F[_]]: ContinueOps[F] = new ContinueOps[F](status)
  }
  implicit class http4sSwitchingProtocolsSyntax(status: SwitchingProtocols.type) {
    def apply[F[_]]: SwitchingProtocolsOps[F] = new SwitchingProtocolsOps[F](status)
  }
  implicit class http4sOkSyntax(status: Ok.type) {
    def apply[F[_]]: OkOps[F] = new OkOps[F](status)
  }
  implicit class http4sCreatedSyntax(status: Created.type) {
    def apply[F[_]]: CreatedOps[F] = new CreatedOps[F](status)
  }
  implicit class http4sAcceptedSyntax(status: Accepted.type) {
    def apply[F[_]]: AcceptedOps[F] = new AcceptedOps[F](status)
  }
  implicit class http4sNonAuthoritativeInformationSyntax(
      status: NonAuthoritativeInformation.type) {
    def apply[F[_]]: NonAuthoritativeInformationOps[F] = new NonAuthoritativeInformationOps[F](status)
  }
  implicit class http4sNoContentSyntax(status: NoContent.type) {
    def apply[F[_]]: NoContentOps[F] = new NoContentOps[F](status)
  }
  implicit class http4sResetContentSyntax(status: ResetContent.type) {
    def apply[F[_]]: ResetContentOps[F] = new ResetContentOps[F](status)
  }
  implicit class http4sPartialContentSyntax(status: PartialContent.type) {
    def apply[F[_]]: PartialContentOps[F] = new PartialContentOps[F](status)
  }
  implicit class http4sMultiStatusSyntax(status: Status.MultiStatus.type) {
    def apply[F[_]]: MultiStatusOps[F] = new MultiStatusOps[F](status)
  }
  implicit class http4sAlreadyReportedSyntax(status: AlreadyReported.type) {
    def apply[F[_]]: AlreadyReportedOps[F] = new AlreadyReportedOps[F](status)
  }
  implicit class http4sIMUsedSyntax(status: IMUsed.type) {
    def apply[F[_]]: IMUsedOps[F] = new IMUsedOps[F](status)
  }

  implicit class http4sMultipleChoicesSyntax(status: MultipleChoices.type) {
    def apply[F[_]]: MultipleChoicesOps[F] = new MultipleChoicesOps[F](status)
  }
  implicit class http4sMovedPermanentlySyntax(status: MovedPermanently.type) {
    def apply[F[_]]: MovedPermanentlyOps[F] = new MovedPermanentlyOps[F](status)
  }
  implicit class http4sFoundSyntax(status: Found.type) {
    def apply[F[_]]: FoundOps[F] = new FoundOps[F](status)
  }
  implicit class http4sSeeOtherSyntax(status: SeeOther.type) {
    def apply[F[_]]: SeeOtherOps[F] = new SeeOtherOps[F](status)
  }
  implicit class http4sNotModifiedSyntax(status: NotModified.type) {
    def apply[F[_]]: NotModifiedOps[F] = new NotModifiedOps[F](status)
  }
  implicit class http4sTemporaryRedirectSyntax(
      status: TemporaryRedirect.type) {
    def apply[F[_]]: TemporaryRedirectOps[F] = new TemporaryRedirectOps[F](status)
  }
  implicit class http4sPermanentRedirectSyntax(
      status: PermanentRedirect.type) {
    def apply[F[_]]: PermanentRedirectOps[F] = new PermanentRedirectOps[F](status)
  }

  implicit class http4sBadRequestSyntax(status: BadRequest.type) {
    def apply[F[_]]: BadRequestOps[F] = new BadRequestOps[F](status)
  }
  implicit class http4sUnauthorizedSyntax(status: Unauthorized.type) {
    def apply[F[_]]: UnauthorizedOps[F] = new UnauthorizedOps[F](status)
  }
  implicit class http4sPaymentRequiredSyntax(status: PaymentRequired.type) {
    def apply[F[_]]: PaymentRequiredOps[F] = new PaymentRequiredOps[F](status)
  }
  implicit class http4sForbiddenSyntax(status: Forbidden.type) {
    def apply[F[_]]: ForbiddenOps[F] = new ForbiddenOps[F](status)
  }
  implicit class http4sNotFoundSyntax(status: NotFound.type) {
    def apply[F[_]]: NotFoundOps[F] = new NotFoundOps[F](status)
  }
  implicit class http4sMethodNotAllowedSyntax(status: MethodNotAllowed.type) {
    def apply[F[_]]: MethodNotAllowedOps[F] = new MethodNotAllowedOps[F](status)
  }
  implicit class http4sNotAcceptableSyntax(status: NotAcceptable.type) {
    def apply[F[_]]: NotAcceptableOps[F] = new NotAcceptableOps[F](status)
  }
  implicit class http4sProxyAuthenticationRequiredSyntax(
      status: ProxyAuthenticationRequired.type) {
    def apply[F[_]]: ProxyAuthenticationRequiredOps[F] = new ProxyAuthenticationRequiredOps[F](status)
  }
  implicit class http4sRequestTimeoutSyntax(status: RequestTimeout.type) {
    def apply[F[_]]: RequestTimeoutOps[F] = new RequestTimeoutOps[F](status)
  }
  implicit class http4sConflictSyntax(status: Conflict.type) {
    def apply[F[_]]: ConflictOps[F] = new ConflictOps[F](status)
  }
  implicit class http4sGoneSyntax(status: Gone.type) {
    def apply[F[_]]: GoneOps[F] = new GoneOps[F](status)
  }
  implicit class http4sLengthRequiredSyntax(status: LengthRequired.type) {
    def apply[F[_]]: LengthRequiredOps[F] = new LengthRequiredOps[F](status)
  }
  implicit class http4sPreconditionFailedSyntax(
      status: PreconditionFailed.type) {
    def apply[F[_]]: PreconditionFailedOps[F] = new PreconditionFailedOps[F](status)
  }
  implicit class http4sPayloadTooLargeSyntax(status: PayloadTooLarge.type) {
    def apply[F[_]]: PayloadTooLargeOps[F] = new PayloadTooLargeOps[F](status)
  }
  implicit class http4sUriTooLongSyntax(status: UriTooLong.type) {
    def apply[F[_]]: UriTooLongOps[F] = new UriTooLongOps[F](status)
  }
  implicit class http4sUnsupportedMediaTypeSyntax(
      status: UnsupportedMediaType.type) {
    def apply[F[_]]: UnsupportedMediaTypeOps[F] = new UnsupportedMediaTypeOps[F](status)
  }
  implicit class http4sRangeNotSatisfiableSyntax(
      status: RangeNotSatisfiable.type) {
    def apply[F[_]]: RangeNotSatisfiableOps[F] = new RangeNotSatisfiableOps[F](status)
  }
  implicit class http4sExpectationFailedSyntax(
      status: ExpectationFailed.type) {
    def apply[F[_]]: ExpectationFailedOps[F] = new ExpectationFailedOps[F](status)
  }
  implicit class http4sUnprocessableEntitySyntax(
      status: UnprocessableEntity.type) {
    def apply[F[_]]: UnprocessableEntityOps[F] = new UnprocessableEntityOps[F](status)
  }
  implicit class http4sLockedSyntax(status: Locked.type) {
    def apply[F[_]]: LockedOps[F] = new LockedOps[F](status)
  }
  implicit class http4sFailedDependencySyntax(status: FailedDependency.type) {
    def apply[F[_]]: FailedDependencyOps[F] = new FailedDependencyOps[F](status)
  }
  implicit class http4sUpgradeRequiredSyntax(status: UpgradeRequired.type) {
    def apply[F[_]]: UpgradeRequiredOps[F] = new UpgradeRequiredOps[F](status)
  }
  implicit class http4sPreconditionRequiredSyntax(
      status: PreconditionRequired.type) {
    def apply[F[_]]: PreconditionRequiredOps[F] = new PreconditionRequiredOps[F](status)
  }
  implicit class http4sTooManyRequestsSyntax(status: TooManyRequests.type) {
    def apply[F[_]]: TooManyRequestsOps[F] = new TooManyRequestsOps[F](status)
  }
  implicit class http4sRequestHeaderFieldsTooLargeSyntax(
      status: RequestHeaderFieldsTooLarge.type) {
    def apply[F[_]]: RequestHeaderFieldsTooLargeOps[F] = new RequestHeaderFieldsTooLargeOps[F](status)
  }
  implicit class http4sUnavailableForLegalReasonsSyntax(
      status: UnavailableForLegalReasons.type) {
    def apply[F[_]]: UnavailableForLegalReasonsOps[F] = new UnavailableForLegalReasonsOps[F](status)
  }

  implicit class http4sInternalServerErrorSyntax(
      status: InternalServerError.type) {
    def apply[F[_]]: InternalServerErrorOps[F] = new InternalServerErrorOps[F](status)
  }
  implicit class http4sNotImplementedSyntax(status: NotImplemented.type) {
    def apply[F[_]]: NotImplementedOps[F] = new NotImplementedOps[F](status)
  }
  implicit class http4sBadGatewaySyntax(status: BadGateway.type) {
    def apply[F[_]]: BadGatewayOps[F] = new BadGatewayOps[F](status)
  }
  implicit class http4sServiceUnavailableSyntax(
      status: ServiceUnavailable.type) {
    def apply[F[_]]: ServiceUnavailableOps[F] = new ServiceUnavailableOps[F](status)
  }
  implicit class http4sGatewayTimeoutSyntax(status: GatewayTimeout.type) {
    def apply[F[_]]: GatewayTimeoutOps[F] = new GatewayTimeoutOps[F](status)
  }
  implicit class http4sHttpVersionNotSupportedSyntax(
      status: HttpVersionNotSupported.type) {
    def apply[F[_]]: HttpVersionNotSupportedOps[F] = new HttpVersionNotSupportedOps[F](status)
  }
  implicit class http4sVariantAlsoNegotiatesSyntax(
      status: VariantAlsoNegotiates.type) {
    def apply[F[_]]: VariantAlsoNegotiatesOps[F] = new VariantAlsoNegotiatesOps[F](status)
  }
  implicit class http4sInsufficientStorageSyntax(
      status: InsufficientStorage.type) {
    def apply[F[_]]: InsufficientStorageOps[F] = new InsufficientStorageOps[F](status)
  }
  implicit class http4sLoopDetectedSyntax(status: LoopDetected.type) {
    def apply[F[_]]: LoopDetectedOps[F] = new LoopDetectedOps[F](status)
  }
  implicit class http4sNotExtendedSyntax(status: NotExtended.type) {
    def apply[F[_]]: NotExtendedOps[F] = new NotExtendedOps[F](status)
  }
  implicit class http4sNetworkAuthenticationRequiredSyntax(
      status: NetworkAuthenticationRequired.type) {
    def apply[F[_]]: NetworkAuthenticationRequiredOps[F] = new NetworkAuthenticationRequiredOps[F](status)
  }
}

object Responses {
  final class ContinueOps[F[_]](val status: Continue.type)
      extends AnyVal
      with EmptyResponseGenerator[F]
  // TODO support Upgrade header
  final class SwitchingProtocolsOps[F[_]](val status: SwitchingProtocols.type)
      extends AnyVal
      with EmptyResponseGenerator[F]
  final class OkOps[F[_]](val status: Ok.type) extends AnyVal with EntityResponseGenerator[F]

  final class CreatedOps[F[_]](val status: Created.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class AcceptedOps[F[_]](val status: Accepted.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class NonAuthoritativeInformationOps[F[_]](val status: NonAuthoritativeInformation.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class NoContentOps[F[_]](val status: NoContent.type)
      extends AnyVal
      with EmptyResponseGenerator[F]
  final class ResetContentOps[F[_]](val status: ResetContent.type)
      extends AnyVal
      with EmptyResponseGenerator[F] {
    override def apply(headers: Header*)(implicit F: Applicative[F]): F[Response[F]] =
      F.pure(Response(ResetContent, headers = Headers(`Content-Length`.zero +: headers: _*)))
  }
  // TODO helpers for Content-Range and multipart/byteranges
  final class PartialContentOps[F[_]](val status: PartialContent.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class MultiStatusOps[F[_]](val status: Status.MultiStatus.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class AlreadyReportedOps[F[_]](val status: AlreadyReported.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class IMUsedOps[F[_]](val status: IMUsed.type)
      extends AnyVal
      with EntityResponseGenerator[F]

  final class MultipleChoicesOps[F[_]](val status: MultipleChoices.type)
      extends AnyVal
      with LocationResponseGenerator[F]
  final class MovedPermanentlyOps[F[_]](val status: MovedPermanently.type)
      extends AnyVal
      with LocationResponseGenerator[F]
  final class FoundOps[F[_]](val status: Found.type)
      extends AnyVal
      with LocationResponseGenerator[F]
  final class SeeOtherOps[F[_]](val status: SeeOther.type)
      extends AnyVal
      with LocationResponseGenerator[F]
  final class NotModifiedOps[F[_]](val status: NotModified.type)
      extends AnyVal
      with EmptyResponseGenerator[F]
  // Note: UseProxy is deprecated in RFC7231, so we will not ease its creation here.
  final class TemporaryRedirectOps[F[_]](val status: TemporaryRedirect.type)
      extends AnyVal
      with LocationResponseGenerator[F]
  final class PermanentRedirectOps[F[_]](val status: PermanentRedirect.type)
      extends AnyVal
      with LocationResponseGenerator[F]

  final class BadRequestOps[F[_]](val status: BadRequest.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class UnauthorizedOps[F[_]](val status: Unauthorized.type)
      extends AnyVal
      with WwwAuthenticateResponseGenerator[F]
  final class PaymentRequiredOps[F[_]](val status: PaymentRequired.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class ForbiddenOps[F[_]](val status: Forbidden.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class NotFoundOps[F[_]](val status: NotFound.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class MethodNotAllowedOps[F[_]](val status: MethodNotAllowed.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class NotAcceptableOps[F[_]](val status: NotAcceptable.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class ProxyAuthenticationRequiredOps[F[_]](val status: ProxyAuthenticationRequired.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  // TODO send Connection: close?
  final class RequestTimeoutOps[F[_]](val status: RequestTimeout.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class ConflictOps[F[_]](val status: Conflict.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class GoneOps[F[_]](val status: Gone.type) extends AnyVal with EntityResponseGenerator[F]
  final class LengthRequiredOps[F[_]](val status: LengthRequired.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class PreconditionFailedOps[F[_]](val status: PreconditionFailed.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class PayloadTooLargeOps[F[_]](val status: PayloadTooLarge.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class UriTooLongOps[F[_]](val status: UriTooLong.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class UnsupportedMediaTypeOps[F[_]](val status: UnsupportedMediaType.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class RangeNotSatisfiableOps[F[_]](val status: RangeNotSatisfiable.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class ExpectationFailedOps[F[_]](val status: ExpectationFailed.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class UnprocessableEntityOps[F[_]](val status: UnprocessableEntity.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class LockedOps[F[_]](val status: Locked.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class FailedDependencyOps[F[_]](val status: FailedDependency.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  // TODO Mandatory upgrade field
  final class UpgradeRequiredOps[F[_]](val status: UpgradeRequired.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class PreconditionRequiredOps[F[_]](val status: PreconditionRequired.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class TooManyRequestsOps[F[_]](val status: TooManyRequests.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class RequestHeaderFieldsTooLargeOps[F[_]](val status: RequestHeaderFieldsTooLarge.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class UnavailableForLegalReasonsOps[F[_]](val status: UnavailableForLegalReasons.type)
      extends AnyVal
      with EntityResponseGenerator[F]

  final class InternalServerErrorOps[F[_]](val status: InternalServerError.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class NotImplementedOps[F[_]](val status: NotImplemented.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class BadGatewayOps[F[_]](val status: BadGateway.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class ServiceUnavailableOps[F[_]](val status: ServiceUnavailable.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class GatewayTimeoutOps[F[_]](val status: GatewayTimeout.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class HttpVersionNotSupportedOps[F[_]](val status: HttpVersionNotSupported.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class VariantAlsoNegotiatesOps[F[_]](val status: VariantAlsoNegotiates.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class InsufficientStorageOps[F[_]](val status: InsufficientStorage.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class LoopDetectedOps[F[_]](val status: LoopDetected.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class NotExtendedOps[F[_]](val status: NotExtended.type)
      extends AnyVal
      with EntityResponseGenerator[F]
  final class NetworkAuthenticationRequiredOps[F[_]](val status: NetworkAuthenticationRequired.type)
      extends AnyVal
      with EntityResponseGenerator[F]

}
