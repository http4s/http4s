/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package parser

import cats.data.NonEmptyList
import org.http4s.headers.Origin
import org.specs2.mutable.Specification

class OriginHeaderSpec extends Specification with Http4sSpec {
  val host1 = Origin.Host(Uri.Scheme.http, Uri.RegName("www.foo.com"), Some(12345))
  val host2 = Origin.Host(Uri.Scheme.https, ipv4"127.0.0.1", None)

  val hostString1 = "http://www.foo.com:12345"
  val hostString2 = "https://127.0.0.1"

  "Origin value method".can {
    "Render a host with a port number" in {
      val origin = Origin.HostList(NonEmptyList.of(host1))
      origin.value must be_==(hostString1)
    }

    "Render a host without a port number" in {
      val origin = Origin.HostList(NonEmptyList.of(host2))
      origin.value must be_==(hostString2)
    }

    "Render a list of multiple hosts" in {
      val origin = Origin.HostList(NonEmptyList.of(host1, host2))
      origin.value must be_==(s"$hostString1 $hostString2")
    }

    "Render an empty origin" in {
      val origin = Origin.Null
      origin.value must be_==("null")
    }
  }

  "OriginHeader parser".can {
    "Parse a host with a port number" in {
      val text = hostString1
      val origin = Origin.HostList(NonEmptyList.of(host1))
      val headers = Headers.of(Header("Origin", text))
      headers.get(Origin) must_== (Some(origin))
    }

    "Parse a host without a port number" in {
      val text = hostString2
      val origin = Origin.HostList(NonEmptyList.of(host2))
      val headers = Headers.of(Header("Origin", text))
      headers.get(Origin) must_== (Some(origin))
    }

    "Parse a list of multiple hosts" in {
      val text = s"$hostString1 $hostString2"
      val origin = Origin.HostList(NonEmptyList.of(host1, host2))
      val headers = Headers.of(Header("Origin", text))
      headers.get(Origin) must_== (Some(origin))
    }

    "Parse an empty origin" in {
      val text = ""
      val origin = Origin.Null
      val headers = Headers.of(Header("Origin", text))
      headers.get(Origin) must_== (Some(origin))
    }

    "Parse a 'null' origin" in {
      val text = "null"
      val origin = Origin.Null
      val headers = Headers.of(Header("Origin", text))
      headers.get(Origin) must_== (Some(origin))
    }
  }
}
