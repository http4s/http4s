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
package dsl
package impl

import cats.Applicative
import cats.~>
import org.http4s.Status._
import org.http4s.headers.`Content-Length`

trait Responses[F[_], G[_]] {
  import Responses._

  def liftG: G ~> F

  implicit def http4sContinueSyntax(status: Continue.type): ContinueOps[F, G] =
    new ContinueOps[F, G](status)
  implicit def http4sSwitchingProtocolsSyntax(
      status: SwitchingProtocols.type
  ): SwitchingProtocolsOps[F, G] =
    new SwitchingProtocolsOps[F, G](status)
  implicit def http4sEarlyHintsSyntax(status: EarlyHints.type): EarlyHintsOps[F, G] =
    new EarlyHintsOps[F, G](status)
  implicit def http4sOkSyntax(status: Ok.type): OkOps[F, G] = new OkOps[F, G](status, liftG)

  implicit def http4sCreatedSyntax(status: Created.type): CreatedOps[F, G] =
    new CreatedOps[F, G](status, liftG)
  implicit def http4sAcceptedSyntax(status: Accepted.type): AcceptedOps[F, G] =
    new AcceptedOps[F, G](status, liftG)
  implicit def http4sNonAuthoritativeInformationSyntax(
      status: NonAuthoritativeInformation.type
  ): NonAuthoritativeInformationOps[F, G] =
    new NonAuthoritativeInformationOps[F, G](status, liftG)
  implicit def http4sNoContentSyntax(status: NoContent.type): NoContentOps[F, G] =
    new NoContentOps[F, G](status)
  implicit def http4sResetContentSyntax(status: ResetContent.type): ResetContentOps[F, G] =
    new ResetContentOps[F, G](status)
  implicit def http4sPartialContentSyntax(status: PartialContent.type): PartialContentOps[F, G] =
    new PartialContentOps[F, G](status, liftG)
  implicit def http4sMultiStatusSyntax(status: Status.MultiStatus.type): MultiStatusOps[F, G] =
    new MultiStatusOps[F, G](status, liftG)
  implicit def http4sAlreadyReportedSyntax(status: AlreadyReported.type): AlreadyReportedOps[F, G] =
    new AlreadyReportedOps[F, G](status, liftG)
  implicit def http4sIMUsedSyntax(status: IMUsed.type): IMUsedOps[F, G] =
    new IMUsedOps[F, G](status, liftG)

  implicit def http4sMultipleChoicesSyntax(status: MultipleChoices.type): MultipleChoicesOps[F, G] =
    new MultipleChoicesOps[F, G](status, liftG)
  implicit def http4sMovedPermanentlySyntax(
      status: MovedPermanently.type
  ): MovedPermanentlyOps[F, G] =
    new MovedPermanentlyOps[F, G](status, liftG)
  implicit def http4sFoundSyntax(status: Found.type): FoundOps[F, G] =
    new FoundOps[F, G](status, liftG)
  implicit def http4sSeeOtherSyntax(status: SeeOther.type): SeeOtherOps[F, G] =
    new SeeOtherOps[F, G](status, liftG)
  implicit def http4sNotModifiedSyntax(status: NotModified.type): NotModifiedOps[F, G] =
    new NotModifiedOps[F, G](status)
  implicit def http4sTemporaryRedirectSyntax(
      status: TemporaryRedirect.type
  ): TemporaryRedirectOps[F, G] =
    new TemporaryRedirectOps[F, G](status, liftG)
  implicit def http4sPermanentRedirectSyntax(
      status: PermanentRedirect.type
  ): PermanentRedirectOps[F, G] =
    new PermanentRedirectOps[F, G](status, liftG)

  implicit def http4sBadRequestSyntax(status: BadRequest.type): BadRequestOps[F, G] =
    new BadRequestOps[F, G](status, liftG)
  implicit def http4sUnauthorizedSyntax(status: Unauthorized.type): UnauthorizedOps[F, G] =
    new UnauthorizedOps[F, G](status)
  implicit def http4sPaymentRequiredSyntax(status: PaymentRequired.type): PaymentRequiredOps[F, G] =
    new PaymentRequiredOps[F, G](status, liftG)
  implicit def http4sForbiddenSyntax(status: Forbidden.type): ForbiddenOps[F, G] =
    new ForbiddenOps[F, G](status, liftG)
  implicit def http4sNotFoundSyntax(status: NotFound.type): NotFoundOps[F, G] =
    new NotFoundOps[F, G](status, liftG)
  implicit def http4sMethodNotAllowedSyntax(
      status: MethodNotAllowed.type
  ): MethodNotAllowedOps[F, G] =
    new MethodNotAllowedOps[F, G](status)
  implicit def http4sNotAcceptableSyntax(status: NotAcceptable.type): NotAcceptableOps[F, G] =
    new NotAcceptableOps[F, G](status, liftG)
  implicit def http4sProxyAuthenticationRequiredSyntax(
      status: ProxyAuthenticationRequired.type
  ): ProxyAuthenticationRequiredOps[F, G] =
    new ProxyAuthenticationRequiredOps[F, G](status, liftG)
  implicit def http4sRequestTimeoutSyntax(status: RequestTimeout.type): RequestTimeoutOps[F, G] =
    new RequestTimeoutOps[F, G](status, liftG)
  implicit def http4sConflictSyntax(status: Conflict.type): ConflictOps[F, G] =
    new ConflictOps[F, G](status, liftG)
  implicit def http4sGoneSyntax(status: Gone.type): GoneOps[F, G] = new GoneOps[F, G](status, liftG)
  implicit def http4sLengthRequiredSyntax(status: LengthRequired.type): LengthRequiredOps[F, G] =
    new LengthRequiredOps[F, G](status, liftG)
  implicit def http4sPreconditionFailedSyntax(
      status: PreconditionFailed.type
  ): PreconditionFailedOps[F, G] =
    new PreconditionFailedOps[F, G](status, liftG)
  implicit def http4sPayloadTooLargeSyntax(status: PayloadTooLarge.type): PayloadTooLargeOps[F, G] =
    new PayloadTooLargeOps[F, G](status, liftG)
  implicit def http4sUriTooLongSyntax(status: UriTooLong.type): UriTooLongOps[F, G] =
    new UriTooLongOps[F, G](status, liftG)
  implicit def http4sUnsupportedMediaTypeSyntax(
      status: UnsupportedMediaType.type
  ): UnsupportedMediaTypeOps[F, G] =
    new UnsupportedMediaTypeOps[F, G](status, liftG)
  implicit def http4sRangeNotSatisfiableSyntax(
      status: RangeNotSatisfiable.type
  ): RangeNotSatisfiableOps[F, G] =
    new RangeNotSatisfiableOps[F, G](status, liftG)
  implicit def http4sExpectationFailedSyntax(
      status: ExpectationFailed.type
  ): ExpectationFailedOps[F, G] =
    new ExpectationFailedOps[F, G](status, liftG)
  implicit def http4sMisdirectedRequestSyntax(
      status: MisdirectedRequest.type
  ): MisdirectedRequestOps[F, G] =
    new MisdirectedRequestOps[F, G](status, liftG)
  implicit def http4sUnprocessableEntitySyntax(
      status: UnprocessableEntity.type
  ): UnprocessableEntityOps[F, G] =
    new UnprocessableEntityOps[F, G](status, liftG)
  implicit def http4sLockedSyntax(status: Locked.type): LockedOps[F, G] =
    new LockedOps[F, G](status, liftG)
  implicit def http4sFailedDependencySyntax(
      status: FailedDependency.type
  ): FailedDependencyOps[F, G] =
    new FailedDependencyOps[F, G](status, liftG)
  implicit def http4sTooEarlySyntax(status: TooEarly.type): TooEarlyOps[F, G] =
    new TooEarlyOps[F, G](status, liftG)
  implicit def http4sUpgradeRequiredSyntax(status: UpgradeRequired.type): UpgradeRequiredOps[F, G] =
    new UpgradeRequiredOps[F, G](status, liftG)
  implicit def http4sPreconditionRequiredSyntax(
      status: PreconditionRequired.type
  ): PreconditionRequiredOps[F, G] =
    new PreconditionRequiredOps[F, G](status, liftG)
  implicit def http4sTooManyRequestsSyntax(status: TooManyRequests.type): TooManyRequestsOps[F, G] =
    new TooManyRequestsOps[F, G](status, liftG)
  implicit def http4sRequestHeaderFieldsTooLargeSyntax(
      status: RequestHeaderFieldsTooLarge.type
  ): RequestHeaderFieldsTooLargeOps[F, G] =
    new RequestHeaderFieldsTooLargeOps[F, G](status, liftG)
  implicit def http4sUnavailableForLegalReasonsSyntax(
      status: UnavailableForLegalReasons.type
  ): UnavailableForLegalReasonsOps[F, G] =
    new UnavailableForLegalReasonsOps[F, G](status, liftG)

  implicit def http4sInternalServerErrorSyntax(
      status: InternalServerError.type
  ): InternalServerErrorOps[F, G] =
    new InternalServerErrorOps[F, G](status, liftG)
  implicit def http4sNotImplementedSyntax(status: NotImplemented.type): NotImplementedOps[F, G] =
    new NotImplementedOps[F, G](status, liftG)
  implicit def http4sBadGatewaySyntax(status: BadGateway.type): BadGatewayOps[F, G] =
    new BadGatewayOps[F, G](status, liftG)
  implicit def http4sServiceUnavailableSyntax(
      status: ServiceUnavailable.type
  ): ServiceUnavailableOps[F, G] =
    new ServiceUnavailableOps[F, G](status, liftG)
  implicit def http4sGatewayTimeoutSyntax(status: GatewayTimeout.type): GatewayTimeoutOps[F, G] =
    new GatewayTimeoutOps[F, G](status, liftG)
  implicit def http4sHttpVersionNotSupportedSyntax(
      status: HttpVersionNotSupported.type
  ): HttpVersionNotSupportedOps[F, G] =
    new HttpVersionNotSupportedOps[F, G](status, liftG)
  implicit def http4sVariantAlsoNegotiatesSyntax(
      status: VariantAlsoNegotiates.type
  ): VariantAlsoNegotiatesOps[F, G] =
    new VariantAlsoNegotiatesOps[F, G](status, liftG)
  implicit def http4sInsufficientStorageSyntax(
      status: InsufficientStorage.type
  ): InsufficientStorageOps[F, G] =
    new InsufficientStorageOps[F, G](status, liftG)
  implicit def http4sLoopDetectedSyntax(status: LoopDetected.type): LoopDetectedOps[F, G] =
    new LoopDetectedOps[F, G](status, liftG)
  implicit def http4sNotExtendedSyntax(status: NotExtended.type): NotExtendedOps[F, G] =
    new NotExtendedOps[F, G](status, liftG)
  implicit def http4sNetworkAuthenticationRequiredSyntax(
      status: NetworkAuthenticationRequired.type
  ): NetworkAuthenticationRequiredOps[F, G] =
    new NetworkAuthenticationRequiredOps[F, G](status, liftG)
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
  final class OkOps[F[_], G[_]](val status: Ok.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]

  final class CreatedOps[F[_], G[_]](val status: Created.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class AcceptedOps[F[_], G[_]](val status: Accepted.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class NonAuthoritativeInformationOps[F[_], G[_]](
      val status: NonAuthoritativeInformation.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class NoContentOps[F[_], G[_]](val status: NoContent.type)
      extends AnyVal
      with EmptyResponseGenerator[F, G]
  final class ResetContentOps[F[_], G[_]](val status: ResetContent.type)
      extends AnyVal
      with EmptyResponseGenerator[F, G] {
    override def headers(header: Header.ToRaw, _headers: Header.ToRaw*)(implicit
        F: Applicative[F]
    ): F[Response[G]] =
      F.pure(
        Response(
          ResetContent,
          headers = Headers(`Content-Length`.zero) ++ Headers(header :: _headers.toList),
        )
      )

    override def apply()(implicit F: Applicative[F]): F[Response[G]] =
      F.pure(Response[G](ResetContent, headers = Headers(List(`Content-Length`.zero))))
  }
  // TODO helpers for Content-Range and multipart/byteranges
  final class PartialContentOps[F[_], G[_]](val status: PartialContent.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class MultiStatusOps[F[_], G[_]](val status: Status.MultiStatus.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class AlreadyReportedOps[F[_], G[_]](val status: AlreadyReported.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class IMUsedOps[F[_], G[_]](val status: IMUsed.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]

  final class MultipleChoicesOps[F[_], G[_]](val status: MultipleChoices.type, val liftG: G ~> F)
      extends LocationResponseGenerator[F, G]
  final class MovedPermanentlyOps[F[_], G[_]](val status: MovedPermanently.type, val liftG: G ~> F)
      extends LocationResponseGenerator[F, G]
  final class FoundOps[F[_], G[_]](val status: Found.type, val liftG: G ~> F)
      extends LocationResponseGenerator[F, G]
  final class SeeOtherOps[F[_], G[_]](val status: SeeOther.type, val liftG: G ~> F)
      extends LocationResponseGenerator[F, G]
  final class NotModifiedOps[F[_], G[_]](val status: NotModified.type)
      extends AnyVal
      with EmptyResponseGenerator[F, G]
  // Note: UseProxy is deprecated in RFC7231, so we will not ease its creation here.
  final class TemporaryRedirectOps[F[_], G[_]](
      val status: TemporaryRedirect.type,
      val liftG: G ~> F,
  ) extends LocationResponseGenerator[F, G]
  final class PermanentRedirectOps[F[_], G[_]](
      val status: PermanentRedirect.type,
      val liftG: G ~> F,
  ) extends LocationResponseGenerator[F, G]

  final class BadRequestOps[F[_], G[_]](val status: BadRequest.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class UnauthorizedOps[F[_], G[_]](val status: Unauthorized.type)
      extends AnyVal
      with WwwAuthenticateResponseGenerator[F, G]
  final class PaymentRequiredOps[F[_], G[_]](val status: PaymentRequired.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class ForbiddenOps[F[_], G[_]](val status: Forbidden.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class NotFoundOps[F[_], G[_]](val status: NotFound.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class MethodNotAllowedOps[F[_], G[_]](val status: MethodNotAllowed.type)
      extends AnyVal
      with AllowResponseGenerator[F, G]
  final class NotAcceptableOps[F[_], G[_]](val status: NotAcceptable.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class ProxyAuthenticationRequiredOps[F[_], G[_]](
      val status: ProxyAuthenticationRequired.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  // TODO send Connection: close?
  final class RequestTimeoutOps[F[_], G[_]](val status: RequestTimeout.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class ConflictOps[F[_], G[_]](val status: Conflict.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class GoneOps[F[_], G[_]](val status: Gone.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class LengthRequiredOps[F[_], G[_]](val status: LengthRequired.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class PreconditionFailedOps[F[_], G[_]](
      val status: PreconditionFailed.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class PayloadTooLargeOps[F[_], G[_]](val status: PayloadTooLarge.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class UriTooLongOps[F[_], G[_]](val status: UriTooLong.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class UnsupportedMediaTypeOps[F[_], G[_]](
      val status: UnsupportedMediaType.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class RangeNotSatisfiableOps[F[_], G[_]](
      val status: RangeNotSatisfiable.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class ExpectationFailedOps[F[_], G[_]](
      val status: ExpectationFailed.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class MisdirectedRequestOps[F[_], G[_]](
      val status: MisdirectedRequest.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class UnprocessableEntityOps[F[_], G[_]](
      val status: UnprocessableEntity.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class LockedOps[F[_], G[_]](val status: Locked.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class FailedDependencyOps[F[_], G[_]](val status: FailedDependency.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class TooEarlyOps[F[_], G[_]](val status: TooEarly.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  // TODO Mandatory upgrade field
  final class UpgradeRequiredOps[F[_], G[_]](val status: UpgradeRequired.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class PreconditionRequiredOps[F[_], G[_]](
      val status: PreconditionRequired.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class TooManyRequestsOps[F[_], G[_]](val status: TooManyRequests.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class RequestHeaderFieldsTooLargeOps[F[_], G[_]](
      val status: RequestHeaderFieldsTooLarge.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class UnavailableForLegalReasonsOps[F[_], G[_]](
      val status: UnavailableForLegalReasons.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]

  final class InternalServerErrorOps[F[_], G[_]](
      val status: InternalServerError.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class NotImplementedOps[F[_], G[_]](val status: NotImplemented.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class BadGatewayOps[F[_], G[_]](val status: BadGateway.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class ServiceUnavailableOps[F[_], G[_]](
      val status: ServiceUnavailable.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class GatewayTimeoutOps[F[_], G[_]](val status: GatewayTimeout.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class HttpVersionNotSupportedOps[F[_], G[_]](
      val status: HttpVersionNotSupported.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class VariantAlsoNegotiatesOps[F[_], G[_]](
      val status: VariantAlsoNegotiates.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class InsufficientStorageOps[F[_], G[_]](
      val status: InsufficientStorage.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
  final class LoopDetectedOps[F[_], G[_]](val status: LoopDetected.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class NotExtendedOps[F[_], G[_]](val status: NotExtended.type, val liftG: G ~> F)
      extends EntityResponseGenerator[F, G]
  final class NetworkAuthenticationRequiredOps[F[_], G[_]](
      val status: NetworkAuthenticationRequired.type,
      val liftG: G ~> F,
  ) extends EntityResponseGenerator[F, G]
}
