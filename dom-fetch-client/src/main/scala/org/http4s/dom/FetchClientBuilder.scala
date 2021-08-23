/*
 * Copyright 2021 http4s.org
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

package org.http4s.dom

import cats.effect.Async
import cats.effect.Resource
import org.http4s.client.{Client, defaults}
import org.http4s.internal.BackendBuilder
import org.scalajs.dom.experimental.ReferrerPolicy
import org.scalajs.dom.experimental.RequestCache
import org.scalajs.dom.experimental.RequestCredentials
import org.scalajs.dom.experimental.RequestMode
import org.scalajs.dom.experimental.RequestRedirect
import scala.concurrent.duration._

/** Configure and obtain a FetchClient
  * @param requestTimeout maximum duration from the submission of a request through reading the body before a timeout.
  * @param cache how the request will interact with the browser’s HTTP cache
  * @param credentials what browsers do with credentials (cookies, HTTP authentication entries, and TLS client certificates)
  * @param integrity subresource integrity value of the request
  * @param keepAlive used to allow the request to outlive the page
  * @param mode mode you want to use for the request, e.g., cors, no-cors, or same-origin
  * @param redirect how to handle a redirect response
  * @param referrer referrer of the request, this can be a same-origin URL, about:client, or an empty string.
  * @param referrerPolicy referrer policy to use for the request
  */
sealed abstract class FetchClientBuilder[F[_]] private (
    val requestTimeout: Duration,
    val cache: Option[RequestCache],
    val credentials: Option[RequestCredentials],
    val integrity: Option[String],
    val keepAlive: Option[Boolean],
    val mode: Option[RequestMode],
    val redirect: Option[RequestRedirect],
    val referrer: Option[String],
    val referrerPolicy: Option[ReferrerPolicy]
)(implicit protected val F: Async[F])
    extends BackendBuilder[F, Client[F]] {

  private def copy(
      requestTimeout: Duration = requestTimeout,
      cache: Option[RequestCache] = cache,
      credentials: Option[RequestCredentials] = credentials,
      integrity: Option[String] = integrity,
      keepAlive: Option[Boolean] = keepAlive,
      mode: Option[RequestMode] = mode,
      redirect: Option[RequestRedirect] = redirect,
      referrer: Option[String] = referrer,
      referrerPolicy: Option[ReferrerPolicy] = referrerPolicy
  ): FetchClientBuilder[F] =
    new FetchClientBuilder[F](
      requestTimeout,
      cache,
      credentials,
      integrity,
      keepAlive,
      mode,
      redirect,
      referrer,
      referrerPolicy
    ) {}

  def withRequestTimeout(requestTimeout: Duration): FetchClientBuilder[F] =
    copy(requestTimeout = requestTimeout)

  def withCacheOption(cache: Option[RequestCache]): FetchClientBuilder[F] =
    copy(cache = cache)
  def withCache(cache: RequestCache): FetchClientBuilder[F] =
    withCacheOption(Some(cache))
  def withDefaultCache: FetchClientBuilder[F] =
    withCacheOption(None)

  def withCredentialsOption(credentials: Option[RequestCredentials]): FetchClientBuilder[F] =
    copy(credentials = credentials)
  def withCredentials(credentials: RequestCredentials): FetchClientBuilder[F] =
    withCredentialsOption(Some(credentials))
  def withDefaultCredentials: FetchClientBuilder[F] =
    withCredentialsOption(None)

  def withIntegrityOption(integrity: Option[String]): FetchClientBuilder[F] =
    copy(integrity = integrity)
  def withIntegrity(integrity: String): FetchClientBuilder[F] =
    withIntegrityOption(Some(integrity))
  def withDefaultIntegrity: FetchClientBuilder[F] =
    withIntegrityOption(None)

  def withKeepAliveOption(keepAlive: Option[Boolean]): FetchClientBuilder[F] =
    copy(keepAlive = keepAlive)
  def withKeepAlive(keepAlive: Boolean): FetchClientBuilder[F] =
    withKeepAliveOption(Some(keepAlive))
  def withDefaultKeepAlive: FetchClientBuilder[F] =
    withKeepAliveOption(None)

  def withModeOption(mode: Option[RequestMode]): FetchClientBuilder[F] =
    copy(mode = mode)
  def withMode(mode: RequestMode): FetchClientBuilder[F] =
    withModeOption(Some(mode))
  def withDefaultMode: FetchClientBuilder[F] =
    withModeOption(None)

  def withRedirectOption(redirect: Option[RequestRedirect]): FetchClientBuilder[F] =
    copy(redirect = redirect)
  def withRedirect(redirect: RequestRedirect): FetchClientBuilder[F] =
    withRedirectOption(Some(redirect))
  def withDefaultRedirect: FetchClientBuilder[F] =
    withRedirectOption(None)

  def withReferrerOption(referrer: Option[String]): FetchClientBuilder[F] =
    copy(referrer = referrer)
  def withReferrer(referrer: String): FetchClientBuilder[F] =
    withReferrerOption(Some(referrer))
  def withDefaultReferrer: FetchClientBuilder[F] =
    withReferrerOption(None)

  def withReferrerPolicyOption(referrerPolicy: Option[ReferrerPolicy]): FetchClientBuilder[F] =
    copy(referrerPolicy = referrerPolicy)
  def withReferrerPolicy(referrerPolicy: ReferrerPolicy): FetchClientBuilder[F] =
    withReferrerPolicyOption(Some(referrerPolicy))
  def withDefaultReferrerPolicy: FetchClientBuilder[F] =
    withReferrerPolicyOption(None)

  /** Creates a [[Client]].
    */
  def create: Client[F] = FetchClient.makeClient(
    requestTimeout,
    cache,
    credentials,
    integrity,
    keepAlive,
    mode,
    redirect,
    referrer,
    referrerPolicy)

  def resource: Resource[F, Client[F]] =
    Resource.eval(F.delay(create))
}

object FetchClientBuilder {

  /** Creates a FetchClientBuilder
    */
  def apply[F[_]: Async]: FetchClientBuilder[F] =
    new FetchClientBuilder[F](
      requestTimeout = defaults.RequestTimeout,
      cache = None,
      credentials = None,
      integrity = None,
      keepAlive = None,
      mode = None,
      redirect = None,
      referrer = None,
      referrerPolicy = None
    ) {}
}
