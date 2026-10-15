/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

final case class IgnoredCase(
    wireCase: WireCase,
    platforms: Set[String],
    observed: String,
) {
  def id: String = wireCase.id
}

/** The only source of ignores. Full comparison suites never consult this registry. */
object IgnoreRegistry {
  private val validPlatforms = Set("jvm", "js", "native")

  val all: Set[IgnoredCase] = Set(
    IgnoredCase(
      Http1FramingCases.h1ClientChunkExtensionBadWhitespace,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); ChunkedEncodingError rejects valid chunk-extension BWS",
    ),
    IgnoredCase(
      Http1LifecycleCases.h1ClientErrorResponseDuringUpload,
      Set("js", "jvm", "native"),
      "provisional observation limit (SHOULD; no deviation established); 8-second timeout after observed 32768-byte prefix and complete 413/Connection: close; stopping/closure unestablished",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ClientGeneratedAsteriskForm,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); public Ember URI/encoder binding sends OPTIONS /* instead of OPTIONS *",
    ),
    IgnoredCase(
      InteractionCases.h1ClientInteractionInterimNextRequestAssociation,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: 201; observed: 103",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ClientObsoleteFoldMultiple,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); InvalidHeaderWhitespace rejects response requiring obs-fold acceptance",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ClientObsoleteFoldSpace,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); InvalidHeaderWhitespace rejects response requiring obs-fold acceptance",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ClientObsoleteFoldTab,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); InvalidHeaderWhitespace rejects response requiring obs-fold acceptance",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ServerAbsoluteFormEmptyHost,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); an empty http URI host was accepted: Some(200)",
    ),
    IgnoredCase(
      Http1FramingCases.h1ServerChunkExtensionBadWhitespace,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: 200; observed: 500",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ServerDuplicateHostCaseInsensitive,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: 400; observed: 200",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ServerDuplicateHostDifferent,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: 400; observed: 200",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ServerDuplicateHostIdentical,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: 400; observed: 200",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ServerFieldNameDelimiter,
      Set("js", "jvm", "native"),
      "confirmed recommendation deviation (SHOULD; not MUST); expected: 400; observed: 200",
    ),
    IgnoredCase(
      Http1FramingCases.h1ServerHeadResponseHasNoContent,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); additional bytes followed a response requiring connection closure",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ServerHostComma,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: 400; observed: 200",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ServerHostNondigitPort,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: 400; observed: 200",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ServerHostPath,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: 400; observed: 200",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ServerHostSpace,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: 400; observed: 200",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ServerHostUnclosedIpv6Literal,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: 400; observed: 200",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ServerHostUserinfo,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: 400; observed: 200",
    ),
    IgnoredCase(
      Http1LifecycleCases.h1ServerHttp10CloseOverridesKeepAlive,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http1FramingCases.h1ServerHttp10ResponseHasNoTransferEncoding,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: Vector(); observed: Vector(chunked)",
    ),
    IgnoredCase(
      Http1SyntaxCases.h1ServerLeadingEmptyLine,
      Set("js", "jvm", "native"),
      "confirmed recommendation deviation (SHOULD; not MUST); expected: 200; observed: 400",
    ),
    IgnoredCase(
      BaselineCases.h1ServerMissingHost,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); expected: 400; observed: 200",
    ),
    IgnoredCase(
      Http1FramingCases.h1ServerNoContentResponseHasNoContent,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); additional bytes followed a response requiring connection closure",
    ),
    IgnoredCase(
      Http1FramingCases.h1ServerNotModifiedResponseHasNoContent,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); additional bytes followed a response requiring connection closure",
    ),
    IgnoredCase(
      Http1FramingCases.h1ServerUnsupportedTransferCoding,
      Set("js", "jvm", "native"),
      "confirmed recommendation deviation (SHOULD; not MUST); expected unsupported coding or length-required policy, observed 400",
    ),
    IgnoredCase(
      Http2StateCases.h2ClientConnectionCreditIndependent,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); selected run after completion-credit correction times out at 8 seconds; required control/progress witness remains incomplete",
    ),
    IgnoredCase(
      Http2StateCases.h2ClientConnectionWindowOverflow,
      Set("native"),
      "Scala 2.12.21: provisional observation limit (MUST; no deviation established); 8-second timeout after connection WINDOW_UPDATE overflow stimulus; only SETTINGS ACK observed, rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackEncoderMinimumBeforeRestore,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); invalid HPACK index after acknowledged encoder table reduction",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackEncoderReductionZero,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); invalid HPACK index after acknowledged encoder table reduction",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectHuffmanNamePaddingEight,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectHuffmanPaddingEight,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectHuffmanStringTruncated,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectIncrementalNameIntegerTruncated,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectIndexIntegerTruncated,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectNameLengthTruncated,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectNameTruncated,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectNeverNameIntegerTruncated,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectStringLengthTruncated,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectUpdateAfterIndex,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectUpdateAfterLiteral,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectValueMissing,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectValueTruncated,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      HpackCases.h2ClientHpackRejectWithoutNameIntegerTruncated,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); target client consumed malformed HPACK",
    ),
    IgnoredCase(
      InteractionCases.h2ClientInteractionEagerServerSettings,
      Set("native"),
      "Scala 2.12.21: confirmed mandatory deviation (MUST); client preface followed by SETTINGS ACK before initial non-ACK SETTINGS",
    ),
    IgnoredCase(
      InteractionCases.h2ClientInteractionEagerZeroWindowUpload,
      Set("native"),
      "confirmed mandatory deviation (MUST/MUST NOT); DATA consumed credit while the stream window was zero",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientMessageEmptyTrailers,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (syntax/MUST); COMPRESSION_ERROR rejects valid empty trailing HEADERS",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectConnectionField,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldNameColon,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldNameControlBoundary,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldNameDel,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldNameNul,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldNameObsTextHigh,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldNameObsTextLow,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldNameSpace,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldNameTab,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldNameUppercaseBoundary,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldValueCr,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldValueLeadingSpace,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldValueLeadingTab,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldValueLf,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldValueNul,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldValueTrailingSpace,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectFieldValueTrailingTab,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectHeadersPaddingEqualsPayload,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed)",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectHeadersPaddingExceedsPayload,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed)",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectHeadersStreamZero,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectInformationalEndStream,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectKeepAliveField,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2ControlCases.h2ClientRejectPrefaceFirstPing,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2ControlCases.h2ClientRejectPriorityStreamZero,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectProxyConnectionField,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectPseudoHeaderAfterRegularField,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectPushInvalidField,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectPushMissingMethod,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectPushRequestContent,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectPushStreamZero,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectPushUnauthoritativeOrigin,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectPushUnsafeMethod,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectRequestAuthorityPseudoHeader,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectRequestMethodPseudoHeader,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectRequestPathPseudoHeader,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectRequestSchemePseudoHeader,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectResponseTe,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2ControlCases.h2ClientRejectSettingsServerEnablePushOne,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectSwitchingProtocols,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST NOT); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectTrailerProhibitedField,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectTrailerPseudoHeader,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectTrailerUppercaseName,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectTrailersWithoutEndStream,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response; native: confirmed mandatory deviation (MUST/MUST NOT); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectTransferEncodingField,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectUndefinedPseudoHeader,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectUpgradeField,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientRejectUppercaseFieldName,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2StateCases.h2ClientSettingsStreamWindowOverflow,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2ControlCases.h2ClientSettingsWindowMaximum,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); FLOW_CONTROL_ERROR rejects legal INITIAL_WINDOW_SIZE=2147483647",
    ),
    IgnoredCase(
      Http2StateCases.h2ClientUnpromisedServerHeaders,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientValidateFieldEmptyName,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (conditional MUST); COMPRESSION_ERROR for detected HTTP field-name error in structurally valid HPACK",
    ),
    IgnoredCase(
      Http2MessageCases.h2ClientValidateFieldSeparatorName,
      Set("js", "jvm", "native"),
      "confirmed recommendation deviation (SHOULD; not MUST); the client consumed a deliberately malformed response",
    ),
    IgnoredCase(
      Http2StateCases.h2ServerConnectionCreditIndependent,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST NOT); DATA payload 16384 exceeds remaining connection credit 16383 before completion grants",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackEncoderMinimumBeforeRestore,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); invalid HPACK index after acknowledged encoder table reduction",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackEncoderReductionZero,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); invalid HPACK index after acknowledged encoder table reduction",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectHuffmanNamePaddingEight,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectHuffmanPaddingEight,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectHuffmanStringTruncated,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectIncrementalNameIntegerTruncated,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectIndexIntegerTruncated,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectNameLengthTruncated,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectNameTruncated,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectNeverNameIntegerTruncated,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectStringLengthTruncated,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectUpdateAfterIndex,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectUpdateAfterLiteral,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectValueMissing,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectValueTruncated,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      HpackCases.h2ServerHpackRejectWithoutNameIntegerTruncated,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerMessageEmptyTrailers,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (syntax/MUST); COMPRESSION_ERROR rejects valid empty trailing HEADERS",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectAuthorityUserinfo,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldNameColon,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldNameControlBoundary,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldNameDel,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldNameNul,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldNameObsTextHigh,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldNameObsTextLow,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldNameSpace,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldNameTab,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldValueCr,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldValueLeadingSpace,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldValueLeadingTab,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldValueLf,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldValueNul,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldValueTrailingSpace,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectFieldValueTrailingTab,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectHeadersPaddingEqualsPayload,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed)",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectHeadersPaddingExceedsPayload,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); COMPRESSION_ERROR for invalid padding requiring PROTOCOL_ERROR (generic INTERNAL_ERROR also allowed)",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectKeepAliveField,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectPathFragment,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2ControlCases.h2ServerRejectPrefaceFirstPing,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2ControlCases.h2ServerRejectPriorityStreamZero,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectProxyConnectionField,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectRelativePath,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectRepeatedAuthority,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectTrailerProhibitedField,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectTrailerPseudoHeader,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectTrailerUppercaseName,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectTrailersWithoutEndStream,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectTransferEncodingField,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerRejectUpgradeField,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2StateCases.h2ServerSettingsStreamWindowOverflow,
      Set("js", "jvm", "native"),
      "provisional observation limit (MUST; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
    IgnoredCase(
      Http2ControlCases.h2ServerSettingsWindowMaximum,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (MUST); FLOW_CONTROL_ERROR rejects legal INITIAL_WINDOW_SIZE=2147483647",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerValidateFieldEmptyName,
      Set("js", "jvm", "native"),
      "confirmed mandatory deviation (conditional MUST); COMPRESSION_ERROR for detected HTTP field-name error in structurally valid HPACK",
    ),
    IgnoredCase(
      Http2MessageCases.h2ServerValidateFieldSeparatorName,
      Set("js", "jvm", "native"),
      "provisional observation limit (SHOULD; no deviation established); 8-second timeout; required completion/rejection/closure unestablished",
    ),
  )

  val http1: Set[IgnoredCase] = all.filter(_.id.startsWith("h1."))
  val http2: Set[IgnoredCase] = all.filter(_.id.startsWith("h2."))

  def get(id: String, platform: String): Option[IgnoredCase] =
    all.find(e => e.id == id && e.platforms(platform))

  def validate(cases: Vector[WireCase]): Vector[String] = {
    val ids = cases.map(_.id)
    val duplicateCases = ids
      .groupBy(identity)
      .collect {
        case (id, xs) if xs.size > 1 =>
          s"duplicate case: $id"
      }
      .toVector
    val duplicateIgnores = all
      .groupBy(_.id)
      .collect {
        case (id, xs) if xs.size > 1 =>
          s"duplicate ignore: $id"
      }
      .toVector
    duplicateCases ++ duplicateIgnores ++ all.toVector.sortBy(_.id).flatMap { e =>
      Vector(
        if (!e.id.startsWith("h1.") && !e.id.startsWith("h2."))
          Some(s"unknown ignore protocol: ${e.id}")
        else None,
        if (!cases.contains(e.wireCase)) Some(s"stale ignore: ${e.id}") else None,
        if (e.platforms.isEmpty || !e.platforms.subsetOf(validPlatforms))
          Some(s"invalid platforms: ${e.id}")
        else None,
        if (e.observed.trim.isEmpty)
          Some(s"incomplete ignore: ${e.id}")
        else None,
      ).flatten
    } ++ cases
      .collect {
        case c if c.references.isEmpty || c.references.exists(_.requirement.trim.isEmpty) =>
          s"missing specification reference: ${c.id}"
      }
  }
}
