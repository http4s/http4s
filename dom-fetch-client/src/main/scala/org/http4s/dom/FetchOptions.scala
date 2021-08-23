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
final case class FetchOptions(
    cache: Option[RequestCache] = None,
    credentials: Option[RequestCredentials] = None,
    integrity: Option[String] = None,
    keepAlive: Option[Boolean] = None,
    mode: Option[RequestMode] = None,
    redirect: Option[RequestRedirect] = None,
    referrer: Option[FetchReferrer] = None,
    referrerPolicy: Option[ReferrerPolicy] = None
) {
  private[dom] def overrideWith(other: FetchOptions): FetchOptions =
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
  val Key: vault.Key[FetchOptions] = vault.Key.newKey[SyncIO, FetchOptions].unsafeRunSync()
}
