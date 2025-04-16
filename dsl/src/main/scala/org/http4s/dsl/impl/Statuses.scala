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

package org.http4s.dsl.impl

import org.http4s.Status

trait Statuses {
  val Continue: Status.Continue.type = Status.Continue
  val SwitchingProtocols: Status.SwitchingProtocols.type = Status.SwitchingProtocols
  val Processing: Status.Processing.type = Status.Processing

  val Ok: Status.Ok.type = Status.Ok
  val Created: Status.Created.type = Status.Created
  val Accepted: Status.Accepted.type = Status.Accepted
  val NonAuthoritativeInformation: Status.NonAuthoritativeInformation.type =
    Status.NonAuthoritativeInformation
  val NoContent: Status.NoContent.type = Status.NoContent
  val ResetContent: Status.ResetContent.type = Status.ResetContent
  val PartialContent: Status.PartialContent.type = Status.PartialContent
  val MultiStatus: Status.MultiStatus.type = Status.MultiStatus
  val AlreadyReported: Status.AlreadyReported.type = Status.AlreadyReported
  val IMUsed: Status.IMUsed.type = Status.IMUsed

  val MultipleChoices: Status.MultipleChoices.type = Status.MultipleChoices
  val MovedPermanently: Status.MovedPermanently.type = Status.MovedPermanently
  val Found: Status.Found.type = Status.Found
  val SeeOther: Status.SeeOther.type = Status.SeeOther
  val NotModified: Status.NotModified.type = Status.NotModified
  val UseProxy: Status.UseProxy.type = Status.UseProxy
  val TemporaryRedirect: Status.TemporaryRedirect.type = Status.TemporaryRedirect
  val PermanentRedirect: Status.PermanentRedirect.type = Status.PermanentRedirect

  val BadRequest: Status.BadRequest.type = Status.BadRequest
  val Unauthorized: Status.Unauthorized.type = Status.Unauthorized
  val PaymentRequired: Status.PaymentRequired.type = Status.PaymentRequired
  val Forbidden: Status.Forbidden.type = Status.Forbidden
  val NotFound: Status.NotFound.type = Status.NotFound
  val MethodNotAllowed: Status.MethodNotAllowed.type = Status.MethodNotAllowed
  val NotAcceptable: Status.NotAcceptable.type = Status.NotAcceptable
  val ProxyAuthenticationRequired: Status.ProxyAuthenticationRequired.type =
    Status.ProxyAuthenticationRequired
  val RequestTimeout: Status.RequestTimeout.type = Status.RequestTimeout
  val Conflict: Status.Conflict.type = Status.Conflict
  val Gone: Status.Gone.type = Status.Gone
  val LengthRequired: Status.LengthRequired.type = Status.LengthRequired
  val PreconditionFailed: Status.PreconditionFailed.type = Status.PreconditionFailed
  val PayloadTooLarge: Status.PayloadTooLarge.type = Status.PayloadTooLarge
  val UriTooLong: Status.UriTooLong.type = Status.UriTooLong
  val UnsupportedMediaType: Status.UnsupportedMediaType.type = Status.UnsupportedMediaType
  val RangeNotSatisfiable: Status.RangeNotSatisfiable.type = Status.RangeNotSatisfiable
  val ExpectationFailed: Status.ExpectationFailed.type = Status.ExpectationFailed
  val UnprocessableContent: Status.UnprocessableContent.type = Status.UnprocessableContent
  @deprecated("now called UnprocessableContent", since = "0.23.31")
  val UnprocessableEntity: Status.UnprocessableEntity.type = Status.UnprocessableEntity
  val Locked: Status.Locked.type = Status.Locked
  val FailedDependency: Status.FailedDependency.type = Status.FailedDependency
  val UpgradeRequired: Status.UpgradeRequired.type = Status.UpgradeRequired
  val PreconditionRequired: Status.PreconditionRequired.type = Status.PreconditionRequired
  val TooManyRequests: Status.TooManyRequests.type = Status.TooManyRequests
  val RequestHeaderFieldsTooLarge: Status.RequestHeaderFieldsTooLarge.type =
    Status.RequestHeaderFieldsTooLarge
  val UnavailableForLegalReasons: Status.UnavailableForLegalReasons.type =
    Status.UnavailableForLegalReasons

  val InternalServerError: Status.InternalServerError.type = Status.InternalServerError
  val NotImplemented: Status.NotImplemented.type = Status.NotImplemented
  val BadGateway: Status.BadGateway.type = Status.BadGateway
  val ServiceUnavailable: Status.ServiceUnavailable.type = Status.ServiceUnavailable
  val GatewayTimeout: Status.GatewayTimeout.type = Status.GatewayTimeout
  val HttpVersionNotSupported: Status.HttpVersionNotSupported.type = Status.HttpVersionNotSupported
  val VariantAlsoNegotiates: Status.VariantAlsoNegotiates.type = Status.VariantAlsoNegotiates
  val InsufficientStorage: Status.InsufficientStorage.type = Status.InsufficientStorage
  val LoopDetected: Status.LoopDetected.type = Status.LoopDetected
  val NotExtended: Status.NotExtended.type = Status.NotExtended
  val NetworkAuthenticationRequired: Status.NetworkAuthenticationRequired.type =
    Status.NetworkAuthenticationRequired
}
