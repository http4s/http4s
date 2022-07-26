package org.http4s.client.middleware

import cats.effect._
import cats.implicits.catsSyntaxApplicativeId
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.Date
//import org.typelevel.ci.CIStringSyntax

//def middleware[F[_]: MonadCancelThrow](client: Client[F], history: Ref[F, List[(HttpDate, Method, Uri)], maxSize: Int): Client[F] = Client[F]{(req: Request[F]) =>
//  Resource.eval(req.headers.get[Date].fold(HttpDate.now[F])(d => d.date.pure[F]).flatMap{ date =>
//  val allTheOtherStuff
//  history.modify(l => event :: l) >>
//  dropElementsAfterN >>
//  clinet.run(req)
//  }
//  } // obviously psuedo

object History {

  // maxSize = size of list? maybe drop all elements after max size - probably this list has a maxSize - drop older stuff once max size is ht
  // do I need to make and release the Resource in addition to using it? Or is that done somewher else?
  // return a Resource of response
  // TO DO - add in maxsize
  def apply[F[_]: MonadCancelThrow: Clock](client: Client[F], history: Ref[F, List[(HttpDate, Method, Uri)]]): Client[F] = Client[F]{
    req: Request[F] =>
      Resource.eval(req.headers.get[Date].fold(HttpDate.current[F])(d => d.date.pure[F])).flatMap( date => {
        val method = req.method
        val uri = req.uri

        // history = Ref[F, List[(h, m, u)]]
        // def eval[F[_], A](fa: F[A]): Resource[F, A]
        Resource.eval(history.update(l => (date, method, uri) :: l))
        // def run(req: Request[F]): Resource[F, Response[F]]
        client.run(req)

      })
  }

}
