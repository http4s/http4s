package org.http4s.client.middleware

import cats.effect._
import cats.implicits.catsSyntaxApplicativeId
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.Date

object History {

  def apply[F[_]: MonadCancelThrow: Clock](client: Client[F], history: Ref[F, Vector[(HttpDate, Method, Uri)]], maxSize: Int): Client[F] = Client[F]{
    req: Request[F] =>
      Resource.eval(req.headers.get[Date].fold(HttpDate.current[F])(d => d.date.pure[F])).flatMap( date => {
        val method = req.method
        val uri = req.uri

        Resource.eval(history.update(l => ((date, method, uri) +: l).take(maxSize)))
        client.run(req)
      })
  }
}
