package org.http4s
package dsl
package impl

import cats.Applicative
import org.http4s.Status._
import org.http4s.headers.`Content-Length`

trait Responses[F[_], G[_]] {
  import Responses._

  implicit def http4sContinueSyntax(status: Continue.type): ContinueOps[F, G] =
    new ContinueOps[F, G](status)
  implicit def http4sSwitchingProtocolsSyntax(
      status: SwitchingProtocols.type): SwitchingProtocolsOps[F, G] =
    new SwitchingProtocolsOps[F, G](status)
  implicit def http4sEarlyHintsSyntax(status: EarlyHints.type): EarlyHintsOps[F, G] =
    new EarlyHintsOps[F, G](status)
  implicit def http4sOkSyntax(status: Ok.type): OkOps[F, G] = new OkOps[F, G](status)

  implicit def http4sCreatedSyntax(status: Created.type): CreatedOps[F, G] =
    new CreatedOps[F, G](status)
  implicit def http4sAcceptedSyntax(status: Accepted.type): AcceptedOps[F, G] =
    new AcceptedOps[F, G](status)
  implicit def http4sNonAuthoritativeInformationSyntax(
      status: NonAuthoritativeInformation.type): NonAuthoritativeInformationOps[F, G] =
    new NonAuthoritativeInformationOps[F, G](status)
  implicit def http4sNoContentSyntax(status: NoContent.type): NoContentOps[F, G] =
    new NoContentOps[F, G](status)
  implicit def http4sResetContentSyntax(status: ResetContent.type): ResetContentOps[F, G] =
    new ResetContentOps[F, G](status)
  implicit def http4sPartialContentSyntax(status: PartialContent.type): PartialContentOps[F, G] =
    new PartialContentOps[F, G](status)
  implicit def http4sMultiStatusSyntax(status: Status.MultiStatus.type): MultiStatusOps[F, G] =
    new MultiStatusOps[F, G](status)
  implicit def http4sAlreadyReportedSyntax(status: AlreadyReported.type): AlreadyReportedOps[F, G] =
    new AlreadyReportedOps[F, G](status)
  implicit def http4sIMUsedSyntax(status: IMUsed.type): IMUsedOps[F, G] =
    new IMUsedOps[F, G](status)

  implicit def http4sMultipleChoicesSyntax(status: MultipleChoices.type): MultipleChoicesOps[F, G] =
    new MultipleChoicesOps[F, G](status)
  implicit def http4sMovedPermanentlySyntax(
      status: MovedPermanently.type): MovedPermanentlyOps[F, G] =
    new MovedPermanentlyOps[F, G](status)
  implicit def http4sFoundSyntax(status: Found.type): FoundOps[F, G] = new FoundOps[F, G](status)
  implicit def http4sSeeOtherSyntax(status: SeeOther.type): SeeOtherOps[F, G] =
    new SeeOtherOps[F, G](status)
  implicit def http4sNotModifiedSyntax(status: NotModified.type): NotModifiedOps[F, G] =
    new NotModifiedOps[F, G](status)
  implicit def http4sTemporaryRedirectSyntax(
      status: TemporaryRedirect.type): TemporaryRedirectOps[F, G] =
    new TemporaryRedirectOps[F, G](status)
  implicit def http4sPermanentRedirectSyntax(
      status: PermanentRedirect.type): PermanentRedirectOps[F, G] =
    new PermanentRedirectOps[F, G](status)

  implicit def http4sBadRequestSyntax(status: BadRequest.type): BadRequestOps[F, G] =
    new BadRequestOps[F, G](status)
  implicit def http4sUnauthorizedSyntax(status: Unauthorized.type): UnauthorizedOps[F, G] =
    new UnauthorizedOps[F, G](status)
  implicit def http4sPaymentRequiredSyntax(status: PaymentRequired.type): PaymentRequiredOps[F, G] =
    new PaymentRequiredOps[F, G](status)
  implicit def http4sForbiddenSyntax(status: Forbidden.type): ForbiddenOps[F, G] =
    new ForbiddenOps[F, G](status)
  implicit def http4sNotFoundSyntax(status: NotFound.type): NotFoundOps[F, G] =
    new NotFoundOps[F, G](status)
  implicit def http4sMethodNotAllowedSyntax(
      status: MethodNotAllowed.type): MethodNotAllowedOps[F, G] =
    new MethodNotAllowedOps[F, G](status)
  implicit def http4sNotAcceptableSyntax(status: NotAcceptable.type): NotAcceptableOps[F, G] =
    new NotAcceptableOps[F, G](status)
  implicit def http4sProxyAuthenticationRequiredSyntax(
      status: ProxyAuthenticationRequired.type): ProxyAuthenticationRequiredOps[F, G] =
    new ProxyAuthenticationRequiredOps[F, G](status)
  implicit def http4sRequestTimeoutSyntax(status: RequestTimeout.type): RequestTimeoutOps[F, G] =
    new RequestTimeoutOps[F, G](status)
  implicit def http4sConflictSyntax(status: Conflict.type): ConflictOps[F, G] =
    new ConflictOps[F, G](status)
  implicit def http4sGoneSyntax(status: Gone.type): GoneOps[F, G] = new GoneOps[F, G](status)
  implicit def http4sLengthRequiredSyntax(status: LengthRequired.type): LengthRequiredOps[F, G] =
    new LengthRequiredOps[F, G](status)
  implicit def http4sPreconditionFailedSyntax(
      status: PreconditionFailed.type): PreconditionFailedOps[F, G] =
    new PreconditionFailedOps[F, G](status)
  implicit def http4sPayloadTooLargeSyntax(status: PayloadTooLarge.type): PayloadTooLargeOps[F, G] =
    new PayloadTooLargeOps[F, G](status)
  implicit def http4sUriTooLongSyntax(status: UriTooLong.type): UriTooLongOps[F, G] =
    new UriTooLongOps[F, G](status)
  implicit def http4sUnsupportedMediaTypeSyntax(
      status: UnsupportedMediaType.type): UnsupportedMediaTypeOps[F, G] =
    new UnsupportedMediaTypeOps[F, G](status)
  implicit def http4sRangeNotSatisfiableSyntax(
      status: RangeNotSatisfiable.type): RangeNotSatisfiableOps[F, G] =
    new RangeNotSatisfiableOps[F, G](status)
  implicit def http4sExpectationFailedSyntax(
      status: ExpectationFailed.type): ExpectationFailedOps[F, G] =
    new ExpectationFailedOps[F, G](status)
  implicit def http4sMisdirectedRequestSyntax(
      status: MisdirectedRequest.type): MisdirectedRequestOps[F, G] =
    new MisdirectedRequestOps[F, G](status)
  implicit def http4sUnprocessableEntitySyntax(
      status: UnprocessableEntity.type): UnprocessableEntityOps[F, G] =
    new UnprocessableEntityOps[F, G](status)
  implicit def http4sLockedSyntax(status: Locked.type): LockedOps[F, G] =
    new LockedOps[F, G](status)
  implicit def http4sFailedDependencySyntax(
      status: FailedDependency.type): FailedDependencyOps[F, G] =
    new FailedDependencyOps[F, G](status)
  implicit def http4sTooEarlySyntax(status: TooEarly.type): TooEarlyOps[F, G] =
    new TooEarlyOps[F, G](status)
  implicit def http4sUpgradeRequiredSyntax(status: UpgradeRequired.type): UpgradeRequiredOps[F, G] =
    new UpgradeRequiredOps[F, G](status)
  implicit def http4sPreconditionRequiredSyntax(
      status: PreconditionRequired.type): PreconditionRequiredOps[F, G] =
    new PreconditionRequiredOps[F, G](status)
  implicit def http4sTooManyRequestsSyntax(status: TooManyRequests.type): TooManyRequestsOps[F, G] =
    new TooManyRequestsOps[F, G](status)
  implicit def http4sRequestHeaderFieldsTooLargeSyntax(
      status: RequestHeaderFieldsTooLarge.type): RequestHeaderFieldsTooLargeOps[F, G] =
    new RequestHeaderFieldsTooLargeOps[F, G](status)
  implicit def http4sUnavailableForLegalReasonsSyntax(
      status: UnavailableForLegalReasons.type): UnavailableForLegalReasonsOps[F, G] =
    new UnavailableForLegalReasonsOps[F, G](status)

  implicit def http4sInternalServerErrorSyntax(
      status: InternalServerError.type): InternalServerErrorOps[F, G] =
    new InternalServerErrorOps[F, G](status)
  implicit def http4sNotImplementedSyntax(status: NotImplemented.type): NotImplementedOps[F, G] =
    new NotImplementedOps[F, G](status)
  implicit def http4sBadGatewaySyntax(status: BadGateway.type): BadGatewayOps[F, G] =
    new BadGatewayOps[F, G](status)
  implicit def http4sServiceUnavailableSyntax(
      status: ServiceUnavailable.type): ServiceUnavailableOps[F, G] =
    new ServiceUnavailableOps[F, G](status)
  implicit def http4sGatewayTimeoutSyntax(status: GatewayTimeout.type): GatewayTimeoutOps[F, G] =
    new GatewayTimeoutOps[F, G](status)
  implicit def http4sHttpVersionNotSupportedSyntax(
      status: HttpVersionNotSupported.type): HttpVersionNotSupportedOps[F, G] =
    new HttpVersionNotSupportedOps[F, G](status)
  implicit def http4sVariantAlsoNegotiatesSyntax(
      status: VariantAlsoNegotiates.type): VariantAlsoNegotiatesOps[F, G] =
    new VariantAlsoNegotiatesOps[F, G](status)
  implicit def http4sInsufficientStorageSyntax(
      status: InsufficientStorage.type): InsufficientStorageOps[F, G] =
    new InsufficientStorageOps[F, G](status)
  implicit def http4sLoopDetectedSyntax(status: LoopDetected.type): LoopDetectedOps[F, G] =
    new LoopDetectedOps[F, G](status)
  implicit def http4sNotExtendedSyntax(status: NotExtended.type): NotExtendedOps[F, G] =
    new NotExtendedOps[F, G](status)
  implicit def http4sNetworkAuthenticationRequiredSyntax(
      status: NetworkAuthenticationRequired.type): NetworkAuthenticationRequiredOps[F, G] =
    new NetworkAuthenticationRequiredOps[F, G](status)
}

object Responses {
  final class ContinueOps[F[_], G[_]](val status: Continue.type)
      extends AnyVal
      with EmptyResponseGenerator[F, G]
  // TODO support Upgrade header
  final class SwitchingProtocolsOps[F[_], G[_]](val status: SwitchingProtocols.type)
      extends AnyVal
      with EmptyResponseGenerator[F, G]
  final class EarlyHintsOps[F[_], G[_]](val status: EarlyHints.type)
      extends AnyVal
      with EmptyResponseGenerator[F, G]
  final class OkOps[F[_], G[_]](val status: Ok.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]

  final class CreatedOps[F[_], G[_]](val status: Created.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class AcceptedOps[F[_], G[_]](val status: Accepted.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class NonAuthoritativeInformationOps[F[_], G[_]](
      val status: NonAuthoritativeInformation.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class NoContentOps[F[_], G[_]](val status: NoContent.type)
      extends AnyVal
      with EmptyResponseGenerator[F, G]
  final class ResetContentOps[F[_], G[_]](val status: ResetContent.type)
      extends AnyVal
      with EmptyResponseGenerator[F, G] {
    override def apply(headers: Header*)(implicit F: Applicative[F]): F[Response[G]] =
      F.pure(Response(ResetContent, headers = Headers(`Content-Length`.zero +: headers: _*)))
  }
  // TODO helpers for Content-Range and multipart/byteranges
  final class PartialContentOps[F[_], G[_]](val status: PartialContent.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class MultiStatusOps[F[_], G[_]](val status: Status.MultiStatus.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class AlreadyReportedOps[F[_], G[_]](val status: AlreadyReported.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class IMUsedOps[F[_], G[_]](val status: IMUsed.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]

  final class MultipleChoicesOps[F[_], G[_]](val status: MultipleChoices.type)
      extends AnyVal
      with LocationResponseGenerator[F, G]
  final class MovedPermanentlyOps[F[_], G[_]](val status: MovedPermanently.type)
      extends AnyVal
      with LocationResponseGenerator[F, G]
  final class FoundOps[F[_], G[_]](val status: Found.type)
      extends AnyVal
      with LocationResponseGenerator[F, G]
  final class SeeOtherOps[F[_], G[_]](val status: SeeOther.type)
      extends AnyVal
      with LocationResponseGenerator[F, G]
  final class NotModifiedOps[F[_], G[_]](val status: NotModified.type)
      extends AnyVal
      with EmptyResponseGenerator[F, G]
  // Note: UseProxy is deprecated in RFC7231, so we will not ease its creation here.
  final class TemporaryRedirectOps[F[_], G[_]](val status: TemporaryRedirect.type)
      extends AnyVal
      with LocationResponseGenerator[F, G]
  final class PermanentRedirectOps[F[_], G[_]](val status: PermanentRedirect.type)
      extends AnyVal
      with LocationResponseGenerator[F, G]

  final class BadRequestOps[F[_], G[_]](val status: BadRequest.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class UnauthorizedOps[F[_], G[_]](val status: Unauthorized.type)
      extends AnyVal
      with WwwAuthenticateResponseGenerator[F, G]
  final class PaymentRequiredOps[F[_], G[_]](val status: PaymentRequired.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class ForbiddenOps[F[_], G[_]](val status: Forbidden.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class NotFoundOps[F[_], G[_]](val status: NotFound.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class MethodNotAllowedOps[F[_], G[_]](val status: MethodNotAllowed.type)
      extends AnyVal
      with AllowResponseGenerator[F, G]
  final class NotAcceptableOps[F[_], G[_]](val status: NotAcceptable.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class ProxyAuthenticationRequiredOps[F[_], G[_]](
      val status: ProxyAuthenticationRequired.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  // TODO send Connection: close?
  final class RequestTimeoutOps[F[_], G[_]](val status: RequestTimeout.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class ConflictOps[F[_], G[_]](val status: Conflict.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class GoneOps[F[_], G[_]](val status: Gone.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class LengthRequiredOps[F[_], G[_]](val status: LengthRequired.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class PreconditionFailedOps[F[_], G[_]](val status: PreconditionFailed.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class PayloadTooLargeOps[F[_], G[_]](val status: PayloadTooLarge.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class UriTooLongOps[F[_], G[_]](val status: UriTooLong.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class UnsupportedMediaTypeOps[F[_], G[_]](val status: UnsupportedMediaType.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class RangeNotSatisfiableOps[F[_], G[_]](val status: RangeNotSatisfiable.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class ExpectationFailedOps[F[_], G[_]](val status: ExpectationFailed.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class MisdirectedRequestOps[F[_], G[_]](val status: MisdirectedRequest.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class UnprocessableEntityOps[F[_], G[_]](val status: UnprocessableEntity.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class LockedOps[F[_], G[_]](val status: Locked.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class FailedDependencyOps[F[_], G[_]](val status: FailedDependency.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class TooEarlyOps[F[_], G[_]](val status: TooEarly.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  // TODO Mandatory upgrade field
  final class UpgradeRequiredOps[F[_], G[_]](val status: UpgradeRequired.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class PreconditionRequiredOps[F[_], G[_]](val status: PreconditionRequired.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class TooManyRequestsOps[F[_], G[_]](val status: TooManyRequests.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class RequestHeaderFieldsTooLargeOps[F[_], G[_]](
      val status: RequestHeaderFieldsTooLarge.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class UnavailableForLegalReasonsOps[F[_], G[_]](val status: UnavailableForLegalReasons.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]

  final class InternalServerErrorOps[F[_], G[_]](val status: InternalServerError.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class NotImplementedOps[F[_], G[_]](val status: NotImplemented.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class BadGatewayOps[F[_], G[_]](val status: BadGateway.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class ServiceUnavailableOps[F[_], G[_]](val status: ServiceUnavailable.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class GatewayTimeoutOps[F[_], G[_]](val status: GatewayTimeout.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class HttpVersionNotSupportedOps[F[_], G[_]](val status: HttpVersionNotSupported.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class VariantAlsoNegotiatesOps[F[_], G[_]](val status: VariantAlsoNegotiates.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class InsufficientStorageOps[F[_], G[_]](val status: InsufficientStorage.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class LoopDetectedOps[F[_], G[_]](val status: LoopDetected.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class NotExtendedOps[F[_], G[_]](val status: NotExtended.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]
  final class NetworkAuthenticationRequiredOps[F[_], G[_]](
      val status: NetworkAuthenticationRequired.type)
      extends AnyVal
      with EntityResponseGenerator[F, G]

}
