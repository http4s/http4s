/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

/** Non-Standard Http Header, which is often added to a Response
  * to indicate that the response was built with a certain scripting
  * technology, such as "ASP".
  *
  * https://stackoverflow.com/a/33580769/1002111
  */
object `X-Powered-By` extends HeaderKey.Default
