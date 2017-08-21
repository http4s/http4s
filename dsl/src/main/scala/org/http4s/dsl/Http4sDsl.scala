package org.http4s.dsl

import cats.Applicative
import org.http4s._
import org.http4s.dsl.impl._
import org.http4s.headers.`Content-Length`

trait Http4sDsl[F[_]] extends Http4s with Methods with Statuses {
  import Http4sDsl._

  implicit def http4sContinueSyntax(status: Continue.type): ContinueOps[F] = new ContinueOps[F](status)
  implicit def http4sSwitchingProtocolsSyntax(status: SwitchingProtocols.type): SwitchingProtocolsOps[F] = new SwitchingProtocolsOps[F](status)
  implicit def http4sOkSyntax(status: Ok.type): OkOps[F] = new OkOps[F](status)

  implicit def http4sCreatedSyntax(status: Created.type): CreatedOps[F] = new CreatedOps[F](status)
  implicit def http4sAcceptedSyntax(status: Accepted.type): AcceptedOps[F] = new AcceptedOps[F](status)
  implicit def http4sNonAuthoritativeInformationSyntax(status: NonAuthoritativeInformation.type): NonAuthoritativeInformationOps[F] = new NonAuthoritativeInformationOps[F](status)
  implicit def http4sNoContentSyntax(status: NoContent.type): NoContentOps[F] = new NoContentOps[F](status)
  implicit def http4sResetContentSyntax(status: ResetContent.type): ResetContentOps[F] = new ResetContentOps[F](status)
  implicit def http4sPartialContentSyntax(status: PartialContent.type): PartialContentOps[F] = new PartialContentOps[F](status)
  implicit def http4sMultiStatusSyntax(status: Status.MultiStatus.type): MultiStatusOps[F] = new MultiStatusOps[F](status)
  implicit def http4sAlreadyReportedSyntax(status: AlreadyReported.type): AlreadyReportedOps[F] = new AlreadyReportedOps[F](status)
  implicit def http4sIMUsedSyntax(status: IMUsed.type): IMUsedOps[F] = new IMUsedOps[F](status)

  implicit def http4sMultipleChoicesSyntax(status: MultipleChoices.type): MultipleChoicesOps[F] = new MultipleChoicesOps[F](status)
  implicit def http4sMovedPermanentlySyntax(status: MovedPermanently.type): MovedPermanentlyOps[F] = new MovedPermanentlyOps[F](status)
  implicit def http4sFoundSyntax(status: Found.type): FoundOps[F] = new FoundOps[F](status)
  implicit def http4sSeeOtherSyntax(status: SeeOther.type): SeeOtherOps[F] = new SeeOtherOps[F](status)
  implicit def http4sNotModifiedSyntax(status: NotModified.type): NotModifiedOps[F] = new NotModifiedOps[F](status)
  implicit def http4sTemporaryRedirectSyntax(status: TemporaryRedirect.type): TemporaryRedirectOps[F] = new TemporaryRedirectOps[F](status)
  implicit def http4sPermanentRedirectSyntax(status: PermanentRedirect.type): PermanentRedirectOps[F] = new PermanentRedirectOps[F](status)

  implicit def http4sBadRequestSyntax(status: BadRequest.type): BadRequestOps[F] = new BadRequestOps[F](status)
  implicit def http4sUnauthorizedSyntax(status: Unauthorized.type): UnauthorizedOps[F] = new UnauthorizedOps[F](status)
  implicit def http4sPaymentRequiredSyntax(status: PaymentRequired.type): PaymentRequiredOps[F] = new PaymentRequiredOps[F](status)
  implicit def http4sForbiddenSyntax(status: Forbidden.type): ForbiddenOps[F] = new ForbiddenOps[F](status)
  implicit def http4sNotFoundSyntax(status: NotFound.type): NotFoundOps[F] = new NotFoundOps[F](status)
  implicit def http4sMethodNotAllowedSyntax(status: MethodNotAllowed.type): MethodNotAllowedOps[F] = new MethodNotAllowedOps[F](status)
  implicit def http4sNotAcceptableSyntax(status: NotAcceptable.type): NotAcceptableOps[F] = new NotAcceptableOps[F](status)
  implicit def http4sProxyAuthenticationRequiredSyntax(status: ProxyAuthenticationRequired.type): ProxyAuthenticationRequiredOps[F] = new ProxyAuthenticationRequiredOps[F](status)
  implicit def http4sRequestTimeoutSyntax(status: RequestTimeout.type): RequestTimeoutOps[F] = new RequestTimeoutOps[F](status)
  implicit def http4sConflictSyntax(status: Conflict.type): ConflictOps[F] = new ConflictOps[F](status)
  implicit def http4sGoneSyntax(status: Gone.type): GoneOps[F] = new GoneOps[F](status)
  implicit def http4sLengthRequiredSyntax(status: LengthRequired.type): LengthRequiredOps[F] = new LengthRequiredOps[F](status)
  implicit def http4sPreconditionFailedSyntax(status: PreconditionFailed.type): PreconditionFailedOps[F] = new PreconditionFailedOps[F](status)
  implicit def http4sPayloadTooLargeSyntax(status: PayloadTooLarge.type): PayloadTooLargeOps[F] = new PayloadTooLargeOps[F](status)
  implicit def http4sUriTooLongSyntax(status: UriTooLong.type): UriTooLongOps[F] = new UriTooLongOps[F](status)
  implicit def http4sUnsupportedMediaTypeSyntax(status: UnsupportedMediaType.type): UnsupportedMediaTypeOps[F] = new UnsupportedMediaTypeOps[F](status)
  implicit def http4sRangeNotSatisfiableSyntax(status: RangeNotSatisfiable.type): RangeNotSatisfiableOps[F] = new RangeNotSatisfiableOps[F](status)
  implicit def http4sExpectationFailedSyntax(status: ExpectationFailed.type): ExpectationFailedOps[F] = new ExpectationFailedOps[F](status)
  implicit def http4sUnprocessableEntitySyntax(status: UnprocessableEntity.type): UnprocessableEntityOps[F] = new UnprocessableEntityOps[F](status)
  implicit def http4sLockedSyntax(status: Locked.type): LockedOps[F] = new LockedOps[F](status)
  implicit def http4sFailedDependencySyntax(status: FailedDependency.type): FailedDependencyOps[F] = new FailedDependencyOps[F](status)
  implicit def http4sUpgradeRequiredSyntax(status: UpgradeRequired.type): UpgradeRequiredOps[F] = new UpgradeRequiredOps[F](status)
  implicit def http4sPreconditionRequiredSyntax(status: PreconditionRequired.type): PreconditionRequiredOps[F] = new PreconditionRequiredOps[F](status)
  implicit def http4sTooManyRequestsSyntax(status: TooManyRequests.type): TooManyRequestsOps[F] = new TooManyRequestsOps[F](status)
  implicit def http4sRequestHeaderFieldsTooLargeSyntax(status: RequestHeaderFieldsTooLarge.type): RequestHeaderFieldsTooLargeOps[F] = new RequestHeaderFieldsTooLargeOps[F](status)
  implicit def http4sUnavailableForLegalReasonsSyntax(status: UnavailableForLegalReasons.type): UnavailableForLegalReasonsOps[F] = new UnavailableForLegalReasonsOps[F](status)

  implicit def http4sInternalServerErrorSyntax(status: InternalServerError.type): InternalServerErrorOps[F] = new InternalServerErrorOps[F](status)
  implicit def http4sNotImplementedSyntax(status: NotImplemented.type): NotImplementedOps[F] = new NotImplementedOps[F](status)
  implicit def http4sBadGatewaySyntax(status: BadGateway.type): BadGatewayOps[F] = new BadGatewayOps[F](status)
  implicit def http4sServiceUnavailableSyntax(status: ServiceUnavailable.type): ServiceUnavailableOps[F] = new ServiceUnavailableOps[F](status)
  implicit def http4sGatewayTimeoutSyntax(status: GatewayTimeout.type): GatewayTimeoutOps[F] = new GatewayTimeoutOps[F](status)
  implicit def http4sHttpVersionNotSupportedSyntax(status: HttpVersionNotSupported.type): HttpVersionNotSupportedOps[F] = new HttpVersionNotSupportedOps[F](status)
  implicit def http4sVariantAlsoNegotiatesSyntax(status: VariantAlsoNegotiates.type): VariantAlsoNegotiatesOps[F] = new VariantAlsoNegotiatesOps[F](status)
  implicit def http4sInsufficientStorageSyntax(status: InsufficientStorage.type): InsufficientStorageOps[F] = new InsufficientStorageOps[F](status)
  implicit def http4sLoopDetectedSyntax(status: LoopDetected.type): LoopDetectedOps[F] = new LoopDetectedOps[F](status)
  implicit def http4sNotExtendedSyntax(status: NotExtended.type): NotExtendedOps[F] = new NotExtendedOps[F](status)
  implicit def http4sNetworkAuthenticationRequiredSyntax(status: NetworkAuthenticationRequired.type): NetworkAuthenticationRequiredOps[F] = new NetworkAuthenticationRequiredOps[F](status)
}

object Http4sDsl {
  final class ContinueOps[F[_]](val status: Continue.type) extends AnyVal with EmptyResponseGenerator[F]
  // TODO support Upgrade header
  final class SwitchingProtocolsOps[F[_]](val status: SwitchingProtocols.type) extends AnyVal with EmptyResponseGenerator[F]
  final class OkOps[F[_]](val status: Ok.type) extends AnyVal with EntityResponseGenerator[F]

  final class CreatedOps[F[_]](val status: Created.type) extends AnyVal with EntityResponseGenerator[F]
  final class AcceptedOps[F[_]](val status: Accepted.type) extends AnyVal with EntityResponseGenerator[F]
  final class NonAuthoritativeInformationOps[F[_]](val status: NonAuthoritativeInformation.type) extends AnyVal with EntityResponseGenerator[F]
  final class NoContentOps[F[_]](val status: NoContent.type) extends AnyVal with EmptyResponseGenerator[F]
  final class ResetContentOps[F[_]](val status: ResetContent.type) extends AnyVal with EmptyResponseGenerator[F] {
    override def apply()(implicit F: Applicative[F]): F[Response[F]] =
      F.pure(Response(ResetContent, headers = Headers(`Content-Length`.zero)))
  }
  // TODO helpers for Content-Range and multipart/byteranges
  final class PartialContentOps[F[_]](val status: PartialContent.type) extends AnyVal with EntityResponseGenerator[F]
  final class MultiStatusOps[F[_]](val status: Status.MultiStatus.type) extends AnyVal with EntityResponseGenerator[F]
  final class AlreadyReportedOps[F[_]](val status: AlreadyReported.type) extends AnyVal with EntityResponseGenerator[F]
  final class IMUsedOps[F[_]](val status: IMUsed.type) extends AnyVal with EntityResponseGenerator[F]

  final class MultipleChoicesOps[F[_]](val status: MultipleChoices.type) extends AnyVal with LocationResponseGenerator[F]
  final class MovedPermanentlyOps[F[_]](val status: MovedPermanently.type) extends AnyVal with LocationResponseGenerator[F]
  final class FoundOps[F[_]](val status: Found.type) extends AnyVal with LocationResponseGenerator[F]
  final class SeeOtherOps[F[_]](val status: SeeOther.type) extends AnyVal with LocationResponseGenerator[F]
  final class NotModifiedOps[F[_]](val status: NotModified.type) extends AnyVal with EmptyResponseGenerator[F]
  // Note: UseProxy is deprecated in RFC7231, so we will not ease its creation here.
  final class TemporaryRedirectOps[F[_]](val status: TemporaryRedirect.type) extends AnyVal with LocationResponseGenerator[F]
  final class PermanentRedirectOps[F[_]](val status: PermanentRedirect.type) extends AnyVal with LocationResponseGenerator[F]

  final class BadRequestOps[F[_]](val status: BadRequest.type) extends AnyVal with EntityResponseGenerator[F]
  final class UnauthorizedOps[F[_]](val status: Unauthorized.type) extends AnyVal with WwwAuthenticateResponseGenerator[F]
  final class PaymentRequiredOps[F[_]](val status: PaymentRequired.type) extends AnyVal with EntityResponseGenerator[F]
  final class ForbiddenOps[F[_]](val status: Forbidden.type) extends AnyVal with EntityResponseGenerator[F]
  final class NotFoundOps[F[_]](val status: NotFound.type) extends AnyVal with EntityResponseGenerator[F]
  final class MethodNotAllowedOps[F[_]](val status: MethodNotAllowed.type) extends AnyVal with EntityResponseGenerator[F]
  final class NotAcceptableOps[F[_]](val status: NotAcceptable.type) extends AnyVal with EntityResponseGenerator[F]
  final class ProxyAuthenticationRequiredOps[F[_]](val status: ProxyAuthenticationRequired.type) extends AnyVal with EntityResponseGenerator[F]
  // TODO send Connection: close?
  final class RequestTimeoutOps[F[_]](val status: RequestTimeout.type) extends AnyVal with EntityResponseGenerator[F]
  final class ConflictOps[F[_]](val status: Conflict.type) extends AnyVal with EntityResponseGenerator[F]
  final class GoneOps[F[_]](val status: Gone.type) extends AnyVal with EntityResponseGenerator[F]
  final class LengthRequiredOps[F[_]](val status: LengthRequired.type) extends AnyVal with EntityResponseGenerator[F]
  final class PreconditionFailedOps[F[_]](val status: PreconditionFailed.type) extends AnyVal with EntityResponseGenerator[F]
  final class PayloadTooLargeOps[F[_]](val status: PayloadTooLarge.type) extends AnyVal with EntityResponseGenerator[F]
  final class UriTooLongOps[F[_]](val status: UriTooLong.type) extends AnyVal with EntityResponseGenerator[F]
  final class UnsupportedMediaTypeOps[F[_]](val status: UnsupportedMediaType.type) extends AnyVal with EntityResponseGenerator[F]
  final class RangeNotSatisfiableOps[F[_]](val status: RangeNotSatisfiable.type) extends AnyVal with EntityResponseGenerator[F]
  final class ExpectationFailedOps[F[_]](val status: ExpectationFailed.type) extends AnyVal with EntityResponseGenerator[F]
  final class UnprocessableEntityOps[F[_]](val status: UnprocessableEntity.type) extends AnyVal with EntityResponseGenerator[F]
  final class LockedOps[F[_]](val status: Locked.type) extends AnyVal with EntityResponseGenerator[F]
  final class FailedDependencyOps[F[_]](val status: FailedDependency.type) extends AnyVal with EntityResponseGenerator[F]
  // TODO Mandatory upgrade field
  final class UpgradeRequiredOps[F[_]](val status: UpgradeRequired.type) extends AnyVal with EntityResponseGenerator[F]
  final class PreconditionRequiredOps[F[_]](val status: PreconditionRequired.type) extends AnyVal with EntityResponseGenerator[F]
  final class TooManyRequestsOps[F[_]](val status: TooManyRequests.type) extends AnyVal with EntityResponseGenerator[F]
  final class RequestHeaderFieldsTooLargeOps[F[_]](val status: RequestHeaderFieldsTooLarge.type) extends AnyVal with EntityResponseGenerator[F]
  final class UnavailableForLegalReasonsOps[F[_]](val status: UnavailableForLegalReasons.type) extends AnyVal with EntityResponseGenerator[F]

  final class InternalServerErrorOps[F[_]](val status: InternalServerError.type) extends AnyVal with EntityResponseGenerator[F]
  final class NotImplementedOps[F[_]](val status: NotImplemented.type) extends AnyVal with EntityResponseGenerator[F]
  final class BadGatewayOps[F[_]](val status: BadGateway.type) extends AnyVal with EntityResponseGenerator[F]
  final class ServiceUnavailableOps[F[_]](val status: ServiceUnavailable.type) extends AnyVal with EntityResponseGenerator[F]
  final class GatewayTimeoutOps[F[_]](val status: GatewayTimeout.type) extends AnyVal with EntityResponseGenerator[F]
  final class HttpVersionNotSupportedOps[F[_]](val status: HttpVersionNotSupported.type) extends AnyVal with EntityResponseGenerator[F]
  final class VariantAlsoNegotiatesOps[F[_]](val status: VariantAlsoNegotiates.type) extends AnyVal with EntityResponseGenerator[F]
  final class InsufficientStorageOps[F[_]](val status: InsufficientStorage.type) extends AnyVal with EntityResponseGenerator[F]
  final class LoopDetectedOps[F[_]](val status: LoopDetected.type) extends AnyVal with EntityResponseGenerator[F]
  final class NotExtendedOps[F[_]](val status: NotExtended.type) extends AnyVal with EntityResponseGenerator[F]
  final class NetworkAuthenticationRequiredOps[F[_]](val status: NetworkAuthenticationRequired.type) extends AnyVal with EntityResponseGenerator[F]

  implicit class MethodOps(val method: Method) extends AnyVal {
    def | (another: Method) = new MethodConcat(Set(method, another))
  }

  implicit class MethodConcatOps(val methods: MethodConcat) extends AnyVal {
    def | (another: Method) = new MethodConcat(methods.methods + another)
  }
}
