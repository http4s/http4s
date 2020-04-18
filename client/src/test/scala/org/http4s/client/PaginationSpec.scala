package org.http4s.client

import cats.effect.IO
import org.http4s.client.pagination.{ClientPagination, ClientPaginationStrategy}
import org.http4s.{Headers, Http4sSpec, HttpApp, Request, Response, Uri}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Link, LinkValue}
import fs2.Stream

class PaginationSpec extends Http4sSpec with Http4sDsl[IO] {

  val maxPages = 3
  val baseUri: Uri = Uri.unsafeFromString("http://localhost/pages")

  def absoluteLinks(last: String, next: Int): Option[Link] = {
    val link = Link
      .parse(s"""<$baseUri?last=$last&n=$next>; rel="next"""")
      .fold(error => throw error, identity)
    if (next < maxPages) Some(link)
    else None
  }

  val httpApp: HttpApp[IO] = HttpApp[IO] { req =>
    val currentPage = req.params("n")
    val headers = absoluteLinks(currentPage, currentPage.toInt + 1)
      .map(Headers.of(_))
      .getOrElse(Headers.empty)
    IO.pure(Response[IO]().withHeaders(headers).withEntity(currentPage))
  }

  val client: Client[IO] =
    Client.fromHttpApp(httpApp)

  def linkHeaderStrategy: ClientPaginationStrategy[IO, LinkValue, Int] =
    ClientPaginationStrategy(
      buildLinkRequest,
      buildNextPage,
      _.bodyAsText.compile.string.map(_.toInt))

  private def buildNextPage(response: Response[IO]): Option[LinkValue] =
    response.headers.get(Link).flatMap(_.values.find(_.rel.contains("next")))

  private def buildLinkRequest(request: Request[IO])(nextLink: LinkValue): Request[IO] =
    nextLink match {
      case link if link.uri.scheme.isDefined =>
        request.withUri(link.uri)

      case link =>
        val uri = request.uri.resolve(link.uri)
        request.withUri(uri)
    }

  "Pagination" should {
    "return all pages in order" in {
      val clientPagination = new ClientPagination[IO, LinkValue, Int](client)
      val request: Request[IO] = Request().withUri(baseUri.withQueryParam("n", "0"))
      val streamOfPages: Stream[IO, Int] =
        clientPagination.paginatedWith(request)(linkHeaderStrategy)
      val pages = streamOfPages.compile.toList.unsafeRunSync()

      pages must have size maxPages
      pages must_== List(0, 1, 2)
    }
  }
}
