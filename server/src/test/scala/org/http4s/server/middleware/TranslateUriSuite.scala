/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.syntax.all._

class TranslateUriSuite extends Http4sSuite {
  val routes = HttpRoutes.of[IO] {
    case _ -> Root / "foo" =>
      Ok("foo")
    case r @ _ -> Root / "checkattr" =>
      val s = r.scriptName.renderString + " " + r.pathInfo.renderString
      Ok(s)
  }

  val trans1 = TranslateUri("/http4s")(routes).orNotFound
  val trans2 = TranslateUri("http4s")(routes).orNotFound

  test("match a matching request") {
    val req = Request[IO](uri = uri"/http4s/foo")
    trans1(req).map(_.status).assertEquals(Ok) *>
      trans2(req).map(_.status).assertEquals(Ok) *>
      routes.orNotFound(req).map(_.status).assertEquals(NotFound)
  }

  test("not match a request missing the prefix") {
    val req = Request[IO](uri = uri"/foo")
    trans1(req).map(_.status).assertEquals(NotFound) *>
      trans2(req).map(_.status).assertEquals(NotFound) *>
      routes.orNotFound(req).map(_.status).assertEquals(Ok)
  }

  test("not match a request with a different prefix") {
    val req = Request[IO](uri = uri"/http5s/foo")
    trans1(req).map(_.status).assertEquals(NotFound) *>
      trans2(req).map(_.status).assertEquals(NotFound) *>
      routes.orNotFound(req).map(_.status).assertEquals(NotFound)
  }

  test("split the Uri into scriptName and pathInfo") {
    val req = Request[IO](uri = uri"/http4s/checkattr")
    trans1(req)
      .map(_.status === Ok) *>
      trans1(req)
        .flatMap(_.as[String])
        .assertEquals("/http4s /checkattr")
  }

  test("do nothing for an empty or / prefix") {
    val emptyPrefix = TranslateUri("")(routes)
    val slashPrefix = TranslateUri("/")(routes)

    val req = Request[IO](uri = uri"/foo")
    emptyPrefix.orNotFound(req).map(_.status).assertEquals(Ok) *>
      slashPrefix.orNotFound(req).map(_.status).assertEquals(Ok)
  }
}
