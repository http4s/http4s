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

import cats.effect.SyncIO
import org.scalajs.dom.experimental.ReferrerPolicy
import org.scalajs.dom.experimental.RequestCache
import org.scalajs.dom.experimental.RequestCredentials
import org.scalajs.dom.experimental.RequestMode
import org.scalajs.dom.experimental.RequestRedirect
import org.typelevel.vault

/** Options to pass to Fetch when using a FetchClient
  *
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
sealed abstract class FetchOptions extends Product with Serializable {
  def cache: Option[RequestCache]
  def credentials: Option[RequestCredentials]
  def integrity: Option[String]
  def keepAlive: Option[Boolean]
  def mode: Option[RequestMode]
  def redirect: Option[RequestRedirect]
  def referrer: Option[FetchReferrer]
  def referrerPolicy: Option[ReferrerPolicy]

  def withCacheOption(cache: Option[RequestCache]): FetchOptions
  def withCache(cache: RequestCache): FetchOptions =
    withCacheOption(Some(cache))
  def withoutCache: FetchOptions =
    withCacheOption(None)

  def withCredentialsOption(credentials: Option[RequestCredentials]): FetchOptions
  def withCredentials(credentials: RequestCredentials): FetchOptions =
    withCredentialsOption(Some(credentials))
  def withoutCredentials: FetchOptions =
    withCredentialsOption(None)

  def withIntegrityOption(integrity: Option[String]): FetchOptions
  def withIntegrity(integrity: String): FetchOptions =
    withIntegrityOption(Some(integrity))
  def withoutIntegrity: FetchOptions =
    withIntegrityOption(None)

  def withKeepAliveOption(keepAlive: Option[Boolean]): FetchOptions
  def withKeepAlive(keepAlive: Boolean): FetchOptions =
    withKeepAliveOption(Some(keepAlive))
  def withoutKeepAlive: FetchOptions =
    withKeepAliveOption(None)

  def withModeOption(mode: Option[RequestMode]): FetchOptions
  def withMode(mode: RequestMode): FetchOptions =
    withModeOption(Some(mode))
  def withoutMode: FetchOptions =
    withModeOption(None)

  def withRedirectOption(redirect: Option[RequestRedirect]): FetchOptions
  def withRedirect(redirect: RequestRedirect): FetchOptions =
    withRedirectOption(Some(redirect))
  def withoutRedirect: FetchOptions =
    withRedirectOption(None)

  def withReferrerOption(referrer: Option[FetchReferrer]): FetchOptions
  def withReferrer(referrer: FetchReferrer): FetchOptions =
    withReferrerOption(Some(referrer))
  def withoutReferrer: FetchOptions =
    withReferrerOption(None)

  def withReferrerPolicyOption(referrerPolicy: Option[ReferrerPolicy]): FetchOptions
  def withReferrerPolicy(referrerPolicy: ReferrerPolicy): FetchOptions =
    withReferrerPolicyOption(Some(referrerPolicy))
  def withoutReferrerPolicy: FetchOptions =
    withReferrerPolicyOption(None)

  /** Merge Two FetchOptions. `other` is prioritized. */
  def merge(other: FetchOptions): FetchOptions =
    FetchOptions(
      cache = other.cache.orElse(cache),
      credentials = other.credentials.orElse(credentials),
      integrity = other.integrity.orElse(integrity),
      keepAlive = other.keepAlive.orElse(keepAlive),
      mode = other.mode.orElse(mode),
      redirect = other.redirect.orElse(redirect),
      referrer = other.referrer.orElse(referrer),
      referrerPolicy = other.referrerPolicy.orElse(referrerPolicy)
    )
}

object FetchOptions {
  private[this] final case class FetchOptionsImpl(
      override final val cache: Option[RequestCache],
      override final val credentials: Option[RequestCredentials] = None,
      override final val integrity: Option[String] = None,
      override final val keepAlive: Option[Boolean] = None,
      override final val mode: Option[RequestMode] = None,
      override final val redirect: Option[RequestRedirect] = None,
      override final val referrer: Option[FetchReferrer] = None,
      override final val referrerPolicy: Option[ReferrerPolicy] = None
  ) extends FetchOptions {

    override def withCacheOption(cache: Option[RequestCache]): FetchOptions =
      copy(cache = cache)

    override def withCredentialsOption(credentials: Option[RequestCredentials]): FetchOptions =
      copy(credentials = credentials)

    override def withIntegrityOption(integrity: Option[String]): FetchOptions =
      copy(integrity = integrity)

    override def withKeepAliveOption(keepAlive: Option[Boolean]): FetchOptions =
      copy(keepAlive = keepAlive)

    override def withModeOption(mode: Option[RequestMode]): FetchOptions = copy(mode = mode)

    override def withRedirectOption(redirect: Option[RequestRedirect]): FetchOptions =
      copy(redirect = redirect)

    override def withReferrerOption(referrer: Option[FetchReferrer]): FetchOptions =
      copy(referrer = referrer)

    override def withReferrerPolicyOption(referrerPolicy: Option[ReferrerPolicy]): FetchOptions =
      copy(referrerPolicy = referrerPolicy)
  }

  def apply(
      cache: Option[RequestCache] = None,
      credentials: Option[RequestCredentials] = None,
      integrity: Option[String] = None,
      keepAlive: Option[Boolean] = None,
      mode: Option[RequestMode] = None,
      redirect: Option[RequestRedirect] = None,
      referrer: Option[FetchReferrer] = None,
      referrerPolicy: Option[ReferrerPolicy] = None
  ): FetchOptions =
    FetchOptionsImpl(
      cache,
      credentials,
      integrity,
      keepAlive,
      mode,
      redirect,
      referrer,
      referrerPolicy)

  val Key: vault.Key[FetchOptions] = vault.Key.newKey[SyncIO, FetchOptions].unsafeRunSync()
}
