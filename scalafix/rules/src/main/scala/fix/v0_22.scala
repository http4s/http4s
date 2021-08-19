/*
 * Copyright 2018 http4s.org
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

package fix

import scalafix.v1._

class v0_22 extends SemanticRule("v0_22") {
  override def fix(implicit doc: SemanticDocument): Patch =
    rewritePackages

  def rewritePackages(implicit doc: SemanticDocument): Patch =
    Patch.replaceSymbols(
      "org.http4s.server.tomcat" -> "org.http4s.tomcat.server",
      "org.http4s.server.jetty" -> "org.http4s.jetty.server",
      "org.http4s.client.jetty" -> "org.http4s.jetty.client",
      "org.http4s.client.okhttp" -> "org.http4s.okhttp.client",
      "org.http4s.client.asynchttpclient" -> "org.http4s.asynchttpclient",
      "org.http4s.client.blaze" -> "org.http4s.blaze.client",
      "org.http4s.server.blaze" -> "org.http4s.blaze.server"
    )
}
