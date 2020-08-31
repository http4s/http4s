/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client

class UnexpectedStatusSpec extends Http4sSpec {
  "UnexpectedStatus" should {
    "include status in message" in {
      val e = UnexpectedStatus(Status.NotFound)
      e.getMessage() must_== "unexpected HTTP status: 404 Not Found"
    }

    "not return null" in {
      prop { (status: Status) =>
        val e = UnexpectedStatus(status)
        e.getMessage() must not beNull
      }
    }
  }
}
