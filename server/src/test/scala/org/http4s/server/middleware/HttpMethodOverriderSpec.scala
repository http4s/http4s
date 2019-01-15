package org.http4s.server.middleware

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.Router
import org.http4s.server.middleware.HttpMethodOverrider.{
  HeaderOverrideStrategy,
  HttpMethodOverriderConfig,
  QueryOverrideStrategy,
  defaultConfig
}
import org.http4s.util.CaseInsensitiveString

class HttpMethodOverriderSpec extends Http4sSpec {

  private final val overrideHeader = "X-HTTP-Method-Override"
  private final val overrideParam = "_method"
  private final val varyHeader = "Vary"
  private final val customHeader = "X-Custom-Header"

  private val headerOverrideStrategy = HeaderOverrideStrategy(CaseInsensitiveString(overrideHeader))
  private val queryOverrideStrategy = QueryOverrideStrategy(overrideParam)

  private val postHeaderOverriderConfig = defaultConfig
  private val postQueryOverridersConfig =
    HttpMethodOverriderConfig(queryOverrideStrategy, List(POST))
  private val deleteHeaderOverriderConfig =
    HttpMethodOverriderConfig(headerOverrideStrategy, List(DELETE))
  private val deleteQueryOverridersConfig =
    HttpMethodOverriderConfig(queryOverrideStrategy, List(DELETE))
  private val noMethodHeaderOverriderConfig =
    HttpMethodOverriderConfig(headerOverrideStrategy, List.empty)

  private val testApp = Router("/" -> HttpRoutes.of[IO] {
    case r @ GET -> Root / "resources" / "id" =>
      Ok(responseText[IO](msg = "resource's details", r))
    case r @ PUT -> Root / "resources" / "id" =>
      Ok(responseText(msg = "resource updated", r), Header(varyHeader, customHeader))
    case r @ DELETE -> Root / "resources" / "id" =>
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
    val overriddenMethod = req.attributes.get(HttpMethodOverrider.overriddenMethodAttrKey)
    mkResponseText(msg, req.method, overriddenMethod)
  }

  "MethodOverrider middleware" should {
    "ignore method override if request method not in the overridable method list" in {
      val req = Request[IO](uri = Uri.uri("/resources/id"))
        .withMethod(GET)
        .withHeaders(Header(overrideHeader, "PUT"))
      val app = HttpMethodOverrider(testApp, noMethodHeaderOverriderConfig)

      val res = app(req)
      res must returnStatus(Status.Ok)
      res must returnBody(
        mkResponseText(msg = "resource's details", reqMethod = GET, overriddenMethod = None))
    }

    "override request method when using header method overrider strategy if override method provided" in {
      val req = Request[IO](uri = Uri.uri("/resources/id"))
        .withMethod(POST)
        .withHeaders(Header(overrideHeader, "PUT"))
      val app = HttpMethodOverrider(testApp, postHeaderOverriderConfig)

      val res = app(req)
      res must returnStatus(Status.Ok)
      res must returnBody(
        mkResponseText(msg = "resource updated", reqMethod = PUT, overriddenMethod = Some(POST)))
    }

    "not override request method when using header method overrider strategy if override method not provided" in {
      val req = Request[IO](uri = Uri.uri("/resources/id"))
        .withMethod(DELETE)
      val app = HttpMethodOverrider(testApp, deleteHeaderOverriderConfig)

      val res = app(req)
      res must returnStatus(Status.Ok)
      res must returnBody(
        mkResponseText(msg = "resource deleted", reqMethod = DELETE, overriddenMethod = None))
    }

    "override request method and store the original method if using query method overrider strategy" in {
      val req = Request[IO](uri = Uri.uri("/resources/id?_method=PUT"))
        .withMethod(POST)
      val app = HttpMethodOverrider(testApp, postQueryOverridersConfig)

      val res = app(req)
      res must returnStatus(Status.Ok)
      res must returnBody(
        mkResponseText(msg = "resource updated", reqMethod = PUT, overriddenMethod = Some(POST)))
    }

    "not override request method when using query method overrider strategy if override method not provided" in {
      val req = Request[IO](uri = Uri.uri("/resources/id"))
        .withMethod(DELETE)
      val app = HttpMethodOverrider(testApp, deleteQueryOverridersConfig)

      val res = app(req)
      res must returnStatus(Status.Ok)
      res must returnBody(
        mkResponseText(msg = "resource deleted", reqMethod = DELETE, overriddenMethod = None))
    }

    "return 404 when using header method overrider strategy if override method provided is not recognized" in {
      val req = Request[IO](uri = Uri.uri("/resources/id"))
        .withMethod(POST)
        .withHeaders(Header(overrideHeader, "INVALID"))
      val app = HttpMethodOverrider(testApp, postHeaderOverriderConfig)

      val res = app(req)
      res must returnStatus(Status.NotFound)
    }

    "return 404 when using query method overrider strategy if override method provided is not recognized" in {
      val req = Request[IO](uri = Uri.uri("/resources/id?_method=INVALID"))
        .withMethod(POST)
      val app = HttpMethodOverrider(testApp, postQueryOverridersConfig)

      val res = app(req)
      res must returnStatus(Status.NotFound)
    }

    "return 400 when using header method overrider strategy if override method provided is duped" in {
      val req = Request[IO](uri = Uri.uri("/resources/id"))
        .withMethod(POST)
        .withHeaders(Header(overrideHeader, ""))
      val app = HttpMethodOverrider(testApp, postHeaderOverriderConfig)

      val res = app(req)
      res must returnStatus(Status.BadRequest)
    }

    "return 405 when using query method overrider strategy if override method provided is duped" in {
      val req = Request[IO](uri = Uri.uri("/resources/id?_method="))
        .withMethod(POST)
      val app = HttpMethodOverrider(testApp, postQueryOverridersConfig)

      val res = app(req)
      res must returnStatus(Status.MethodNotAllowed)
    }

    "override request method when using header method overrider strategy and be case insensitive" in {
      val req = Request[IO](uri = Uri.uri("/resources/id"))
        .withMethod(POST)
        .withHeaders(Header(overrideHeader, "pUt"))
      val app = HttpMethodOverrider(testApp, postHeaderOverriderConfig)

      val res = app(req)
      res must returnStatus(Status.Ok)
      res must returnBody(
        mkResponseText(msg = "resource updated", reqMethod = PUT, overriddenMethod = Some(POST)))
    }

    "override request method when using query method overrider strategy and be case insensitive" in {
      val req = Request[IO](uri = Uri.uri("/resources/id?_method=pUt"))
        .withMethod(POST)
      val app = HttpMethodOverrider(testApp, postQueryOverridersConfig)

      val res = app(req)
      res must returnStatus(Status.Ok)
      res must returnBody(
        mkResponseText(msg = "resource updated", reqMethod = PUT, overriddenMethod = Some(POST)))
    }

    "updates vary header when using query method overrider strategy and vary header comes pre-populated" in {
      val req = Request[IO](uri = Uri.uri("/resources/id"))
        .withMethod(POST)
        .withHeaders(Header(overrideHeader, "PUT"))
      val app = HttpMethodOverrider(testApp, postHeaderOverriderConfig)

      val res = app(req)
      res must returnStatus(Status.Ok)
      res must returnBody(
        mkResponseText(msg = "resource updated", reqMethod = PUT, overriddenMethod = Some(POST)))

      res must returnValue(containsHeader(Header(varyHeader, s"$customHeader, $overrideHeader")))
    }

    "set vary header when using query method overrider strategy and vary header has not been set" in {
      val req = Request[IO](uri = Uri.uri("/resources/id"))
        .withMethod(POST)
        .withHeaders(Header(overrideHeader, "DELETE"))
      val app = HttpMethodOverrider(testApp, postHeaderOverriderConfig)

      val res = app(req)
      res must returnStatus(Status.Ok)
      res must returnBody(
        mkResponseText(msg = "resource deleted", reqMethod = DELETE, overriddenMethod = Some(POST)))

      res must returnValue(containsHeader(Header(varyHeader, s"$overrideHeader")))
    }
  }
}
