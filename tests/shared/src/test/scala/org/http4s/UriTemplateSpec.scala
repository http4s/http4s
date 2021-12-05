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

package org.http4s

import com.comcast.ip4s._
import org.http4s.Uri.Authority
import org.http4s.Uri.RegName
import org.http4s.Uri.Scheme
import org.http4s.Uri.UserInfo
import org.http4s.UriTemplate._
import org.typelevel.ci._

class UriTemplateSpec extends Http4sSuite {
  {
    test("UriTemplate should render /") {
      assertEquals(UriTemplate().toString, "/")
      assertEquals(UriTemplate(path = List()).toString, "/")
    }
    test("UriTemplate should render /test") {
      assertEquals(UriTemplate(path = List(PathElm("test"))).toString, "/test")
    }
    test("UriTemplate should render {rel}") {
      assertEquals(UriTemplate(path = List(VarExp("rel"))).toString, "{rel}")
    }
    test("UriTemplate should render /?ref={path,name}") {
      val query = List(ParamVarExp("ref", "path", "name"))
      assertEquals(UriTemplate(query = query).toString, "/?ref={path,name}")
    }
    test("UriTemplate should render /here?ref={+path,name}") {
      val query = List(ParamReservedExp("ref", "path", "name"))
      assertEquals(UriTemplate(query = query).toString, "/?ref={+path,name}")
    }
    test("UriTemplate should render /here?ref={path}") {
      val path = List(PathElm("here"))
      val query = List(ParamVarExp("ref", "path"))
      assertEquals(UriTemplate(path = path, query = query).toString, "/here?ref={path}")
    }
    test("UriTemplate should render /here?{ref[name]}") {
      val path = List(PathElm("here"))
      val query = List(ParamExp("ref[name]"))
      assertEquals(UriTemplate(path = path, query = query).toString, "/here{?ref[name]}")
    }
    test("UriTemplate should render /here?ref={+path}") {
      val path = List(PathElm("here"))
      val query = List(ParamReservedExp("ref", "path"))
      assertEquals(UriTemplate(path = path, query = query).toString, "/here?ref={+path}")
    }
    test("UriTemplate should render {somewhere}?ref={path}") {
      val path = List(VarExp("somewhere"))
      val query = List(ParamVarExp("ref", "path"))
      assertEquals(UriTemplate(path = path, query = query).toString, "{somewhere}?ref={path}")
    }
    test("UriTemplate should render /?ref={file,folder}") {
      val query = List(ParamVarExp("ref", List("file", "folder")))
      assertEquals(UriTemplate(query = query).toString, "/?ref={file,folder}")
    }
    test("UriTemplate should render /orders{/id}") {
      assertEquals(
        UriTemplate(path = List(PathElm("orders"), PathExp("id"))).toString,
        "/orders{/id}",
      )
    }
    test("UriTemplate should render /orders{/id,item}") {
      assertEquals(
        UriTemplate(path = List(PathElm("orders"), PathExp("id", "item"))).toString,
        "/orders{/id,item}",
      )
    }
    test("UriTemplate should render /orders{id}") {
      assertEquals(
        UriTemplate(path = List(PathElm("orders"), VarExp("id"))).toString,
        "/orders{id}",
      )
    }
    test("UriTemplate should render {id,item}") {
      assertEquals(UriTemplate(path = List(VarExp("id", "item"))).toString, "{id,item}")
    }
    test("UriTemplate should render /orders{id,item}") {
      assertEquals(
        UriTemplate(path = List(PathElm("orders"), VarExp("id", "item"))).toString,
        "/orders{id,item}",
      )
    }
    test("UriTemplate should render /some/test{rel}") {
      assertEquals(
        UriTemplate(path = List(PathElm("some"), PathElm("test"), VarExp("rel"))).toString,
        "/some/test{rel}",
      )
    }
    test("UriTemplate should render /some{rel}/test") {
      assertEquals(
        UriTemplate(path = List(PathElm("some"), VarExp("rel"), PathElm("test"))).toString,
        "/some{rel}/test",
      )
    }
    test("UriTemplate should render /some{rel}/test{?id}") {
      val path = List(PathElm("some"), VarExp("rel"), PathElm("test"))
      val query = List(ParamExp("id"))
      assertEquals(UriTemplate(path = path, query = query).toString, "/some{rel}/test{?id}")
    }
    test("UriTemplate should render /item{?id}{&option}") {
      val path = List(PathElm("item"))
      val query = List(ParamExp("id"), ParamContExp("option"))
      assertEquals(UriTemplate(path = path, query = query).toString, "/item{?id}{&option}")
    }
    test("UriTemplate should render /items{?start,limit}") {
      val path = List(PathElm("items"))
      val query = List(ParamExp("start", "limit"))
      assertEquals(UriTemplate(path = path, query = query).toString, "/items{?start,limit}")
    }
    test("UriTemplate should render /items?start=0{&limit}") {
      val path = List(PathElm("items"))
      val query = List(ParamElm("start", "0"), ParamContExp("limit"))
      assertEquals(UriTemplate(path = path, query = query).toString, "/items?start=0{&limit}")
    }
    test("UriTemplate should render /?switch") {
      assertEquals(UriTemplate(path = Nil, query = List(ParamElm("switch"))).toString, "/?switch")
    }
    test("UriTemplate should render /?some=") {
      assertEquals(UriTemplate(query = List(ParamElm("some", ""))).toString, "/?some=")
    }
    test("UriTemplate should render /{?id}") {
      assertEquals(UriTemplate(query = List(ParamExp("id"))).toString, "/{?id}")
      assertEquals(UriTemplate(query = List(ParamExp("id"))).toString, "/{?id}")
    }
    test("UriTemplate should render /test{?id}") {
      val path = List(PathElm("test"))
      val query = List(ParamExp("id"))
      assertEquals(UriTemplate(path = path, query = query).toString, "/test{?id}")
    }
    test("UriTemplate should render /test{?start,limit}") {
      val path = List(PathElm("test"))
      val query = List(ParamExp("start", "limit"))
      assertEquals(UriTemplate(path = path, query = query).toString, "/test{?start,limit}")
    }
    test("UriTemplate should render /orders?id=1{&start,limit}") {
      val path = List(PathElm("orders"))
      val query = List(ParamElm("id", "1"), ParamContExp("start", "limit"))
      assertEquals(UriTemplate(path = path, query = query).toString, "/orders?id=1{&start,limit}")
    }
    test("UriTemplate should render /orders?item=2&item=4{&start,limit}") {
      val path = List(PathElm("orders"))
      val query = List(ParamExp("start", "limit"), ParamElm("item", List("2", "4")))
      assertEquals(
        UriTemplate(path = path, query = query).toString,
        "/orders?item=2&item=4{&start,limit}",
      )
    }
    test("UriTemplate should render /orders?item=2&item=4{&start}{&limit}") {
      val path = List(PathElm("orders"))
      val query = List(ParamExp("start"), ParamElm("item", List("2", "4")), ParamExp("limit"))
      assertEquals(
        UriTemplate(path = path, query = query).toString,
        "/orders?item=2&item=4{&start}{&limit}",
      )
    }
    test("UriTemplate should render /search?option{&term}") {
      val path = List(PathElm("search"))
      val query = List(ParamExp("term"), ParamElm("option"))
      assertEquals(UriTemplate(path = path, query = query).toString, "/search?option{&term}")
    }
    test("UriTemplate should render /search?option={&term}") {
      val path = List(PathElm("search"))
      val query = List(ParamExp("term"), ParamElm("option", ""))
      assertEquals(UriTemplate(path = path, query = query).toString, "/search?option={&term}")
    }
    test("UriTemplate should render /{#frg}") {
      val fragment = List(SimpleFragmentExp("frg"))
      assertEquals(UriTemplate(fragment = fragment).toString, "/{#frg}")
    }
    test("UriTemplate should render /{#x,y}") {
      val fragment = List(MultiFragmentExp("x", "y"))
      assertEquals(UriTemplate(fragment = fragment).toString, "/{#x,y}")
    }
    test("UriTemplate should render /#test") {
      val fragment = List(FragmentElm("test"))
      assertEquals(UriTemplate(fragment = fragment).toString, "/#test")
    }
    test("UriTemplate should render {path}/search{?term}{#section}") {
      val path = List(VarExp("path"), PathElm("search"))
      val query = List(ParamExp("term"))
      val fragment = List(SimpleFragmentExp("section"))
      assertEquals(
        UriTemplate(path = path, query = query, fragment = fragment).toString,
        "{path}/search{?term}{#section}",
      )
    }
    test("UriTemplate should render {+path}/search{?term}{#section}") {
      val path = List(ReservedExp("path"), PathElm("search"))
      val query = List(ParamExp("term"))
      val fragment = List(SimpleFragmentExp("section"))
      assertEquals(
        UriTemplate(path = path, query = query, fragment = fragment).toString,
        "{+path}/search{?term}{#section}",
      )
    }
    test("UriTemplate should render {+var}") {
      val path = List(ReservedExp("var"))
      assertEquals(UriTemplate(path = path).toString, "{+var}")
    }
    test("UriTemplate should render {+path}/here") {
      val path = List(ReservedExp("path"), PathElm("here"))
      assertEquals(UriTemplate(path = path).toString, "{+path}/here")
    }
    test("UriTemplate should render /?") {
      assertEquals(UriTemplate(query = List(ParamElm("", Nil)), fragment = Nil).toString, "/?")
    }
    test("UriTemplate should render /?#") {
      val fragment = List(FragmentElm(""))
      assertEquals(
        UriTemplate(query = List(ParamElm("", Nil)), fragment = fragment).toString,
        "/?#",
      )
    }
    test("UriTemplate should render /#") {
      val fragment = List(FragmentElm(""))
      assertEquals(UriTemplate(query = Nil, fragment = fragment).toString, "/#")
    }
    test("UriTemplate should render http://[1ab:1ab:1ab:1ab:1ab:1ab:1ab:1ab]/foo?bar=baz") {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv6Address(ipv6"1ab:1ab:1ab:1ab:1ab:1ab:1ab:1ab")
      val authority = Some(Authority(host = host))
      val path = List(PathElm("foo"))
      val query = List(ParamElm("bar", "baz"))
      assertEquals(
        UriTemplate(scheme, authority, path, query).toString,
        "http://[1ab:1ab:1ab:1ab:1ab:1ab:1ab:1ab]/foo?bar=baz",
      )
    }
    test("UriTemplate should render http://www.foo.com/foo?bar=baz") {
      val scheme = Some(Scheme.http)
      val host = RegName("www.foo.com")
      val authority = Some(Authority(host = host))
      val path = List(PathElm("foo"))
      val query = List(ParamElm("bar", "baz"))
      assertEquals(
        UriTemplate(scheme, authority, path, query).toString,
        "http://www.foo.com/foo?bar=baz",
      )
    }
    test("UriTemplate should render http://www.foo.com:80") {
      val scheme = Some(Scheme.http)
      val host = RegName(ci"www.foo.com")
      val authority = Some(Authority(host = host, port = Some(80)))
      val path = Nil
      val query = Nil
      assertEquals(UriTemplate(scheme, authority, path, query).toString, "http://www.foo.com:80")
    }
    test("UriTemplate should render http://www.foo.com") {
      assertEquals(
        UriTemplate(Some(Scheme.http), Some(Authority(host = RegName(ci"www.foo.com")))).toString,
        "http://www.foo.com",
      )
    }
    test("UriTemplate should render http://192.168.1.1") {
      assertEquals(
        UriTemplate(
          Some(Scheme.http),
          Some(Authority(host = Uri.Ipv4Address(ipv4"192.168.1.1"))),
        ).toString,
        "http://192.168.1.1",
      )
    }
    test("UriTemplate should render http://192.168.1.1:8080") {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv4Address(ipv4"192.168.1.1")
      val authority = Some(Authority(host = host, port = Some(8080)))
      val query = List(ParamElm("", Nil))
      assertEquals(UriTemplate(scheme, authority, Nil, query).toString, "http://192.168.1.1:8080/?")
      assertEquals(UriTemplate(scheme, authority, Nil, Nil).toString, "http://192.168.1.1:8080")
    }
    test("UriTemplate should render http://192.168.1.1:80/c?GB=object&Class=one") {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv4Address(ipv4"192.168.1.1")
      val authority = Some(Authority(host = host, port = Some(80)))
      val path = List(PathElm("c"))
      val query = List(ParamElm("GB", "object"), ParamElm("Class", "one"))
      assertEquals(
        UriTemplate(scheme, authority, path, query).toString,
        "http://192.168.1.1:80/c?GB=object&Class=one",
      )
    }
    test("UriTemplate should render http://[2001:db8:85a3:8d3:1319:8a2e:370:7344]:8080") {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv6Address(ipv6"2001:db8:85a3:8d3:1319:8a2e:370:7344")
      val authority = Some(Authority(host = host, port = Some(8080)))
      assertEquals(
        UriTemplate(scheme, authority).toString,
        "http://[2001:db8:85a3:8d3:1319:8a2e:370:7344]:8080",
      )
    }
    test("UriTemplate should render http://[2001:db8::7]") {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv6Address(ipv6"2001:db8::7")
      val authority = Some(Authority(host = host))
      assertEquals(UriTemplate(scheme, authority).toString, "http://[2001:db8::7]")
    }
    test("UriTemplate should render http://[2001:db8::7]/c?GB=object&Class=one") {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv6Address(ipv6"2001:db8::7")
      val authority = Some(Authority(host = host))
      val path = List(PathElm("c"))
      val query = List(ParamElm("GB", "object"), ParamElm("Class", "one"))
      assertEquals(
        UriTemplate(scheme, authority, path, query).toString,
        "http://[2001:db8::7]/c?GB=object&Class=one",
      )
    }
    test("UriTemplate should render http://[2001:db8:85a3:8d3:1319:8a2e:370:7344]") {
      assertEquals(
        UriTemplate(
          Some(Scheme.http),
          Some(Authority(host = Uri.Ipv6Address(ipv6"2001:db8:85a3:8d3:1319:8a2e:370:7344"))),
        ).toString,
        "http://[2001:db8:85a3:8d3:1319:8a2e:370:7344]",
      )
    }
    test("UriTemplate should render email address (not supported at the moment)") {
      import org.http4s.syntax.all._
      assertEquals(
        UriTemplate(Some(scheme"mailto"), path = List(PathElm("John.Doe@example.com"))).toString,
        "mailto:/John.Doe@example.com",
      )
      // but should be "mailto:John.Doe@example.com"
    }
    test("UriTemplate should render http://username:password@some.example.com") {
      val scheme = Some(Scheme.http)
      val host = RegName("some.example.com")
      val authority = Some(Authority(Some(UserInfo("username", Some("password"))), host, None))
      assertEquals(
        UriTemplate(scheme, authority).toString,
        "http://username:password@some.example.com",
      )
      assertEquals(
        UriTemplate(scheme, authority, Nil).toString,
        "http://username:password@some.example.com",
      )
    }
    test(
      "UriTemplate should render http://username:password@some.example.com/some/path?param1=5&param-without-value"
    ) {
      val scheme = Some(Scheme.http)
      val host = RegName("some.example.com")
      val authority = Some(Authority(Some(UserInfo("username", Some("password"))), host, None))
      val path = List(PathElm("some"), PathElm("path"))
      val query = List(ParamElm("param1", "5"), ParamElm("param-without-value"))
      assertEquals(
        UriTemplate(scheme, authority, path, query).toString,
        "http://username:password@some.example.com/some/path?param1=5&param-without-value",
      )
    }
  }

  {
    test("UriTemplate.toUriIfPossible should convert / to Uri") {
      import org.http4s.syntax.all._
      assertEquals(UriTemplate().toUriIfPossible.get, Uri())
      assertEquals(UriTemplate(path = Nil).toUriIfPossible.get, uri"")
    }
    test("UriTemplate.toUriIfPossible should convert /test to Uri") {
      import org.http4s.syntax.all._
      assertEquals(UriTemplate(path = List(PathElm("test"))).toUriIfPossible.get, uri"/test")
    }
    test("UriTemplate.toUriIfPossible should convert {path} to UriTemplate") {
      val tpl = UriTemplate(path = List(VarExp("path")))
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert {+path} to UriTemplate") {
      val tpl = UriTemplate(path = List(ReservedExp("path")))
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert {+path} to Uri") {
      import org.http4s.syntax.all._
      val tpl = UriTemplate(path = List(ReservedExp("path"))).expandPath("path", List("foo", "bar"))
      assertEquals(tpl.toUriIfPossible.get, uri"/foo/bar")
    }
    test("UriTemplate.toUriIfPossible should convert /some/test/{rel} to UriTemplate") {
      val tpl = UriTemplate(path = List(PathElm("some"), PathElm("test"), VarExp("rel")))
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert /some/{rel}/test to UriTemplate") {
      val tpl = UriTemplate(path = List(PathElm("some"), VarExp("rel"), PathElm("test")))
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert /some/{rel}/test{?id} to UriTemplate") {
      val path = List(PathElm("some"), VarExp("rel"), PathElm("test"))
      val query = List(ParamExp("id"))
      val tpl = UriTemplate(path = path, query = query)
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert ?switch to Uri") {
      import org.http4s.syntax.all._
      assertEquals(UriTemplate(query = List(ParamElm("switch"))).toUriIfPossible.get, uri"?switch")
    }
    test("UriTemplate.toUriIfPossible should convert ?switch=foo&switch=bar to Uri") {
      import org.http4s.syntax.all._
      assertEquals(
        UriTemplate(query = List(ParamElm("switch", List("foo", "bar")))).toUriIfPossible.get,
        uri"?switch=foo&switch=bar",
      )
    }
    test("UriTemplate.toUriIfPossible should convert /{?id} to UriTemplate") {
      val tpl = UriTemplate(query = List(ParamExp("id")))
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert /test{?id} to UriTemplate") {
      val path = List(PathElm("test"))
      val query = List(ParamExp("id"))
      val tpl = UriTemplate(path = path, query = query)
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert /test{?start,limit} to UriTemplate") {
      val path = List(PathElm("test"))
      val query = List(ParamExp("start"), ParamExp("limit"))
      val tpl = UriTemplate(path = path, query = query)
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert /orders?id=1{&start,limit} to UriTemplate") {
      val path = List(PathElm("orders"))
      val query = List(ParamElm("id", "1"), ParamExp("start"), ParamExp("limit"))
      val tpl = UriTemplate(path = path, query = query)
      assert(tpl.toUriIfPossible.isFailure)
    }
    test(
      "UriTemplate.toUriIfPossible should convert /orders?item=2&item=4{&start,limit} to UriTemplate"
    ) {
      val path = List(PathElm("orders"))
      val query = List(ParamExp("start"), ParamElm("item", List("2", "4")), ParamExp("limit"))
      val tpl = UriTemplate(path = path, query = query)
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert /search?option{&term} to UriTemplate") {
      val path = List(PathElm("search"))
      val query = List(ParamExp("term"), ParamElm("option"))
      val tpl = UriTemplate(path = path, query = query)
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert /{#frg} to UriTemplate") {
      val fragment = List(SimpleFragmentExp("frg"))
      val tpl = UriTemplate(fragment = fragment)
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert /{#x,y} to UriTemplate") {
      val fragment = List(MultiFragmentExp("x", "y"))
      val tpl = UriTemplate(fragment = fragment)
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert /#test to Uri") {
      val fragment = List(FragmentElm("test"))
      assertEquals(
        UriTemplate(fragment = fragment).toUriIfPossible.get,
        Uri(fragment = Some("test")),
      )
    }
    test(
      "UriTemplate.toUriIfPossible should convert {path}/search{?term}{#section} to UriTemplate"
    ) {
      val path = List(VarExp("path"), PathElm("search"))
      val query = List(ParamExp("term"))
      val fragment = List(SimpleFragmentExp("section"))
      val tpl = UriTemplate(path = path, query = query, fragment = fragment)
      assert(tpl.toUriIfPossible.isFailure)
    }
    test(
      "UriTemplate.toUriIfPossible should convert {+path}/search{?term}{#section} to UriTemplate"
    ) {
      val path = List(ReservedExp("path"), PathElm("search"))
      val query = List(ParamExp("term"))
      val fragment = List(SimpleFragmentExp("section"))
      val tpl = UriTemplate(path = path, query = query, fragment = fragment)
      assert(tpl.toUriIfPossible.isFailure)
    }
    test("UriTemplate.toUriIfPossible should convert /? to Uri") {
      assertEquals(
        UriTemplate(query = List(ParamElm("", Nil)), fragment = Nil).toUriIfPossible.get,
        Uri(query = Query.unsafeFromString("")),
      )
    }
    test("UriTemplate.toUriIfPossible should convert /?# to Uri") {
      val fragment = List(FragmentElm(""))
      assertEquals(
        UriTemplate(query = List(ParamElm("", Nil)), fragment = fragment).toUriIfPossible.get,
        Uri(query = Query.unsafeFromString(""), fragment = Some("")),
      )
    }
    test("UriTemplate.toUriIfPossible should convert /# to Uri") {
      val fragment = List(FragmentElm(""))
      assertEquals(UriTemplate(fragment = fragment).toUriIfPossible.get, Uri(fragment = Some("")))
    }
    test(
      "UriTemplate.toUriIfPossible should convert http://[01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab]/{rel}/search{?term}{#section} to UriTemplate"
    ) {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv6Address(ipv6"01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab")
      val authority = Some(Authority(host = host))
      val path = List(VarExp("rel"), PathElm("search"))
      val query = List(ParamExp("term"))
      val fragment = List(SimpleFragmentExp("section"))
      val tpl = UriTemplate(scheme, authority, path, query, fragment)
      assert(tpl.toUriIfPossible.isFailure)
    }
    test(
      "UriTemplate.toUriIfPossible should convert http://[01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab]:8080/{rel}/search{?term}{#section} to UriTemplate"
    ) {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv6Address(ipv6"01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab")
      val authority = Some(Authority(host = host, port = Some(8080)))
      val path = List(VarExp("rel"), PathElm("search"))
      val query = List(ParamExp("term"))
      val fragment = List(SimpleFragmentExp("section"))
      val tpl = UriTemplate(scheme, authority, path, query, fragment)
      assert(tpl.toUriIfPossible.isFailure)
    }
    test(
      "UriTemplate.toUriIfPossible should convert http://[01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab]/foo?bar=baz Uri"
    ) {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv6Address(ipv6"01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab")
      val authority = Some(Authority(host = host))
      val path = List(PathElm("foo"))
      val query = List(ParamElm("bar", List("baz")))
      import org.http4s.syntax.all._
      assertEquals(
        UriTemplate(scheme, authority, path, query).toUriIfPossible.get,
        Uri(scheme, authority, path"/foo", Query.unsafeFromString("bar=baz")),
      )
    }
    test("UriTemplate.toUriIfPossible should convert http://www.foo.com/foo?bar=baz to Uri") {
      val scheme = Some(Scheme.http)
      val host = RegName("www.foo.com")
      val authority = Some(Authority(host = host))
      val path = List(PathElm("foo"))
      val query = List(ParamElm("bar", "baz"))
      import org.http4s.syntax.all._
      assertEquals(
        UriTemplate(scheme, authority, path, query).toUriIfPossible.get,
        Uri(scheme, authority, path"/foo", Query.unsafeFromString("bar=baz")),
      )
    }
    test("UriTemplate.toUriIfPossible should convert http://www.foo.com:80 to Uri") {
      val scheme = Some(Scheme.http)
      val host = RegName(ci"www.foo.com")
      val authority = Some(Authority(host = host, port = Some(80)))
      val path = Nil
      assertEquals(
        UriTemplate(scheme, authority, path).toUriIfPossible.get,
        Uri(scheme, authority, Uri.Path.empty),
      )
    }
    test("UriTemplate.toUriIfPossible should convert http://www.foo.com to Uri") {
      val scheme = Some(Scheme.http)
      val host = RegName(ci"www.foo.com")
      val authority = Some(Authority(host = host))
      assertEquals(
        UriTemplate(
          Some(Scheme.http),
          Some(Authority(host = RegName(ci"www.foo.com"))),
        ).toUriIfPossible.get,
        Uri(scheme, authority),
      )
    }
    test("UriTemplate.toUriIfPossible should convert http://192.168.1.1 to Uri") {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv4Address(ipv4"192.168.1.1")
      val authority = Some(Authority(None, host, None))
      assertEquals(UriTemplate(scheme, authority).toUriIfPossible.get, Uri(scheme, authority))
    }
    test("UriTemplate.toUriIfPossible should convert http://192.168.1.1:8080 to Uri") {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv4Address(ipv4"192.168.1.1")
      val authority = Some(Authority(host = host, port = Some(8080)))
      val query = List(ParamElm("", Nil))
      assertEquals(
        UriTemplate(scheme, authority, Nil, query).toUriIfPossible.get,
        Uri(scheme, authority, Uri.Path.empty, Query.unsafeFromString("")),
      )
      assertEquals(
        UriTemplate(scheme, authority, Nil, Nil).toUriIfPossible.get,
        Uri(scheme, authority, Uri.Path.empty, Query.empty),
      )
    }
    test(
      "UriTemplate.toUriIfPossible should convert http://192.168.1.1:80/c?GB=object&Class=one to Uri"
    ) {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv4Address(ipv4"192.168.1.1")
      val authority = Some(Authority(host = host, port = Some(80)))
      val path = List(PathElm("c"))
      val query = List(ParamElm("GB", "object"), ParamElm("Class", "one"))
      import org.http4s.syntax.all._
      assertEquals(
        UriTemplate(scheme, authority, path, query).toUriIfPossible.get,
        Uri(scheme, authority, path"/c", Query.unsafeFromString("GB=object&Class=one")),
      )
    }
    test(
      "UriTemplate.toUriIfPossible should convert http://[2001:db8::7]/c?GB=object&Class=one to Uri"
    ) {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv6Address(ipv6"2001:db8::7")
      val authority = Some(Authority(host = host))
      val path = List(PathElm("c"))
      val query = List(ParamElm("GB", "object"), ParamElm("Class", "one"))
      import org.http4s.syntax.all._
      assertEquals(
        UriTemplate(scheme, authority, path, query).toUriIfPossible.get,
        Uri(scheme, authority, path"/c", Query.unsafeFromString("GB=object&Class=one")),
      )
    }
    test(
      "UriTemplate.toUriIfPossible should convert http://[2001:0db8:85a3:08d3:1319:8a2e:0370:7344] to Uri"
    ) {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv6Address(ipv6"2001:0db8:85a3:08d3:1319:8a2e:0370:7344")
      val authority = Some(Authority(None, host, None))
      assertEquals(UriTemplate(scheme, authority).toUriIfPossible.get, Uri(scheme, authority))
    }
    test(
      "UriTemplate.toUriIfPossible should convert http://[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]:8080 to Uri"
    ) {
      val scheme = Some(Scheme.http)
      val host = Uri.Ipv6Address(ipv6"2001:0db8:85a3:08d3:1319:8a2e:0370:7344")
      val authority = Some(Authority(None, host, Some(8080)))
      assertEquals(UriTemplate(scheme, authority).toUriIfPossible.get, Uri(scheme, authority))
    }
    test(
      "UriTemplate.toUriIfPossible should convert https://username:password@some.example.com to Uri"
    ) {
      val scheme = Some(Scheme.https)
      val host = RegName("some.example.com")
      val authority = Some(Authority(Some(UserInfo("username", Some("password"))), host, None))
      assertEquals(
        UriTemplate(scheme, authority, Nil, Nil, Nil).toUriIfPossible.get,
        Uri(scheme, authority),
      )
    }
    test(
      "UriTemplate.toUriIfPossible should convert http://username:password@some.example.com/some/path?param1=5&param-without-value to Uri"
    ) {
      val scheme = Some(Scheme.http)
      val host = RegName("some.example.com")
      val authority = Some(Authority(Some(UserInfo("username", Some("password"))), host, None))
      val path = List(PathElm("some"), PathElm("path"))
      val query = List(ParamElm("param1", "5"), ParamElm("param-without-value"))
      import org.http4s.syntax.all._
      assertEquals(
        UriTemplate(scheme, authority, path, query).toUriIfPossible.get,
        Uri(
          scheme,
          authority,
          path"/some/path",
          Query.unsafeFromString("param1=5&param-without-value"),
        ),
      )
    }
    test(
      "UriTemplate.toUriIfPossible should convert http://username:password@some.example.com/some/path?param1=5&param-without-value#sec-1.2 to Uri"
    ) {
      val scheme = Some(Scheme.http)
      val host = RegName("some.example.com")
      val authority = Some(Authority(Some(UserInfo("username", Some("password"))), host, None))
      val path = List(PathElm("some"), PathElm("path"))
      val query = List(ParamElm("param1", "5"), ParamElm("param-without-value"))
      val fragment = List(FragmentElm("sec-1.2"))
      import org.http4s.syntax.all._
      assertEquals(
        UriTemplate(scheme, authority, path, query, fragment).toUriIfPossible.get,
        Uri(
          scheme,
          authority,
          path"/some/path",
          Query.unsafeFromString("param1=5&param-without-value"),
          Some("sec-1.2"),
        ),
      )
    }
  }

  {
    test("UriTemplate.expandAny should expand {path} to /123") {
      val path = List(VarExp("path"))
      assertEquals(
        UriTemplate(path = path).expandAny("path", "123"),
        UriTemplate(path = List(PathElm("123"))),
      )
    }
    test("UriTemplate.expandAny should expand {+path} to /123") {
      val path = List(ReservedExp("path"))
      assertEquals(
        UriTemplate(path = path).expandAny("path", "123"),
        UriTemplate(path = List(PathElm("123"))),
      )
    }
    test("UriTemplate.expandAny should expand /?ref={path} to /?ref=123") {
      val query = List(ParamVarExp("ref", "path"))
      assertEquals(
        UriTemplate(query = query).expandAny("path", "123"),
        UriTemplate(query = List(ParamElm("ref", "123"))),
      )
    }
    test("UriTemplate.expandAny should expand /?ref={file,folder} to /?ref=123&ref={folder}") {
      val query = List(ParamVarExp("ref", List("file", "folder")))
      assertEquals(
        UriTemplate(query = query).expandAny("file", "123"),
        UriTemplate(query = List(ParamElm("ref", "123"), ParamVarExp("ref", "folder"))),
      )
    }
    test("UriTemplate.expandAny should expand /?ref={+path} to /?ref=123") {
      val query = List(ParamReservedExp("ref", "path"))
      assertEquals(
        UriTemplate(query = query).expandAny("path", "123"),
        UriTemplate(query = List(ParamElm("ref", "123"))),
      )
    }
    test("UriTemplate.expandAny should expand /?ref={+file,folder} to /?ref=123&ref={+folder}") {
      val query = List(ParamReservedExp("ref", List("file", "folder")))
      assertEquals(
        UriTemplate(query = query).expandAny("file", "123"),
        UriTemplate(query = List(ParamElm("ref", "123"), ParamReservedExp("ref", "folder"))),
      )
    }
    test("UriTemplate.expandAny should expand {/id} to /123") {
      val path = List(PathExp("id"))
      assertEquals(
        UriTemplate(path = path).expandAny("id", "123"),
        UriTemplate(path = List(PathElm("123"))),
      )
    }
    test("UriTemplate.expandAny should expand /id{/id} to /id/123") {
      val path = List(PathElm("id"), VarExp("id"))
      assertEquals(
        UriTemplate(path = path).expandAny("id", "123"),
        UriTemplate(path = List(PathElm("id"), PathElm("123"))),
      )
    }
    test(
      "UriTemplate.expandAny should expand /orders{/id,item} to UriTemplate /orders/123{/item}"
    ) {
      val id = VarExp("id")
      val item = VarExp("item")
      assertEquals(
        UriTemplate(path = List(PathElm("orders"), id, item)).expandAny("id", "123"),
        UriTemplate(path = List(PathElm("orders"), PathElm("123"), item)),
      )
    }
    test("UriTemplate.expandAny should expand nothing") {
      val tpl = UriTemplate()
      assertEquals(tpl.expandAny("unknown", "123"), tpl)
    }
  }

  {
    test("UriTemplate.expandPath should expand {id} to /123") {
      val path = List(VarExp("id"))
      assertEquals(
        UriTemplate(path = path).expandPath("id", "123"),
        UriTemplate(path = List(PathElm("123"))),
      )
    }
    test("UriTemplate.expandPath should expand {+path} to /foo/bar") {
      val path = List(ReservedExp("path"))
      assertEquals(
        UriTemplate(path = path).expandPath("path", List("foo", "bar")),
        UriTemplate(path = List(PathElm("foo"), PathElm("bar"))),
      )
    }
    test("UriTemplate.expandPath should expand {/id} to /123") {
      val path = List(PathExp("id"))
      assertEquals(
        UriTemplate(path = path).expandPath("id", "123"),
        UriTemplate(path = List(PathElm("123"))),
      )
    }
    test("UriTemplate.expandPath should expand /id{/id} to /id/123") {
      val path = List(PathElm("id"), VarExp("id"))
      assertEquals(
        UriTemplate(path = path).expandPath("id", "123"),
        UriTemplate(path = List(PathElm("id"), PathElm("123"))),
      )
    }
    test(
      "UriTemplate.expandPath should expand /orders{/id,item} to UriTemplate /orders/123{/item}"
    ) {
      val id = VarExp("id")
      val item = VarExp("item")
      assertEquals(
        UriTemplate(path = List(PathElm("orders"), id, item)).expandPath("id", "123"),
        UriTemplate(path = List(PathElm("orders"), PathElm("123"), item)),
      )
    }
    test("UriTemplate.expandPath should expand nothing") {
      val path = List(PathElm("id"), VarExp("id"))
      val tpl = UriTemplate(path = path)
      assertEquals(tpl.expandPath("unknown", "123"), tpl)
    }
  }

  {
    test("UriTemplate.expandQuery should expand /{?start} to /?start=123") {
      val exp = ParamExp("start")
      val query = List(exp)
      assertEquals(
        UriTemplate(query = query).expandQuery("start", 123),
        UriTemplate(query = List(ParamElm("start", "123"))),
      )
    }
    test("UriTemplate.expandQuery should expand /{?switch} to /?switch") {
      val query = List(ParamExp("switch"))
      assertEquals(
        UriTemplate(query = query).expandQuery("switch"),
        UriTemplate(query = List(ParamElm("switch"))),
      )
    }
    test("UriTemplate.expandQuery should expand /{?term} to /?term=") {
      val query = List(ParamExp("term"))
      assertEquals(
        UriTemplate(query = query).expandQuery("term", ""),
        UriTemplate(query = List(ParamElm("term", List("")))),
      )
    }
    test("UriTemplate.expandQuery should expand /?={path} to /?=123") {
      val query = List(ParamVarExp("", "path"))
      assertEquals(
        UriTemplate(query = query).expandQuery("path", 123L),
        UriTemplate(query = List(ParamElm("", "123"))),
      )
    }
    test("UriTemplate.expandQuery should expand /?={path} to /?=25.1&=56.9") {
      val query = List(ParamVarExp("", "path"))
      assertEquals(
        UriTemplate(query = query).expandQuery("path", List(25.1, 56.9)),
        UriTemplate(query = List(ParamElm("", List("25.1", "56.9")))),
      )
    }
    test("UriTemplate.expandQuery should expand /orders{?start} to /orders?start=123&start=456") {
      val exp = ParamExp("start")
      val query = List(exp)
      val path = List(PathElm("orders"))
      assertEquals(
        UriTemplate(path = path, query = query).expandQuery("start", List("123", "456")),
        UriTemplate(path = path, query = List(ParamElm("start", List("123", "456")))),
      )
    }
    test(
      "UriTemplate.expandQuery should expand /orders{?start,limit} to UriTemplate /orders?start=123{&limit}"
    ) {
      val start = ParamExp("start")
      val limit = ParamExp("limit")
      val path = List(PathElm("orders"))
      assertEquals(
        UriTemplate(path = path, query = List(start, limit)).expandQuery("start", List("123")),
        UriTemplate(path = path, query = List(ParamElm("start", "123"), limit)),
      )
    }
    test("UriTemplate.expandQuery should expand nothing") {
      val tpl = UriTemplate(query = List(ParamExp("some")))
      assertEquals(tpl.expandQuery("unknown"), tpl)
    }
    test("UriTemplate.expandQuery should expand using a custom encoder if defined") {
      val types = ParamExp("type")
      val path = List(PathElm("orders"))

      final case class Foo(bar: String)

      val qpe =
        new QueryParamEncoder[Foo] {
          def encode(value: Foo) = new QueryParameterValue(value.bar)
        }

      assertEquals(
        UriTemplate(path = path, query = List(types)).expandQuery("type", Foo("whee"))(qpe),
        UriTemplate(path = path, query = List(ParamElm("type", "whee"))),
      )
    }
  }

  {
    test("UriTemplate.expandFragment should expand /{#section} to /#sec2.1") {
      val fragment = List(SimpleFragmentExp("section"))
      assertEquals(
        UriTemplate(fragment = fragment).expandFragment("section", "sec2.1"),
        UriTemplate(fragment = List(FragmentElm("sec2.1"))),
      )
    }
    test("UriTemplate.expandFragment should expand /{#x,y} to /#23{#y}") {
      val tpl = UriTemplate(fragment = List(MultiFragmentExp("x", "y")))
      assertEquals(
        tpl.expandFragment("x", "23"),
        UriTemplate(fragment = List(FragmentElm("23"), MultiFragmentExp("y"))),
      )
    }
    test("UriTemplate.expandFragment should expand /{#x,y} to UriTemplate /#42{#x}") {
      val tpl = UriTemplate(fragment = List(MultiFragmentExp("x", "y")))
      assertEquals(
        tpl.expandFragment("y", "42"),
        UriTemplate(fragment = List(FragmentElm("42"), MultiFragmentExp("x"))),
      )
    }
    test("UriTemplate.expandFragment should expand /{#x,y,z} to UriTemplate /#42{#x,z}") {
      val tpl = UriTemplate(fragment = List(MultiFragmentExp("x", "y", "z")))
      assertEquals(
        tpl.expandFragment("y", "42"),
        UriTemplate(fragment = List(FragmentElm("42"), MultiFragmentExp("x", "z"))),
      )
    }
    test("UriTemplate.expandFragment should expand nothing") {
      val fragment = List(FragmentElm("sec1.2"), SimpleFragmentExp("section"))
      val tpl = UriTemplate(fragment = fragment)
      assertEquals(tpl.expandFragment("unknown", "123"), tpl)
    }
  }
}
