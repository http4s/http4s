package org.http4s.client.middleware

import cats.effect._
import cats.implicits.catsSyntaxApplicativeId
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.Date

/** This Middleware provides history tracking of Uri's visited.
  * History entries are ordered most recent to oldest.
  *
  * @param client: Client[F]
  * @param history: Ref[F, Vector[HistoryEntry]
  * @param maxSize: Int
  */

class HistoryEntry private (val httpDate: HttpDate, val method: Method, val uri: Uri) {

  override def toString = s"HistoryEntry(httpDate=$httpDate, method=$method, uri=$uri)"

  def canEqual(other: Any): Boolean = other.isInstanceOf[HistoryEntry]

  override def equals(other: Any): Boolean = other match {
    case that: HistoryEntry =>
      (that canEqual this) &&
        httpDate == that.httpDate &&
        method == that.method &&
        uri == that.uri
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(httpDate, method, uri)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }

}

object HistoryEntry {
  def apply(date: HttpDate, method: Method, uri: Uri): HistoryEntry = new HistoryEntry(date, method, uri)

}

final class HistoryBuilder[F[_]: MonadCancelThrow: Clock] private (
    private val client: Client[F],
    private val history: Ref[F, Vector[HistoryEntry]],
    val maxSize: Int = 1024,
) { self =>

   private def copy(
      client: Client[F] = self.client,
      history: Ref[F, Vector[HistoryEntry]] = self.history,
      maxSize: Int,
  ): HistoryBuilder[F] = new HistoryBuilder[F](client, history, maxSize)

  def withMaxSize(size: Int): HistoryBuilder[F] = copy(maxSize = size)

  def build: Client[F] = Client[F] { req: Request[F] =>
    Resource.eval(req.headers.get[Date].fold(HttpDate.current[F])(d => d.date.pure[F])).flatMap {
      date =>
        val method = req.method
        val uri = req.uri

        Resource
          .eval(this.history.update(l => (HistoryEntry(date, method, uri) +: l).take(this.maxSize)))
          .flatMap(_ => client.run(req))

    }
  }
}

object HistoryBuilder {

  def default[F[_]: MonadCancelThrow: Clock](
      client: Client[F],
      history: Ref[F, Vector[HistoryEntry]]
  ): HistoryBuilder[F] = new HistoryBuilder[F](
    client,
    history)
}

