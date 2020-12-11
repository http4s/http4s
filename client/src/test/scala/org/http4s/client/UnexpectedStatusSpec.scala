/*
 * Copyright 2014 http4s.org
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

package org.http4s
package client

class UnexpectedStatusSpec extends Http4sSpec {
  "UnexpectedStatus" should {
    "include status and original request in message" in {
      val e = UnexpectedStatus(Status.NotFound, Method.GET, Uri.unsafeFromString("www.google.com"))
      e.getMessage() must_== "unexpected HTTP status: 404 Not Found for request GET www.google.com"
    }

    "not return null" in {
      prop { (status: Status) =>
        val e = UnexpectedStatus(status, Method.GET, Uri.unsafeFromString("www.google.it"))
        e.getMessage() must not beNull
      }
    }
  }
}
