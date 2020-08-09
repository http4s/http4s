/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

final class AccessControlExposeHeadersSpec extends HeaderLaws {
  checkAll("Access-Control-Expose-Headers", headerLaws(`Access-Control-Expose-Headers`))
}
