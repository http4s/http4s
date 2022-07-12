package org.http4s.client.middleware

import cats.effect._
import cats.implicits.catsSyntaxApplicativeId
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.Date
import org.typelevel.ci.CIStringSyntax

//def middleware[F[_]: MonadCancelThrow](client: Client[F], history: Ref[F, List[(HttpDate, Method, Uri)], maxSize: Int): Client[F] = Client[F]{(req: Request[F]) =>
//  Resource.eval(req.headers.get[Date].fold(HttpDate.now[F])(d => d.date.pure[F]).flatMap{ date =>
//  val allTheOtherStuff
//  history.modify(l => event :: l) >>
//  dropElementsAfterN >>
//  clinet.run(req)
//  }
//  } // obviously psuedo

object History {

  // maxSize = size of list? maybe drop all elements after max size
  def apply[F[_]: MonadCancelThrow: Clock](client: Client[F], history: Ref[F, List[(HttpDate, Method, Uri)]], maxSize: Int): Client[F] = Client[F]{
    req: Request[F] =>
      Resource.eval(req.headers.get[Date].fold(HttpDate.current[F])(d => d.date.pure[F])).flatMap( date => {
        val method = req.method
        val uri = req.uri

        // append these to the ref (hisotry)
        // what the heck is B???
        history.modify(l => (date, method, uri) :: l)

      })
  }

}
