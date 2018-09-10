package org.http4s.server.middleware

import org.http4s.{Http, Response, Status}
import cats.data.Kleisli
import cats.effect.{Clock, Sync}
import cats.effect.concurrent.Ref
import scala.concurrent.duration.FiniteDuration
import cats.implicits._
import java.util.concurrent.TimeUnit.NANOSECONDS
import scala.concurrent.duration._

/**
  * Transform a service to reject any calls the go over a given rate.
  */
object Throttle {

  sealed abstract class TokenAvailability extends Product with Serializable
  case object TokenAvailable extends TokenAvailability
  final case class TokenUnavailable(retryAfter: Option[FiniteDuration]) extends TokenAvailability

  /**
    * A token bucket for use with the [[Throttle]] middleware.  Consumers can take tokens which will be refilled over time.
    * Implementations are required to provide their own refill mechanism.
    *
    * Possible implementations include a remote TokenBucket service to coordinate between different application instances.
    */
  trait TokenBucket[F[_]] {
    def takeToken: F[TokenAvailability]
  }

  object TokenBucket {

    /**
      * Creates an in-memory [[TokenBucket]].
      *
      * @param capacity the number of tokens the bucket can hold and starts with.
      * @param refillEvery the frequency with which to add another token if there is capacity spare.
      * @return A task to create the [[TokenBucket]].
      */
    def local[F[_]](capacity: Int, refillEvery: FiniteDuration)(
        implicit F: Sync[F],
        clock: Clock[F]): F[TokenBucket[F]] = {

      def getTime = clock.monotonic(NANOSECONDS)
      val bucket = getTime.flatMap(time => Ref[F].of((capacity.toDouble, time)))

      bucket.map { counter =>
        new TokenBucket[F] {
          override def takeToken: F[TokenAvailability] = {
            val attemptUpdate = counter.access.flatMap {
              case ((previousTokens, previousTime), setter) =>
                getTime.flatMap(currentTime => {
                  val timeDifference = currentTime - previousTime
                  val tokensToAdd = timeDifference.toDouble / refillEvery.toNanos.toDouble
                  val newTokenTotal = Math.min(previousTokens + tokensToAdd, capacity.toDouble)

                  val attemptSet: F[Option[TokenAvailability]] = if (newTokenTotal >= 1) {
                    setter((newTokenTotal - 1, currentTime))
                      .map(_.guard[Option].as(TokenAvailable))
                  } else {
                    val timeToNextToken = refillEvery.toNanos - timeDifference
                    val successResponse = TokenUnavailable(timeToNextToken.nanos.some)
                    setter((newTokenTotal, currentTime)).map(_.guard[Option].as(successResponse))
                  }

                  attemptSet
                })
            }

            def loop: F[TokenAvailability] = attemptUpdate.flatMap { attempt =>
              attempt.fold(loop)(token => token.pure[F])
            }
            loop
          }

        }
      }

    }

  }

  /**
    * Limits the supplied service to a given rate of calls using an in-memory [[TokenBucket]]
    *
    * @param amount the number of calls to the service to permit within the given time period.
    * @param per the time period over which a given number of calls is permitted.
    * @param http the service to transform.
    * @return a task containing the transformed service.
    */
  def apply[F[_], G[_]](amount: Int, per: FiniteDuration)(
      http: Http[F, G])(implicit F: Sync[F], timer: Clock[F]): F[Http[F, G]] = {
    val refillFrequency = per / amount.toLong
    val createBucket = TokenBucket.local(amount, refillFrequency)
    createBucket.map(bucket => apply(bucket)(http))
  }

  def defaultResponse[F[_]](retryAfter: Option[FiniteDuration]): Response[F] = {
    val _ = retryAfter
    Response[F](Status.TooManyRequests)
  }

  /**
    * Limits the supplied service using a provided [[TokenBucket]]
    *
    * @param bucket a [[TokenBucket]] to use to track the rate of incoming requests.
    * @param throttleResponse a function that defines the response when throttled, may be supplied a suggested retry time depending on bucket implementation.
    * @param http the service to transform.
    * @return a task containing the transformed service.
    */
  def apply[F[_], G[_]](
      bucket: TokenBucket[F],
      throttleResponse: Option[FiniteDuration] => Response[G] = defaultResponse[G] _)(
      http: Http[F, G])(implicit F: Sync[F]): Http[F, G] =
    Kleisli { req =>
      bucket.takeToken.flatMap {
        case TokenAvailable => http(req)
        case TokenUnavailable(retryAfter) => throttleResponse(retryAfter).pure[F]
      }
    }
}
