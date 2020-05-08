/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

class ContentEncodingSpec extends HeaderLaws {
  checkAll("Content-Encoding", headerLaws(`Content-Encoding`))
}
