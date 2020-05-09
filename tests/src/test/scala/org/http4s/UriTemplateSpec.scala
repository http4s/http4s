package org.http4s

import com.rossabaker.ci.CIString
import org.http4s.Uri.{Authority, RegName, Scheme, UserInfo}
import org.http4s.UriTemplate._

class UriTemplateSpec extends Http4sSpec {
  "UriTemplate" should {
    "render /" in {
      UriTemplate().toString must equalTo("/")
      UriTemplate(path = List()).toString must equalTo("/")
    }
    "render /test" in {
      UriTemplate(path = List(PathElm("test"))).toString must equalTo("/test")
    }
    "render {rel}" in {
      UriTemplate(path = List(VarExp("rel"))).toString must equalTo("{rel}")
    }
    "render /?ref={path,name}" in {
      val query = List(ParamVarExp("ref", "path", "name"))
      UriTemplate(query = query).toString must equalTo("/?ref={path,name}")
    }
    "render /here?ref={+path,name}" in {
      val query = List(ParamReservedExp("ref", "path", "name"))
      UriTemplate(query = query).toString must equalTo("/?ref={+path,name}")
    }
    "render /here?ref={path}" in {
      val path = List(PathElm("here"))
      val query = List(ParamVarExp("ref", "path"))
      UriTemplate(path = path, query = query).toString must equalTo("/here?ref={path}")
    }
    "render /here?{ref[name]}" in {
      val path = List(PathElm("here"))
      val query = List(ParamExp("ref[name]"))
      UriTemplate(path = path, query = query).toString must equalTo("/here{?ref[name]}")
    }
    "render /here?ref={+path}" in {
      val path = List(PathElm("here"))
      val query = List(ParamReservedExp("ref", "path"))
      UriTemplate(path = path, query = query).toString must equalTo("/here?ref={+path}")
    }
    "render {somewhere}?ref={path}" in {
      val path = List(VarExp("somewhere"))
      val query = List(ParamVarExp("ref", "path"))
      UriTemplate(path = path, query = query).toString must equalTo("{somewhere}?ref={path}")
    }
    "render /?ref={file,folder}" in {
      val query = List(ParamVarExp("ref", List("file", "folder")))
      UriTemplate(query = query).toString must equalTo("/?ref={file,folder}")
    }
    "render /orders{/id}" in {
      UriTemplate(path = List(PathElm("orders"), PathExp("id"))).toString must
        equalTo("/orders{/id}")
    }
    "render /orders{/id,item}" in {
      UriTemplate(path = List(PathElm("orders"), PathExp("id", "item"))).toString must
        equalTo("/orders{/id,item}")
    }
    "render /orders{id}" in {
      UriTemplate(path = List(PathElm("orders"), VarExp("id"))).toString must
        equalTo("/orders{id}")
    }
    "render {id,item}" in {
      UriTemplate(path = List(VarExp("id", "item"))).toString must
        equalTo("{id,item}")
    }
    "render /orders{id,item}" in {
      UriTemplate(path = List(PathElm("orders"), VarExp("id", "item"))).toString must
        equalTo("/orders{id,item}")
    }
    "render /some/test{rel}" in {
      UriTemplate(path = List(PathElm("some"), PathElm("test"), VarExp("rel"))).toString must equalTo(
        "/some/test{rel}")
    }
    "render /some{rel}/test" in {
      UriTemplate(path = List(PathElm("some"), VarExp("rel"), PathElm("test"))).toString must equalTo(
        "/some{rel}/test")
    }
    "render /some{rel}/test{?id}" in {
      val path = List(PathElm("some"), VarExp("rel"), PathElm("test"))
      val query = List(ParamExp("id"))
      UriTemplate(path = path, query = query).toString must equalTo("/some{rel}/test{?id}")
    }
    "render /item{?id}{&option}" in {
      val path = List(PathElm("item"))
      val query = List(ParamExp("id"), ParamContExp("option"))
      UriTemplate(path = path, query = query).toString must equalTo("/item{?id}{&option}")
    }
    "render /items{?start,limit}" in {
      val path = List(PathElm("items"))
      val query = List(ParamExp("start", "limit"))
      UriTemplate(path = path, query = query).toString must equalTo("/items{?start,limit}")
    }
    "render /items?start=0{&limit}" in {
      val path = List(PathElm("items"))
      val query = List(ParamElm("start", "0"), ParamContExp("limit"))
      UriTemplate(path = path, query = query).toString must equalTo("/items?start=0{&limit}")
    }
    "render /?switch" in {
      UriTemplate(path = Nil, query = List(ParamElm("switch"))).toString must equalTo("/?switch")
    }
    "render /?some=" in {
      UriTemplate(query = List(ParamElm("some", ""))).toString must equalTo("/?some=")
    }
    "render /{?id}" in {
      UriTemplate(query = List(ParamExp("id"))).toString must equalTo("/{?id}")
      UriTemplate(query = List(ParamExp("id"))).toString must equalTo("/{?id}")
    }
    "render /test{?id}" in {
      val path = List(PathElm("test"))
      val query = List(ParamExp("id"))
      UriTemplate(path = path, query = query).toString must equalTo("/test{?id}")
    }
    "render /test{?start,limit}" in {
      val path = List(PathElm("test"))
      val query = List(ParamExp("start", "limit"))
      UriTemplate(path = path, query = query).toString must equalTo("/test{?start,limit}")
    }
    "render /orders?id=1{&start,limit}" in {
      val path = List(PathElm("orders"))
      val query = List(ParamElm("id", "1"), ParamContExp("start", "limit"))
      UriTemplate(path = path, query = query).toString must equalTo("/orders?id=1{&start,limit}")
    }
    "render /orders?item=2&item=4{&start,limit}" in {
      val path = List(PathElm("orders"))
      val query = List(ParamExp("start", "limit"), ParamElm("item", List("2", "4")))
      UriTemplate(path = path, query = query).toString must equalTo(
        "/orders?item=2&item=4{&start,limit}")
    }
    "render /orders?item=2&item=4{&start}{&limit}" in {
      val path = List(PathElm("orders"))
      val query = List(ParamExp("start"), ParamElm("item", List("2", "4")), ParamExp("limit"))
      UriTemplate(path = path, query = query).toString must equalTo(
        "/orders?item=2&item=4{&start}{&limit}")
    }
    "render /search?option{&term}" in {
      val path = List(PathElm("search"))
      val query = List(ParamExp("term"), ParamElm("option"))
      UriTemplate(path = path, query = query).toString must equalTo("/search?option{&term}")
    }
    "render /search?option={&term}" in {
      val path = List(PathElm("search"))
      val query = List(ParamExp("term"), ParamElm("option", ""))
      UriTemplate(path = path, query = query).toString must equalTo("/search?option={&term}")
    }
    "render /{#frg}" in {
      val fragment = List(SimpleFragmentExp("frg"))
      UriTemplate(fragment = fragment).toString must equalTo("/{#frg}")
    }
    "render /{#x,y}" in {
      val fragment = List(MultiFragmentExp("x", "y"))
      UriTemplate(fragment = fragment).toString must equalTo("/{#x,y}")
    }
    "render /#test" in {
      val fragment = List(FragmentElm("test"))
      UriTemplate(fragment = fragment).toString must equalTo("/#test")
    }
    "render {path}/search{?term}{#section}" in {
      val path = List(VarExp("path"), PathElm("search"))
      val query = List(ParamExp("term"))
      val fragment = List(SimpleFragmentExp("section"))
      UriTemplate(path = path, query = query, fragment = fragment).toString must equalTo(
        "{path}/search{?term}{#section}")
    }
    "render {+path}/search{?term}{#section}" in {
      val path = List(ReservedExp("path"), PathElm("search"))
      val query = List(ParamExp("term"))
      val fragment = List(SimpleFragmentExp("section"))
      UriTemplate(path = path, query = query, fragment = fragment).toString must equalTo(
        "{+path}/search{?term}{#section}")
    }
    "render {+var}" in {
      val path = List(ReservedExp("var"))
      UriTemplate(path = path).toString must equalTo("{+var}")
    }
    "render {+path}/here" in {
      val path = List(ReservedExp("path"), PathElm("here"))
      UriTemplate(path = path).toString must equalTo("{+path}/here")
    }
    "render /?" in {
      UriTemplate(query = List(ParamElm("", Nil)), fragment = Nil).toString must equalTo("/?")
    }
    "render /?#" in {
      val fragment = List(FragmentElm(""))
      UriTemplate(query = List(ParamElm("", Nil)), fragment = fragment).toString must equalTo("/?#")
    }
    "render /#" in {
      val fragment = List(FragmentElm(""))
      UriTemplate(query = Nil, fragment = fragment).toString must equalTo("/#")
    }
    "render http://[1ab:1ab:1ab:1ab:1ab:1ab:1ab:1ab]/foo?bar=baz" in {
      val scheme = Some(Scheme.http)
      val host = ipv6"1ab:1ab:1ab:1ab:1ab:1ab:1ab:1ab"
      val authority = Some(Authority(host = host))
      val path = List(PathElm("foo"))
      val query = List(ParamElm("bar", "baz"))
      UriTemplate(scheme, authority, path, query).toString must
        equalTo("http://[1ab:1ab:1ab:1ab:1ab:1ab:1ab:1ab]/foo?bar=baz")
    }
    "render http://www.foo.com/foo?bar=baz" in {
      val scheme = Some(Scheme.http)
      val host = RegName("www.foo.com")
      val authority = Some(Authority(host = host))
      val path = List(PathElm("foo"))
      val query = List(ParamElm("bar", "baz"))
      UriTemplate(scheme, authority, path, query).toString must
        equalTo("http://www.foo.com/foo?bar=baz")
    }
    "render http://www.foo.com:80" in {
      val scheme = Some(Scheme.http)
      val host = RegName(CIString("www.foo.com"))
      val authority = Some(Authority(host = host, port = Some(80)))
      val path = Nil
      val query = Nil
      UriTemplate(scheme, authority, path, query).toString must
        equalTo("http://www.foo.com:80")
    }
    "render http://www.foo.com" in {
      UriTemplate(Some(Scheme.http), Some(Authority(host = RegName(CIString("www.foo.com"))))).toString must
        equalTo("http://www.foo.com")
    }
    "render http://192.168.1.1" in {
      UriTemplate(Some(Scheme.http), Some(Authority(host = ipv4"192.168.1.1"))).toString must
        equalTo("http://192.168.1.1")
    }
    "render http://192.168.1.1:8080" in {
      val scheme = Some(Scheme.http)
      val host = ipv4"192.168.1.1"
      val authority = Some(Authority(host = host, port = Some(8080)))
      val query = List(ParamElm("", Nil))
      UriTemplate(scheme, authority, Nil, query).toString must equalTo("http://192.168.1.1:8080/?")
      UriTemplate(scheme, authority, Nil, Nil).toString must equalTo("http://192.168.1.1:8080")
    }
    "render http://192.168.1.1:80/c?GB=object&Class=one" in {
      val scheme = Some(Scheme.http)
      val host = ipv4"192.168.1.1"
      val authority = Some(Authority(host = host, port = Some(80)))
      val path = List(PathElm("c"))
      val query = List(ParamElm("GB", "object"), ParamElm("Class", "one"))
      UriTemplate(scheme, authority, path, query).toString must
        equalTo("http://192.168.1.1:80/c?GB=object&Class=one")
    }
    "render http://[2001:db8:85a3:8d3:1319:8a2e:370:7344]:8080" in {
      val scheme = Some(Scheme.http)
      val host = ipv6"2001:db8:85a3:8d3:1319:8a2e:370:7344"
      val authority = Some(Authority(host = host, port = Some(8080)))
      UriTemplate(scheme, authority).toString must
        equalTo("http://[2001:db8:85a3:8d3:1319:8a2e:370:7344]:8080")
    }
    "render http://[2001:db8::7]" in {
      val scheme = Some(Scheme.http)
      val host = ipv6"2001:db8::7"
      val authority = Some(Authority(host = host))
      UriTemplate(scheme, authority).toString must
        equalTo("http://[2001:db8::7]")
    }
    "render http://[2001:db8::7]/c?GB=object&Class=one" in {
      val scheme = Some(Scheme.http)
      val host = ipv6"2001:db8::7"
      val authority = Some(Authority(host = host))
      val path = List(PathElm("c"))
      val query = List(ParamElm("GB", "object"), ParamElm("Class", "one"))
      UriTemplate(scheme, authority, path, query).toString must
        equalTo("http://[2001:db8::7]/c?GB=object&Class=one")
    }
    "render http://[2001:db8:85a3:8d3:1319:8a2e:370:7344]" in {
      UriTemplate(
        Some(Scheme.http),
        Some(Authority(host = ipv6"2001:db8:85a3:8d3:1319:8a2e:370:7344"))).toString must
        equalTo("http://[2001:db8:85a3:8d3:1319:8a2e:370:7344]")
    }
    "render email address (not supported at the moment)" in {
      UriTemplate(Some(scheme"mailto"), path = List(PathElm("John.Doe@example.com"))).toString must
        equalTo("mailto:/John.Doe@example.com")
      // but should equalTo("mailto:John.Doe@example.com")
    }
    "render http://username:password@some.example.com" in {
      val scheme = Some(Scheme.http)
      val host = RegName("some.example.com")
      val authority = Some(Authority(Some(UserInfo("username", Some("password"))), host, None))
      UriTemplate(scheme, authority).toString must
        equalTo("http://username:password@some.example.com")
      UriTemplate(scheme, authority, Nil).toString must
        equalTo("http://username:password@some.example.com")
    }
    "render http://username:password@some.example.com/some/path?param1=5&param-without-value" in {
      val scheme = Some(Scheme.http)
      val host = RegName("some.example.com")
      val authority = Some(Authority(Some(UserInfo("username", Some("password"))), host, None))
      val path = List(PathElm("some"), PathElm("path"))
      val query = List(ParamElm("param1", "5"), ParamElm("param-without-value"))
      UriTemplate(scheme, authority, path, query).toString must
        equalTo("http://username:password@some.example.com/some/path?param1=5&param-without-value")
    }
  }

  "UriTemplate.toUriIfPossible" should {
    "convert / to Uri" in {
      UriTemplate().toUriIfPossible.get must equalTo(Uri())
      UriTemplate(path = Nil).toUriIfPossible.get must equalTo(Uri(path = ""))
    }
    "convert /test to Uri" in {
      UriTemplate(path = List(PathElm("test"))).toUriIfPossible.get must equalTo(
        Uri(path = "/test"))
    }
    "convert {path} to UriTemplate" in {
      val tpl = UriTemplate(path = List(VarExp("path")))
      tpl.toUriIfPossible.isFailure
    }
    "convert {+path} to UriTemplate" in {
      val tpl = UriTemplate(path = List(ReservedExp("path")))
      tpl.toUriIfPossible.isFailure
    }
    "convert {+path} to Uri" in {
      val tpl = UriTemplate(path = List(ReservedExp("path"))).expandPath("path", List("foo", "bar"))
      tpl.toUriIfPossible.get must equalTo(Uri(path = "/foo/bar"))
    }
    "convert /some/test/{rel} to UriTemplate" in {
      val tpl = UriTemplate(path = List(PathElm("some"), PathElm("test"), VarExp("rel")))
      tpl.toUriIfPossible.isFailure
    }
    "convert /some/{rel}/test to UriTemplate" in {
      val tpl = UriTemplate(path = List(PathElm("some"), VarExp("rel"), PathElm("test")))
      tpl.toUriIfPossible.isFailure
    }
    "convert /some/{rel}/test{?id} to UriTemplate" in {
      val path = List(PathElm("some"), VarExp("rel"), PathElm("test"))
      val query = List(ParamExp("id"))
      val tpl = UriTemplate(path = path, query = query)
      tpl.toUriIfPossible.isFailure
    }
    "convert ?switch to Uri" in {
      UriTemplate(query = List(ParamElm("switch"))).toUriIfPossible.get must
        equalTo(Uri(path = "", query = Query.fromString("switch")))
    }
    "convert ?switch=foo&switch=bar to Uri" in {
      UriTemplate(query = List(ParamElm("switch", List("foo", "bar")))).toUriIfPossible.get must
        equalTo(Uri(path = "", query = Query.fromString("switch=foo&switch=bar")))
    }
    "convert /{?id} to UriTemplate" in {
      val tpl = UriTemplate(query = List(ParamExp("id")))
      tpl.toUriIfPossible.isFailure
    }
    "convert /test{?id} to UriTemplate" in {
      val path = List(PathElm("test"))
      val query = List(ParamExp("id"))
      val tpl = UriTemplate(path = path, query = query)
      tpl.toUriIfPossible.isFailure
    }
    "convert /test{?start,limit} to UriTemplate" in {
      val path = List(PathElm("test"))
      val query = List(ParamExp("start"), ParamExp("limit"))
      val tpl = UriTemplate(path = path, query = query)
      tpl.toUriIfPossible.isFailure
    }
    "convert /orders?id=1{&start,limit} to UriTemplate" in {
      val path = List(PathElm("orders"))
      val query = List(ParamElm("id", "1"), ParamExp("start"), ParamExp("limit"))
      val tpl = UriTemplate(path = path, query = query)
      tpl.toUriIfPossible.isFailure
    }
    "convert /orders?item=2&item=4{&start,limit} to UriTemplate" in {
      val path = List(PathElm("orders"))
      val query = List(ParamExp("start"), ParamElm("item", List("2", "4")), ParamExp("limit"))
      val tpl = UriTemplate(path = path, query = query)
      tpl.toUriIfPossible.isFailure
    }
    "convert /search?option{&term} to UriTemplate" in {
      val path = List(PathElm("search"))
      val query = List(ParamExp("term"), ParamElm("option"))
      val tpl = UriTemplate(path = path, query = query)
      tpl.toUriIfPossible.isFailure
    }
    "convert /{#frg} to UriTemplate" in {
      val fragment = List(SimpleFragmentExp("frg"))
      val tpl = UriTemplate(fragment = fragment)
      tpl.toUriIfPossible.isFailure
    }
    "convert /{#x,y} to UriTemplate" in {
      val fragment = List(MultiFragmentExp("x", "y"))
      val tpl = UriTemplate(fragment = fragment)
      tpl.toUriIfPossible.isFailure
    }
    "convert /#test to Uri" in {
      val fragment = List(FragmentElm("test"))
      UriTemplate(fragment = fragment).toUriIfPossible.get must
        equalTo(Uri(fragment = Some("test")))
    }
    "convert {path}/search{?term}{#section} to UriTemplate" in {
      val path = List(VarExp("path"), PathElm("search"))
      val query = List(ParamExp("term"))
      val fragment = List(SimpleFragmentExp("section"))
      val tpl = UriTemplate(path = path, query = query, fragment = fragment)
      tpl.toUriIfPossible.isFailure
    }
    "convert {+path}/search{?term}{#section} to UriTemplate" in {
      val path = List(ReservedExp("path"), PathElm("search"))
      val query = List(ParamExp("term"))
      val fragment = List(SimpleFragmentExp("section"))
      val tpl = UriTemplate(path = path, query = query, fragment = fragment)
      tpl.toUriIfPossible.isFailure
    }
    "convert /? to Uri" in {
      UriTemplate(query = List(ParamElm("", Nil)), fragment = Nil).toUriIfPossible.get must
        equalTo(Uri(query = Query.fromString("")))
    }
    "convert /?# to Uri" in {
      val fragment = List(FragmentElm(""))
      UriTemplate(query = List(ParamElm("", Nil)), fragment = fragment).toUriIfPossible.get must
        equalTo(Uri(query = Query.fromString(""), fragment = Some("")))
    }
    "convert /# to Uri" in {
      val fragment = List(FragmentElm(""))
      UriTemplate(fragment = fragment).toUriIfPossible.get must
        equalTo(Uri(fragment = Some("")))
    }
    "convert http://[01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab]/{rel}/search{?term}{#section} to UriTemplate" in {
      val scheme = Some(Scheme.http)
      val host = ipv6"01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab"
      val authority = Some(Authority(host = host))
      val path = List(VarExp("rel"), PathElm("search"))
      val query = List(ParamExp("term"))
      val fragment = List(SimpleFragmentExp("section"))
      val tpl = UriTemplate(scheme, authority, path, query, fragment)
      tpl.toUriIfPossible.isFailure
    }
    "convert http://[01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab]:8080/{rel}/search{?term}{#section} to UriTemplate" in {
      val scheme = Some(Scheme.http)
      val host = ipv6"01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab"
      val authority = Some(Authority(host = host, port = Some(8080)))
      val path = List(VarExp("rel"), PathElm("search"))
      val query = List(ParamExp("term"))
      val fragment = List(SimpleFragmentExp("section"))
      val tpl = UriTemplate(scheme, authority, path, query, fragment)
      tpl.toUriIfPossible.isFailure
    }
    "convert http://[01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab]/foo?bar=baz Uri" in {
      val scheme = Some(Scheme.http)
      val host = ipv6"01ab:01ab:01ab:01ab:01ab:01ab:01ab:01ab"
      val authority = Some(Authority(host = host))
      val path = List(PathElm("foo"))
      val query = List(ParamElm("bar", List("baz")))
      UriTemplate(scheme, authority, path, query).toUriIfPossible.get must
        equalTo(Uri(scheme, authority, "/foo", Query.fromString("bar=baz")))
    }
    "convert http://www.foo.com/foo?bar=baz to Uri" in {
      val scheme = Some(Scheme.http)
      val host = RegName("www.foo.com")
      val authority = Some(Authority(host = host))
      val path = List(PathElm("foo"))
      val query = List(ParamElm("bar", "baz"))
      UriTemplate(scheme, authority, path, query).toUriIfPossible.get must
        equalTo(Uri(scheme, authority, "/foo", Query.fromString("bar=baz")))
    }
    "convert http://www.foo.com:80 to Uri" in {
      val scheme = Some(Scheme.http)
      val host = RegName(CIString("www.foo.com"))
      val authority = Some(Authority(host = host, port = Some(80)))
      val path = Nil
      UriTemplate(scheme, authority, path).toUriIfPossible.get must
        equalTo(Uri(scheme, authority, ""))
    }
    "convert http://www.foo.com to Uri" in {
      val scheme = Some(Scheme.http)
      val host = RegName(CIString("www.foo.com"))
      val authority = Some(Authority(host = host))
      UriTemplate(Some(Scheme.http), Some(Authority(host = RegName(CIString("www.foo.com"))))).toUriIfPossible.get must
        equalTo(Uri(scheme, authority))
    }
    "convert http://192.168.1.1 to Uri" in {
      val scheme = Some(Scheme.http)
      val host = ipv4"192.168.1.1"
      val authority = Some(Authority(None, host, None))
      UriTemplate(scheme, authority).toUriIfPossible.get must
        equalTo(Uri(scheme, authority))
    }
    "convert http://192.168.1.1:8080 to Uri" in {
      val scheme = Some(Scheme.http)
      val host = ipv4"192.168.1.1"
      val authority = Some(Authority(host = host, port = Some(8080)))
      val query = List(ParamElm("", Nil))
      UriTemplate(scheme, authority, Nil, query).toUriIfPossible.get must equalTo(
        Uri(scheme, authority, "", Query.fromString("")))
      UriTemplate(scheme, authority, Nil, Nil).toUriIfPossible.get must equalTo(
        Uri(scheme, authority, "", Query.empty))
    }
    "convert http://192.168.1.1:80/c?GB=object&Class=one to Uri" in {
      val scheme = Some(Scheme.http)
      val host = ipv4"192.168.1.1"
      val authority = Some(Authority(host = host, port = Some(80)))
      val path = List(PathElm("c"))
      val query = List(ParamElm("GB", "object"), ParamElm("Class", "one"))
      UriTemplate(scheme, authority, path, query).toUriIfPossible.get must
        equalTo(Uri(scheme, authority, "/c", Query.fromString("GB=object&Class=one")))
    }
    "convert http://[2001:db8::7]/c?GB=object&Class=one to Uri" in {
      val scheme = Some(Scheme.http)
      val host = ipv6"2001:db8::7"
      val authority = Some(Authority(host = host))
      val path = List(PathElm("c"))
      val query = List(ParamElm("GB", "object"), ParamElm("Class", "one"))
      UriTemplate(scheme, authority, path, query).toUriIfPossible.get must
        equalTo(Uri(scheme, authority, "/c", Query.fromString("GB=object&Class=one")))
    }
    "convert http://[2001:0db8:85a3:08d3:1319:8a2e:0370:7344] to Uri" in {
      val scheme = Some(Scheme.http)
      val host = ipv6"2001:0db8:85a3:08d3:1319:8a2e:0370:7344"
      val authority = Some(Authority(None, host, None))
      UriTemplate(scheme, authority).toUriIfPossible.get must
        equalTo(Uri(scheme, authority))
    }
    "convert http://[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]:8080 to Uri" in {
      val scheme = Some(Scheme.http)
      val host = ipv6"2001:0db8:85a3:08d3:1319:8a2e:0370:7344"
      val authority = Some(Authority(None, host, Some(8080)))
      UriTemplate(scheme, authority).toUriIfPossible.get must
        equalTo(Uri(scheme, authority))
    }
    "convert https://username:password@some.example.com to Uri" in {
      val scheme = Some(Scheme.https)
      val host = RegName("some.example.com")
      val authority = Some(Authority(Some(UserInfo("username", Some("password"))), host, None))
      UriTemplate(scheme, authority, Nil, Nil, Nil).toUriIfPossible.get must
        equalTo(Uri(scheme, authority))
    }
    "convert http://username:password@some.example.com/some/path?param1=5&param-without-value to Uri" in {
      val scheme = Some(Scheme.http)
      val host = RegName("some.example.com")
      val authority = Some(Authority(Some(UserInfo("username", Some("password"))), host, None))
      val path = List(PathElm("some"), PathElm("path"))
      val query = List(ParamElm("param1", "5"), ParamElm("param-without-value"))
      UriTemplate(scheme, authority, path, query).toUriIfPossible.get must
        equalTo(
          Uri(scheme, authority, "/some/path", Query.fromString("param1=5&param-without-value")))
    }
    "convert http://username:password@some.example.com/some/path?param1=5&param-without-value#sec-1.2 to Uri" in {
      val scheme = Some(Scheme.http)
      val host = RegName("some.example.com")
      val authority = Some(Authority(Some(UserInfo("username", Some("password"))), host, None))
      val path = List(PathElm("some"), PathElm("path"))
      val query = List(ParamElm("param1", "5"), ParamElm("param-without-value"))
      val fragment = List(FragmentElm("sec-1.2"))
      UriTemplate(scheme, authority, path, query, fragment).toUriIfPossible.get must
        equalTo(
          Uri(
            scheme,
            authority,
            "/some/path",
            Query.fromString("param1=5&param-without-value"),
            Some("sec-1.2")))
    }
  }

  "UriTemplate.expandAny" should {
    "expand {path} to /123" in {
      val path = List(VarExp("path"))
      UriTemplate(path = path).expandAny("path", "123") must
        equalTo(UriTemplate(path = List(PathElm("123"))))
    }
    "expand {+path} to /123" in {
      val path = List(ReservedExp("path"))
      UriTemplate(path = path).expandAny("path", "123") must
        equalTo(UriTemplate(path = List(PathElm("123"))))
    }
    "expand /?ref={path} to /?ref=123" in {
      val query = List(ParamVarExp("ref", "path"))
      UriTemplate(query = query).expandAny("path", "123") must
        equalTo(UriTemplate(query = List(ParamElm("ref", "123"))))
    }
    "expand /?ref={file,folder} to /?ref=123&ref={folder}" in {
      val query = List(ParamVarExp("ref", List("file", "folder")))
      UriTemplate(query = query).expandAny("file", "123") must
        equalTo(UriTemplate(query = List(ParamElm("ref", "123"), ParamVarExp("ref", "folder"))))
    }
    "expand /?ref={+path} to /?ref=123" in {
      val query = List(ParamReservedExp("ref", "path"))
      UriTemplate(query = query).expandAny("path", "123") must
        equalTo(UriTemplate(query = List(ParamElm("ref", "123"))))
    }
    "expand /?ref={+file,folder} to /?ref=123&ref={+folder}" in {
      val query = List(ParamReservedExp("ref", List("file", "folder")))
      UriTemplate(query = query).expandAny("file", "123") must
        equalTo(
          UriTemplate(query = List(ParamElm("ref", "123"), ParamReservedExp("ref", "folder"))))
    }
    "expand {/id} to /123" in {
      val path = List(PathExp("id"))
      UriTemplate(path = path).expandAny("id", "123") must
        equalTo(UriTemplate(path = List(PathElm("123"))))
    }
    "expand /id{/id} to /id/123" in {
      val path = List(PathElm("id"), VarExp("id"))
      UriTemplate(path = path).expandAny("id", "123") must
        equalTo(UriTemplate(path = List(PathElm("id"), PathElm("123"))))
    }
    "expand /orders{/id,item} to UriTemplate /orders/123{/item}" in {
      val id = VarExp("id")
      val item = VarExp("item")
      UriTemplate(path = List(PathElm("orders"), id, item)).expandAny("id", "123") must
        equalTo(UriTemplate(path = List(PathElm("orders"), PathElm("123"), item)))
    }
    "expand nothing" in {
      val tpl = UriTemplate()
      tpl.expandAny("unknown", "123") must equalTo(tpl)
    }
  }

  "UriTemplate.expandPath" should {
    "expand {id} to /123" in {
      val path = List(VarExp("id"))
      UriTemplate(path = path).expandPath("id", "123") must
        equalTo(UriTemplate(path = List(PathElm("123"))))
    }
    "expand {+path} to /foo/bar" in {
      val path = List(ReservedExp("path"))
      UriTemplate(path = path).expandPath("path", List("foo", "bar")) must
        equalTo(UriTemplate(path = List(PathElm("foo"), PathElm("bar"))))
    }
    "expand {/id} to /123" in {
      val path = List(PathExp("id"))
      UriTemplate(path = path).expandPath("id", "123") must
        equalTo(UriTemplate(path = List(PathElm("123"))))
    }
    "expand /id{/id} to /id/123" in {
      val path = List(PathElm("id"), VarExp("id"))
      UriTemplate(path = path).expandPath("id", "123") must
        equalTo(UriTemplate(path = List(PathElm("id"), PathElm("123"))))
    }
    "expand /orders{/id,item} to UriTemplate /orders/123{/item}" in {
      val id = VarExp("id")
      val item = VarExp("item")
      UriTemplate(path = List(PathElm("orders"), id, item)).expandPath("id", "123") must
        equalTo(UriTemplate(path = List(PathElm("orders"), PathElm("123"), item)))
    }
    "expand nothing" in {
      val path = List(PathElm("id"), VarExp("id"))
      val tpl = UriTemplate(path = path)
      tpl.expandPath("unknown", "123") must
        equalTo(tpl)
    }
  }

  "UriTemplate.expandQuery" should {
    "expand /{?start} to /?start=123" in {
      val exp = ParamExp("start")
      val query = List(exp)
      UriTemplate(query = query).expandQuery("start", 123) must
        equalTo(UriTemplate(query = List(ParamElm("start", "123"))))
    }
    "expand /{?switch} to /?switch" in {
      val query = List(ParamExp("switch"))
      UriTemplate(query = query).expandQuery("switch") must
        equalTo(UriTemplate(query = List(ParamElm("switch"))))
    }
    "expand /{?term} to /?term=" in {
      val query = List(ParamExp("term"))
      UriTemplate(query = query).expandQuery("term", "") must
        equalTo(UriTemplate(query = List(ParamElm("term", List("")))))
    }
    "expand /?={path} to /?=123" in {
      val query = List(ParamVarExp("", "path"))
      UriTemplate(query = query).expandQuery("path", 123L) must
        equalTo(UriTemplate(query = List(ParamElm("", "123"))))
    }
    "expand /?={path} to /?=25.1&=56.9" in {
      val query = List(ParamVarExp("", "path"))
      UriTemplate(query = query).expandQuery("path", List(25.1f, 56.9f)) must
        equalTo(UriTemplate(query = List(ParamElm("", List("25.1", "56.9")))))
    }
    "expand /orders{?start} to /orders?start=123&start=456" in {
      val exp = ParamExp("start")
      val query = List(exp)
      val path = List(PathElm("orders"))
      UriTemplate(path = path, query = query).expandQuery("start", List("123", "456")) must
        equalTo(UriTemplate(path = path, query = List(ParamElm("start", List("123", "456")))))
    }
    "expand /orders{?start,limit} to UriTemplate /orders?start=123{&limit}" in {
      val start = ParamExp("start")
      val limit = ParamExp("limit")
      val path = List(PathElm("orders"))
      UriTemplate(path = path, query = List(start, limit)).expandQuery("start", List("123")) must
        equalTo(UriTemplate(path = path, query = List(ParamElm("start", "123"), limit)))
    }
    "expand nothing" in {
      val tpl = UriTemplate(query = List(ParamExp("some")))
      tpl.expandQuery("unknown") must equalTo(tpl)
    }
    "expand using a custom encoder if defined" in {
      val types = ParamExp("type")
      val path = List(PathElm("orders"))

      final case class Foo(bar: String)

      val qpe =
        new QueryParamEncoder[Foo] {
          def encode(value: Foo) = new QueryParameterValue(value.bar)
        }

      UriTemplate(path = path, query = List(types)).expandQuery("type", Foo("whee"))(qpe) must
        equalTo(UriTemplate(path = path, query = List(ParamElm("type", "whee"))))
    }
  }

  "UriTemplate.expandFragment" should {
    "expand /{#section} to /#sec2.1" in {
      val fragment = List(SimpleFragmentExp("section"))
      UriTemplate(fragment = fragment).expandFragment("section", "sec2.1") must
        equalTo(UriTemplate(fragment = List(FragmentElm("sec2.1"))))
    }
    "expand /{#x,y} to /#23{#y}" in {
      val tpl = UriTemplate(fragment = List(MultiFragmentExp("x", "y")))
      tpl.expandFragment("x", "23") must
        equalTo(UriTemplate(fragment = List(FragmentElm("23"), MultiFragmentExp("y"))))
    }
    "expand /{#x,y} to UriTemplate /#42{#x}" in {
      val tpl = UriTemplate(fragment = List(MultiFragmentExp("x", "y")))
      tpl.expandFragment("y", "42") must
        equalTo(UriTemplate(fragment = List(FragmentElm("42"), MultiFragmentExp("x"))))
    }
    "expand /{#x,y,z} to UriTemplate /#42{#x,z}" in {
      val tpl = UriTemplate(fragment = List(MultiFragmentExp("x", "y", "z")))
      tpl.expandFragment("y", "42") must
        equalTo(UriTemplate(fragment = List(FragmentElm("42"), MultiFragmentExp("x", "z"))))
    }
    "expand nothing" in {
      val fragment = List(FragmentElm("sec1.2"), SimpleFragmentExp("section"))
      val tpl = UriTemplate(fragment = fragment)
      tpl.expandFragment("unknown", "123") must equalTo(tpl)
    }
  }
}
