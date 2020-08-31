/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

/**
  * {{{
  *   The "Proxy-Authorization" header field allows the client to identify
  *   itself (or its user) to a proxy that requires authentication.
  * }}}
  *
  *  From [[https://tools.ietf.org/html/rfc7235#section-4.4 RFC-7235]]
  */
object `Proxy-Authorization` extends HeaderKey.Default
