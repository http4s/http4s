/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.client

import cats.Id
import com.comcast.ip4s._
import org.http4s.Http4sSuite
import org.http4s.Request
import org.http4s.Uri
import org.http4s.client.RequestKey

class IgnoredHostsSuite extends Http4sSuite {

  private def key(uri: String): RequestKey =
    RequestKey.fromRequest(Request[Id](uri = Uri.unsafeFromString(uri)))

  test("the default mask ignores the loopback hosts") {
    assert(IgnoredHosts.default.ignores(key("http://localhost/")))
    assert(IgnoredHosts.default.ignores(key("http://localhost:8080/")))
    assert(IgnoredHosts.default.ignores(key("http://127.0.0.1/")))
    assert(IgnoredHosts.default.ignores(key("http://127.1.2.3:9000/")))
    // Uri.Host renders an IPv6 address bracketed, which the default mask spells out.
    assert(IgnoredHosts.default.ignores(key("http://[::1]/")))
  }

  test("the default mask ignores nothing else") {
    assert(!IgnoredHosts.default.ignores(key("http://origin.example:8080/")))
    assert(!IgnoredHosts.default.ignores(key("http://notlocalhost/")))
    assert(!IgnoredHosts.default.ignores(key("http://128.0.0.1/")))
    assert(!IgnoredHosts.default.ignores(key("http://[::2]/")))
  }

  test("matching is case insensitive") {
    assert(IgnoredHosts.default.ignores(key("http://LOCALHOST/")))
    assert(
      IgnoredHosts.fromString("Origin.Example").exists(_.ignores(key("http://origin.EXAMPLE/")))
    )
  }

  test("a disjunct may match a prefix, a suffix, or every host") {
    val hosts = IgnoredHosts.fromString("*.internal|10.*|*corp*")
    assert(hosts.exists(_.ignores(key("http://db.internal/"))))
    assert(hosts.exists(_.ignores(key("http://10.1.2.3/"))))
    assert(hosts.exists(_.ignores(key("http://my-corp-host/"))))
    assert(!hosts.exists(_.ignores(key("http://internal/"))))
    assert(!hosts.exists(_.ignores(key("http://110.1.2.3/"))))
    assert(IgnoredHosts.fromString("*").exists(_.ignores(key("http://origin.example/"))))
  }

  test("a mask naming no host ignores nothing") {
    assertEquals(IgnoredHosts.fromString(""), None)
    assertEquals(IgnoredHosts.fromString(" | "), None)
    assert(!IgnoredHosts.none.ignores(key("http://localhost/")))
  }

  test("a no_proxy entry with a leading dot covers the domain and its subdomains") {
    val hosts = IgnoredHosts.fromNoProxy(".example.com, 10.0.0.1 ,*.internal")
    assert(hosts.exists(_.ignores(key("http://example.com/"))))
    assert(hosts.exists(_.ignores(key("http://host.example.com/"))))
    assert(hosts.exists(_.ignores(key("http://10.0.0.1/"))))
    assert(hosts.exists(_.ignores(key("http://db.internal/"))))
    assert(!hosts.exists(_.ignores(key("http://example.org/"))))
  }

  test("a no_proxy entry without a leading dot is exact") {
    val hosts = IgnoredHosts.fromNoProxy("example.com")
    assert(hosts.exists(_.ignores(key("http://example.com/"))))
    assert(!hosts.exists(_.ignores(key("http://host.example.com/"))))
  }

  test("a no_proxy list naming no host yields None") {
    assertEquals(IgnoredHosts.fromNoProxy(" , "), None)
  }

  test("ip4s hosts match the same mask as request keys") {
    assert(IgnoredHosts.default.ignores(host"localhost"))
    assert(IgnoredHosts.default.ignores(ip"127.0.0.1"))
    assert(IgnoredHosts.default.ignores(ip"::1"))
    assert(!IgnoredHosts.default.ignores(host"origin.example"))
  }
}
