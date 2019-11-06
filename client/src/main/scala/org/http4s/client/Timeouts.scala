package org.http4s
package client

import cats._
import cats.data._
import cats.implicits._
import org.http4s.client.Timeout._

sealed trait Timeouts {
  def connectTimeout: ConnectTimeout
  def idleTimeout: IdleTimeout
  def requestTimeout: RequestTimeout
  def responseHeaderTimeout: ResponseHeaderTimeout
}

object Timeouts {

  implicit val showInstance: Show[Timeouts] =
    Show.fromToString[Timeouts]

  sealed trait Error extends Product with Serializable {
    def errorMessage: String
  }

  object Error {
    final case class TimeoutOrderingError(a: Timeout, b: Timeout) extends Error {
      override final lazy val errorMessage: String = {
        s"${a.show} should be < ${b.show}"
      }
    }
  }

  private[this] final case class TimeoutsImpl(
      override final val connectTimeout: ConnectTimeout,
      override final val idleTimeout: IdleTimeout,
      override final val requestTimeout: RequestTimeout,
      override final val responseHeaderTimeout: ResponseHeaderTimeout
  ) extends Timeouts {
    override final lazy val toString: String =
      s"Timeouts($connectTimeout, $idleTimeout, $requestTimeout, $responseHeaderTimeout)"
  }

  def unapply(
      value: Timeouts): Some[(ConnectTimeout, IdleTimeout, RequestTimeout, ResponseHeaderTimeout)] =
    Some(
      (value.connectTimeout, value.idleTimeout, value.requestTimeout, value.responseHeaderTimeout))

  /** Attempt to construct a [[Timeouts]] value validating that timeouts pass
    * sanity checks, e.g. responseHeaderTimeout < idleTimeout.
    */
  def apply[F[_], G[_]](
      connectTimeout: ConnectTimeout,
      idleTimeout: IdleTimeout,
      requestTimeout: RequestTimeout,
      responseHeaderTimeout: ResponseHeaderTimeout
  )(
      implicit F: ApplicativeError[F, G[Error]],
      G: Applicative[G]
  ): F[Timeouts] = {
    def checkTimeouts(a: Timeout, b: Timeout): F[Unit] =
      if (a.value.isFinite && a.value >= b.value) {
        F.raiseError[Unit](G.pure(Error.TimeoutOrderingError(a, b)))
      } else {
        F.unit
      }

    (
      checkTimeouts(responseHeaderTimeout, requestTimeout),
      checkTimeouts(responseHeaderTimeout, idleTimeout),
      checkTimeouts(requestTimeout, idleTimeout)).mapN((_: Unit, _: Unit, _: Unit) =>
      TimeoutsImpl(connectTimeout, idleTimeout, requestTimeout, responseHeaderTimeout))
  }

  /** As [[#apply]], but the result is always a [[ValidatedNec]] value. */
  def validatedNec(
      connectTimeout: ConnectTimeout,
      idleTimeout: IdleTimeout,
      requestTimeout: RequestTimeout,
      responseHeaderTimeout: ResponseHeaderTimeout
  ): ValidatedNec[Error, Timeouts] =
    this.apply[ValidatedNec[Error, ?], NonEmptyChain](
      connectTimeout,
      idleTimeout,
      requestTimeout,
      responseHeaderTimeout)

  val defaults: Timeouts =
    TimeoutsImpl(
      ConnectTimeout.default,
      IdleTimeout.default,
      RequestTimeout.default,
      ResponseHeaderTimeout.default
    )
}
