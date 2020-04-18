package org.http4s.client

import cats.implicits._
import cats.{Applicative, Functor}
import fs2.Stream
import org.http4s.{Request, Response}

object pagination {
  type PaginationStrategy[F[_], T, O] = Option[T] => F[(O, Option[T])]

  private sealed trait PageIndicator[+T]
  private case object FirstPage extends PageIndicator[Nothing]
  private case class NextPage[T](token: T) extends PageIndicator[T]
  private case object NoMorePages extends PageIndicator[Nothing]

  private def unfoldPages[F[_], T, O](
      f: PaginationStrategy[F, T, O]
  )(implicit F: Applicative[F]): Stream[F, O] = {

    def fetchPage(maybeNextPageToken: Option[T]): F[Option[(O, PageIndicator[T])]] =
      f(maybeNextPageToken).map {
        case (segment, Some(nextToken)) => Option((segment, NextPage(nextToken)))
        case (segment, None) => Option((segment, NoMorePages))
      }

    Stream.unfoldEval[F, PageIndicator[T], O](FirstPage) {
      case FirstPage => fetchPage(None)
      case NextPage(token) => fetchPage(Some(token))
      case NoMorePages => F.pure(None)
    }
  }

  class ClientPagination[F[_]: Applicative, T, O](client: Client[F]) {
    def paginatedWith(request: Request[F])(
        implicit S: ClientPaginationStrategy[F, T, O]): Stream[F, O] =
      unfoldPages(S(client)(request))
  }

  type ClientPaginationStrategy[F[_], T, O] = Client[F] => Request[F] => PaginationStrategy[F, T, O]

  object ClientPaginationStrategy {
    def apply[F[_], T, O](
        requestBuilder: Request[F] => T => Request[F],
        nextPage: Response[F] => Option[T],
        extractor: Response[F] => F[O]
    )(implicit F: Functor[F]): ClientPaginationStrategy[F, T, O] = {
      client => baseRequest => nextToken =>
        val request = nextToken.fold(baseRequest)(requestBuilder(baseRequest))
        client.fetch(request)(response => extractor(response).map((_, nextPage(response))))
    }
  }
}
