/*
 * Copyright 2013 http4s.org
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

package org.http4s.headers

import org.http4s.{Http4sSuite, ParseResult}
import org.http4s.implicits.http4sSelectSyntaxOne

class AccessControlMaxAgeSuite extends Http4sSuite {
//  checkAll("Access-Control-MaxA-ge", headerLaws[`Access-Control-Max-Age`])

  test("render should age in seconds") {
    assertEquals(`Access-Control-Max-Age`.fromLong(120).map(_.renderString), ParseResult.success("Access-Control-Max-Age: 120"))
  }

}
