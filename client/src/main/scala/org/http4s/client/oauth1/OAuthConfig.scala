package org.http4s.client.oauth1

import org.http4s.client.oauth1.Header.{Callback, Verifier}
import org.http4s.client.oauth1.{Header => OAuthHeader}

case class OAuthConfig[F[_]](
    consumer: OAuthHeader.Consumer,
    token: Option[OAuthHeader.Token],
    realm: Option[OAuthHeader.Realm],
    signatureMethod: OAuthHeader.SignatureMethod = Header.SignatureMethod(),
    timestampGenerator: F[OAuthHeader.Timestamp],
    version: Header.Version = OAuthHeader.Version(),
    nonceGenerator: F[OAuthHeader.Nonce],
    verifier: Option[Verifier] = None,
    callback: Option[Callback] = None)
