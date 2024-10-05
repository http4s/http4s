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

import cats._
import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.kernel.Temporal
import cats.implicits._
import org.http4s.Http
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.Response
import org.http4s.Status

import scala.annotation.nowarn
import scala.concurrent.duration._

/** Transform a service to reject any calls the go over a given rate.
  */
object Throttle {
  sealed abstract class TokenAvailability extends Product with Serializable

  case object TokenAvailable extends TokenAvailability

  final case class TokenUnavailable(retryAfter: Option[FiniteDuration]) extends TokenAvailability

  /** A token bucket for use with the [[Throttle]] middleware.  Consumers can take tokens which will be refilled over time.
    * Implementations are required to provide their own refill mechanism.
    *
    * Possible implementations include a remote TokenBucket service to coordinate between different application instances.
    */
  trait TokenBucket[F[_]] {
    def takeToken: F[TokenAvailability]

    def mapK[G[_]](fk: F ~> G): TokenBucket[G] = new TokenBucket.Translated[F, G](this, fk)
  }

  object TokenBucket {
    private class Translated[F[_], G[_]](t: TokenBucket[F], fk: F ~> G) extends TokenBucket[G] {
      def takeToken: G[TokenAvailability] = fk(t.takeToken)
    }

    /** Creates an in-memory [[TokenBucket]].
      *
      * @param capacity    the number of tokens the bucket can hold and starts with.
      * @param refillEvery the frequency with which to add another token if there is capacity spare.
      * @return A task to create the [[TokenBucket]].
      */
    def local[F[_]](capacity: Int, refillEvery: FiniteDuration)(implicit
        F: Temporal[F]
    ): F[TokenBucket[F]] = {
      val getNanoTime = Temporal[F].monotonic.map(_.toNanos)
      val refillEveryNanos = refillEvery.toNanos
      for {
        // make sure that refillEvery is positive
        _ <- F.raiseUnless(refillEveryNanos > 0L)(
          new IllegalArgumentException("refillEvery should be > 0 nano")
        )
        creationTime <- getNanoTime
        counter <- F.ref((capacity.toDouble, creationTime))

      } yield new TokenBucket[F] {
        override def takeToken: F[TokenAvailability] =
          for {
            values <- counter.access
            ((previousTokens, previousTime), setter) = values
            currentTime <- getNanoTime
            token <- {
              val timeDifference = currentTime - previousTime
              val tokensToAdd = timeDifference.toDouble / refillEveryNanos.toDouble
              val newTokenTotal = Math.min(previousTokens + tokensToAdd, capacity.toDouble)

              // If setter fails (yields the value false), then retry with recursive call
              if (newTokenTotal >= 1)
                setter((newTokenTotal - 1, currentTime))
                  .ifM(F.pure(TokenAvailable: TokenAvailability), takeToken)
              else {
                def unavailable: TokenAvailability = {
                  val timeToNextToken = refillEveryNanos - timeDifference
                  TokenUnavailable(timeToNextToken.nanos.some)
                }
                setter((newTokenTotal, currentTime)).ifM(F.pure(unavailable), takeToken)
              }
            }
          } yield token
      }
    }
  }

  private def createBucket[F[_]](amount: Int, per: FiniteDuration)(implicit F: Temporal[F]) = {
    val refillFrequency = per / amount.toLong
    TokenBucket.local(amount, refillFrequency)
  }

  /** Limits the supplied service to a given rate of calls using an in-memory [[TokenBucket]]
    *
    * @param amount the number of calls to the service to permit within the given time period.
    * @param per    the time period over which a given number of calls is permitted.
    * @param http   the service to transform.
    * @return a task containing the transformed service.
    */
  def apply[F[_], G[_]](amount: Int, per: FiniteDuration)(
      http: Http[F, G]
  )(implicit F: Temporal[F]): F[Http[F, G]] =
    createBucket(amount, per).map(bucket => apply(bucket, defaultResponse[G] _)(http))

  /** As [[[apply[F[_],G[_]](amount:Int,per:scala\.concurrent\.duration\.FiniteDuration* apply(amount,per)]]], but for HttpRoutes[F]
    */
  def httpRoutes[F[_]](amount: Int, per: FiniteDuration)(
      httpRoutes: HttpRoutes[F]
  )(implicit F: Temporal[F]): F[HttpRoutes[F]] =
    createBucket(amount, per).map(bucket =>
      Throttle.httpRoutes(bucket, defaultResponse[F] _)(httpRoutes)
    )

  @deprecated("Use httpApp instead.", "0.23.14")
  def httpAapp[F[_]](amount: Int, per: FiniteDuration)(httpApp: HttpApp[F])(implicit
      F: Temporal[F]
  ): F[HttpApp[F]] = this.httpApp(amount, per)(httpApp)

  /** As [[[apply[F[_],G[_]](amount:Int,per:scala\.concurrent\.duration\.FiniteDuration* apply(amount,per)]]], but for HttpApp[F]
    */
  def httpApp[F[_]](amount: Int, per: FiniteDuration)(httpApp: HttpApp[F])(implicit
      F: Temporal[F]
  ): F[HttpApp[F]] =
    apply(amount, per)(httpApp)

  def defaultResponse[F[_]](@nowarn retryAfter: Option[FiniteDuration]): Response[F] =
    Response[F](Status.TooManyRequests)

  /** Limits the supplied service using a provided [[TokenBucket]]
    *
    * @param bucket           a [[TokenBucket]] to use to track the rate of incoming requests.
    * @param throttleResponse a function that defines the response when throttled, may be supplied a suggested retry time depending on bucket implementation.
    * @param http             the service to transform.
    * @return a task containing the transformed service.
    */
  def apply[F[_], G[_]](
      bucket: TokenBucket[F],
      throttleResponse: Option[FiniteDuration] => Response[G] = defaultResponse[G] _,
  )(http: Http[F, G])(implicit F: Monad[F]): Http[F, G] =
    Kleisli { req =>
      bucket.takeToken.flatMap {
        case TokenAvailable => http(req)
        case TokenUnavailable(retryAfter) => throttleResponse(retryAfter).pure[F]
      }
    }

  /** As [[[apply[F[_],G[_]](bucket:org\.http4s\.server\.middleware\.Throttle\.TokenBucket[F],throttleResponse:Option[scala\.concurrent\.duration\.FiniteDuration]=>org\.http4s\.Response[G]* apply(bucket,throttleResponse)]]], but for HttpRoutes[F]
    */
  def httpRoutes[F[_]: Monad](
      bucket: TokenBucket[F],
      throttleResponse: Option[FiniteDuration] => Response[F] = defaultResponse[F] _,
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(bucket.mapK(OptionT.liftK), throttleResponse)(httpRoutes)

  /** As [[[apply[F[_],G[_]](bucket:org\.http4s\.server\.middleware\.Throttle\.TokenBucket[F],throttleResponse:Option[scala\.concurrent\.duration\.FiniteDuration]=>org\.http4s\.Response[G]* apply(bucket,throttleResponse)]]], but for HttpApp[F]
    */
  def httpApp[F[_]: Monad](
      bucket: TokenBucket[F],
      throttleResponse: Option[FiniteDuration] => Response[F] = defaultResponse[F] _,
  )(httpApp: HttpApp[F]): HttpApp[F] =
    apply(bucket, throttleResponse)(httpApp)
}
