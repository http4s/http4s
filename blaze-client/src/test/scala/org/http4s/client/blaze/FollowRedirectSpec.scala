package org.http4s.client
package blaze

import javax.servlet.http.{HttpServletResponse, HttpServletRequest, HttpServlet}

import org.http4s._
import org.specs2.specification.core.Fragments


class FollowRedirectSpec extends JettyScaffold("blaze-client Redirect") {

  val client = middleware.FollowRedirect(1)(defaultClient)

  override def testServlet(): HttpServlet = new HttpServlet {
    override def doGet(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
      req.getRequestURI match {
        case "/good"     => resp.getOutputStream().print("Done.")

        case "/redirect" =>
          resp.setStatus(Status.MovedPermanently.code)
          resp.addHeader("location", "/good")
          resp.getOutputStream().print("redirect")

        case "/redirectloop" =>
          resp.setStatus(Status.MovedPermanently.code)
          resp.addHeader("Location", "/redirectloop")
          resp.getOutputStream().print("redirect")
      }
    }
  }

  override protected def runAllTests(): Fragments = {
    val addr = initializeServer()

    "Honor redirect" in {
      val resp = client(getUri(s"http://localhost:${addr.getPort}/redirect")).run
      resp.status must_== Status.Ok
    }

    "Terminate redirect loop" in {
      val resp = client(getUri(s"http://localhost:${addr.getPort}/redirectloop")).run
      resp.status must_== Status.MovedPermanently
    }

    "Not redirect more than 'maxRedirects' iterations" in {
      val resp = defaultClient(getUri(s"http://localhost:${addr.getPort}/redirect")).run
      resp.status must_== Status.MovedPermanently
    }
  }

  def getUri(s: String): Uri = Uri.fromString(s).getOrElse(sys.error("Bad uri."))
}
