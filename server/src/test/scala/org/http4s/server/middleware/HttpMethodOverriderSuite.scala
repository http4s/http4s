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

package org.http4s.server.middleware

import cats.implicits._
import cats.effect.IO
import cats.~>
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.http4s.server.Router
import org.http4s.server.middleware.HttpMethodOverrider._
import org.typelevel.ci.CIString

class HttpMethodOverriderSuite extends Http4sSuite {
  private final val overrideHeader = "X-HTTP-Method-Override"
  private final val overrideParam, overrideField: String = "_method"
  private final val varyHeader = "Vary"
  private final val customHeader = "X-Custom-Header"

  private def headerOverrideStrategy[F[_], G[_]] =
    HeaderOverrideStrategy[F, G](CIString(overrideHeader))
  private def queryOverrideStrategy[F[_], G[_]] = QueryOverrideStrategy[F, G](overrideParam)
  private val formOverrideStrategy = FormOverrideStrategy(
    overrideParam,
    new (IO ~> IO) { def apply[A](i: IO[A]): IO[A] = i }
  )

  private def postHeaderOverriderConfig[F[_], G[_]] = defaultConfig[F, G]
  private def postQueryOverriderConfig[F[_], G[_]] =
    HttpMethodOverriderConfig[F, G](queryOverrideStrategy, Set(Post))
  private val postFormOverriderConfig =
    HttpMethodOverriderConfig(formOverrideStrategy, Set(Post))
  private def deleteHeaderOverriderConfig[F[_], G[_]] =
    HttpMethodOverriderConfig[F, G](headerOverrideStrategy, Set(Delete))
  private def deleteQueryOverriderConfig[F[_], G[_]] =
    HttpMethodOverriderConfig[F, G](queryOverrideStrategy, Set(Delete))
  private val deleteFormOverriderConfig =
    HttpMethodOverriderConfig(formOverrideStrategy, Set(Delete))
  private def noMethodHeaderOverriderConfig[F[_], G[_]] =
    HttpMethodOverriderConfig[F, G](headerOverrideStrategy, Set.empty)

  private val testApp = Router("/" -> HttpRoutes.of[IO] {
    case r @ Get -> Root / "resources" / "id" =>
      Ok(responseText[IO](msg = "resource's details", r))
    case r @ Put -> Root / "resources" / "id" =>
      Ok(responseText(msg = "resource updated", r), varyHeader -> customHeader)
    case r @ Delete -> Root / "resources" / "id" =>
      Ok(responseText(msg = "resource deleted", r))
  }).orNotFound

  private def mkResponseText(
      msg: String,
      reqMethod: Method,
      overriddenMethod: Option[Method]): String =
    overriddenMethod
      .map(om => s"[$om ~> $reqMethod] => $msg")
      .getOrElse(s"[$reqMethod] => $msg")

  private def responseText[F[_]](msg: String, req: Request[F]): String = {
    val overriddenMethod = req.attributes.lookup(HttpMethodOverrider.overriddenMethodAttrKey)
    mkResponseText(msg, req.method, overriddenMethod)
  }

  test("ignore method override if request method not in the overridable method list") {
    val req = Request[IO](uri = uri"/resources/id")
      .withMethod(Get)
      .withHeaders(overrideHeader -> "PUT")
    val app = HttpMethodOverrider(testApp, noMethodHeaderOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ === mkResponseText(
            msg = "resource's details",
            reqMethod = Get,
            overriddenMethod = None) &&
            res.status === Status.Ok)
    }.assert
  }

  test(
    "override request method when using header method overrider strategy if override method provided") {
    val req = Request[IO](uri = uri"/resources/id")
      .withMethod(Post)
      .withHeaders(overrideHeader -> "PUT")
    val app = HttpMethodOverrider(testApp, postHeaderOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ === mkResponseText(
            msg = "resource updated",
            reqMethod = Put,
            overriddenMethod = Some(Post)) &&
            res.status === Status.Ok
        )
    }.assert
  }

  test(
    "not override request method when using header method overrider strategy if override method not provided") {
    val req = Request[IO](uri = uri"/resources/id")
      .withMethod(Delete)
    val app = HttpMethodOverrider(testApp, deleteHeaderOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ ===
            mkResponseText(
              msg = "resource deleted",
              reqMethod = Delete,
              overriddenMethod = None) && res.status === Status.Ok
        )
    }.assert
  }

  test(
    "override request method and store the original method when using query method overrider strategy") {
    val req = Request[IO](uri = uri"/resources/id?_method=PUT")
      .withMethod(Post)
    val app = HttpMethodOverrider(testApp, postQueryOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ === mkResponseText(
            msg = "resource updated",
            reqMethod = Put,
            overriddenMethod = Some(Post)) && res.status === Status.Ok
        )
    }.assert
  }

  test(
    "not override request method when using query method overrider strategy if override method not provided") {
    val req = Request[IO](uri = uri"/resources/id")
      .withMethod(Delete)
    val app = HttpMethodOverrider(testApp, deleteQueryOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ ===
            mkResponseText(
              msg = "resource deleted",
              reqMethod = Delete,
              overriddenMethod = None) && res.status === Status.Ok
        )
    }.assert
  }

  test(
    "override request method and store the original method when using form method overrider strategy") {
    val urlForm = UrlForm("foo" -> "bar", overrideField -> "PUT")
    val req = Request[IO](uri = uri"/resources/id")
      .withEntity(urlForm)
      .withMethod(Post)
    val app = HttpMethodOverrider(testApp, postFormOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ === mkResponseText(
            msg = "resource updated",
            reqMethod = Put,
            overriddenMethod = Some(Post)) && res.status === Status.Ok
        )
    }.assert
  }

  test(
    "not override request method when using form method overrider strategy if override method not provided") {
    val urlForm = UrlForm("foo" -> "bar")
    val req = Request[IO](uri = uri"/resources/id")
      .withEntity(urlForm)
      .withMethod(Delete)
    val app = HttpMethodOverrider(testApp, deleteFormOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ === mkResponseText(
            msg = "resource deleted",
            reqMethod = Delete,
            overriddenMethod = None) && res.status === Status.Ok
        )
    }.assert
  }

  test(
    "return 404 when using header method overrider strategy if override method provided is not recognized") {
    val req = Request[IO](uri = uri"/resources/id")
      .withMethod(Post)
      .withHeaders(overrideHeader -> "INVALID")
    val app = HttpMethodOverrider(testApp, postHeaderOverriderConfig)

    app(req).map(_.status).assertEquals(Status.NotFound)
  }

  test(
    "return 404 when using query method overrider strategy if override method provided is not recognized") {
    val req = Request[IO](uri = uri"/resources/id?_method=INVALID")
      .withMethod(Post)
    val app = HttpMethodOverrider(testApp, postQueryOverriderConfig)

    app(req).map(_.status).assertEquals(Status.NotFound)
  }

  test(
    "return 404 when using form method overrider strategy if override method provided is not recognized") {
    val urlForm = UrlForm("foo" -> "bar", overrideField -> "INVALID")
    val req = Request[IO](uri = uri"/resources/id")
      .withEntity(urlForm)
      .withMethod(Post)
    val app = HttpMethodOverrider(testApp, postFormOverriderConfig)

    app(req).map(_.status).assertEquals(Status.NotFound)
  }

  test(
    "return 400 when using header method overrider strategy if override method provided is duped") {
    val req = Request[IO](uri = uri"/resources/id")
      .withMethod(Post)
      .withHeaders(overrideHeader -> "")
    val app = HttpMethodOverrider(testApp, postHeaderOverriderConfig)

    app(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test(
    "return 400 when using query method overrider strategy if override method provided is duped") {
    val req = Request[IO](uri = uri"/resources/id?_method=")
      .withMethod(Post)
    val app = HttpMethodOverrider(testApp, postQueryOverriderConfig)

    app(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test(
    "return 400 when using form method overrider strategy if override method provided is duped") {
    val urlForm = UrlForm("foo" -> "bar", overrideField -> "")
    val req = Request[IO](uri = uri"/resources/id")
      .withEntity(urlForm)
      .withMethod(Post)
    val app = HttpMethodOverrider(testApp, postFormOverriderConfig)

    app(req).map(_.status).assertEquals(Status.BadRequest)
  }

  test(
    "override request method when using header method overrider strategy and be case insensitive") {
    val req = Request[IO](uri = uri"/resources/id")
      .withMethod(Post)
      .withHeaders(overrideHeader -> "pUt")
    val app = HttpMethodOverrider(testApp, postHeaderOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ ===
            mkResponseText(
              msg = "resource updated",
              reqMethod = Put,
              overriddenMethod = Some(Post)) &&
            res.status === (Status.Ok)
        )
    }
  }

  test(
    "override request method when using query method overrider strategy and be case insensitive") {
    val req = Request[IO](uri = uri"/resources/id?_method=pUt")
      .withMethod(Post)
    val app = HttpMethodOverrider(testApp, postQueryOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ === mkResponseText(
            msg = "resource updated",
            reqMethod = Put,
            overriddenMethod = Some(Post)) && res.status === (Status.Ok)
        )
    }
  }

  test(
    "override request method when form query method overrider strategy and be case insensitive") {
    val urlForm = UrlForm("foo" -> "bar", overrideField -> "pUt")
    val req = Request[IO](uri = uri"/resources/id")
      .withEntity(urlForm)
      .withMethod(Post)
    val app = HttpMethodOverrider(testApp, postFormOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ === mkResponseText(
            msg = "resource updated",
            reqMethod = Put,
            overriddenMethod = Some(Post)) && res.status === (Status.Ok)
        )
    }
  }

  test(
    "updates vary header when using query method overrider strategy and vary header comes pre-populated") {
    val req = Request[IO](uri = uri"/resources/id")
      .withMethod(Post)
      .withHeaders(overrideHeader -> "PUT")
    val app = HttpMethodOverrider(testApp, postHeaderOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ === mkResponseText(
            msg = "resource updated",
            reqMethod = Put,
            overriddenMethod = Some(Post)) &&
            res.status === (Status.Ok) &&
            res.headers.headers.exists(
              _ === Header.Raw(CIString(varyHeader), s"$customHeader, $overrideHeader"))
        )
    }
  }

  test(
    "set vary header when using header method overrider strategy and vary header has not been set") {
    val req = Request[IO](uri = uri"/resources/id")
      .withMethod(Post)
      .withHeaders(overrideHeader -> "DELETE")
    val app = HttpMethodOverrider(testApp, postHeaderOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ === mkResponseText(
            msg = "resource deleted",
            reqMethod = Delete,
            overriddenMethod = Some(Post)) && res.status === Status.Ok &&
            res.headers.headers.exists(_ === Header.Raw(CIString(varyHeader), s"$overrideHeader"))
        )
    }.assert
  }

  test(
    "not set vary header when using query method overrider strategy and vary header has not been set") {
    val req = Request[IO](uri = uri"/resources/id?_method=DELETE")
      .withMethod(Post)
    val app = HttpMethodOverrider(testApp, postQueryOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ ===
            mkResponseText(
              msg = "resource deleted",
              reqMethod = Delete,
              overriddenMethod = Some(Post)) && res.status === Status.Ok &&
            !res.headers.headers.exists(_.name === CIString(varyHeader))
        )
    }.assert
  }

  test(
    "not update vary header when using query method overrider strategy and vary header comes pre-populated") {
    val req = Request[IO](uri = uri"/resources/id?_method=PUT")
      .withMethod(Post)
    val app = HttpMethodOverrider(testApp, postQueryOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ ===
            mkResponseText(
              msg = "resource updated",
              reqMethod = Put,
              overriddenMethod = Some(Post)) && res.status === Status.Ok &&
            res.headers.headers.exists(_ === Header.Raw(CIString(varyHeader), s"$customHeader"))
        )
    }.assert
  }

  test(
    "not set vary header when using form method overrider strategy and vary header has not been set") {
    val urlForm = UrlForm("foo" -> "bar", overrideField -> "DELETE")
    val req = Request[IO](uri = uri"/resources/id")
      .withEntity(urlForm)
      .withMethod(Post)
    val app = HttpMethodOverrider(testApp, postFormOverriderConfig)

    app(req).flatMap { res =>
      res.as[String].map {
        _ ===
          mkResponseText(
            msg = "resource deleted",
            reqMethod = Delete,
            overriddenMethod = Some(Post)) && res.status === Status.Ok && !res.headers.headers
            .exists(_.name === CIString(varyHeader))
      }
    }.assert
  }

  test(
    "not update vary header when using form method overrider strategy and vary header comes pre-populated") {
    val urlForm = UrlForm("foo" -> "bar", overrideField -> "PUT")
    val req = Request[IO](uri = uri"/resources/id")
      .withEntity(urlForm)
      .withMethod(Post)
    val app = HttpMethodOverrider(testApp, postFormOverriderConfig)

    app(req).flatMap { res =>
      res
        .as[String]
        .map(
          _ ===
            mkResponseText(
              msg = "resource updated",
              reqMethod = Put,
              overriddenMethod = Some(Post)) && res.status === Status.Ok &&
            res.headers.headers.exists(_ === Header.Raw(CIString(varyHeader), s"$customHeader"))
        )
    }.assert

  }
}
