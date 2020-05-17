/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.headers

class AllowSpec extends HeaderLaws {
  checkAll("Allow", headerLaws(Allow))
}
