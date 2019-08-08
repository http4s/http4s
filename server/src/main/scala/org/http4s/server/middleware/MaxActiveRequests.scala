package org.http4s.server.middleware

import cats.implicits._
import cats.data.Kleisli
import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.effect.implicits._

import org.http4s.Status
import org.http4s.Response

object MaxActiveRequests {

  def byHeaders[F[_]: Concurrent, A](
    maxActive: Long,
    defaultResp: Response[F] = Response[F](status = Status.ServiceUnavailable)
  )(http: Kleisli[F, A, Response[F]]): F[Kleisli[F, A, Response[F]]] = {
    Semaphore[F](maxActive).map{ sem => 
      Kleisli{a: A => 
        sem.tryAcquire.bracket{
          if (_) http.run(a)
          else defaultResp.pure[F]
        }{
          if (_) sem.release
          else Sync[F].unit
        }
        
      }
    }
  }

  /**
   * Caveats: Body Of the Response Must Be Run.
   */
  def byBody[F[_]: Concurrent, A](
    maxActive: Long,
    defaultResp: Response[F] = Response[F](status = Status.ServiceUnavailable)
  )(http: Kleisli[F, A, Response[F]]): F[Kleisli[F, A, Response[F]]] = {
    Semaphore[F](maxActive).map{ sem => 
      Kleisli{a: A => 
        sem.tryAcquire.bracketCase{ bool => 
          if (bool) http.run(a).map(resp => resp.copy(body = resp.body.onFinalize(sem.release)))
          else defaultResp.pure[F]
        }{
          case (bool, ExitCase.Canceled | ExitCase.Error(_)) => 
            if (bool) sem.release
            else Sync[F].unit
          case (_, ExitCase.Completed) => Sync[F].unit
        }
        
      }
    }
  }

}