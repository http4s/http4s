package org.http4s.client.oauth1

import cats.{Functor, Show}
import cats.effect.Clock
import cats.kernel.Order
import cats.implicits._
import java.util.concurrent.TimeUnit

sealed trait ProtocolParameter {
  val headerName: String
  val headerValue: String
  def toTuple: (String, String) =
    headerName -> encode(headerValue)
}

object ProtocolParameter {
  case class Consumer(override val headerValue: String, secret: String) extends ProtocolParameter {
    override val headerName: String = "oauth_consumer_key"
  }

  case class Token(override val headerValue: String, secret: String) extends ProtocolParameter {
    override val headerName: String = "oauth_token"
  }

  case class Realm(override val headerValue: String) extends ProtocolParameter {
    override val headerName: String = "realm"
  }

  case class Custom(headerName: String, headerValue: String) extends ProtocolParameter

  case class SignatureMethod(override val headerValue: String = "HMAC-SHA1")
      extends ProtocolParameter {
    override val headerName: String = "oauth_signature_method"
  }

  case class Timestamp(override val headerValue: String) extends ProtocolParameter {
    override val headerName: String = "oauth_timestamp"
  }

  object Timestamp {
    def now[F[_]](implicit F: Functor[F], clock: Clock[F]): F[Timestamp] =
      clock.realTime(TimeUnit.SECONDS).map(seconds => Timestamp(seconds.toString))
  }

  case class Nonce(override val headerValue: String) extends ProtocolParameter {
    override val headerName: String = "oauth_nonce"
  }

  object Nonce {
    def now[F[_]](implicit F: Functor[F], clock: Clock[F]): F[Nonce] =
      clock.monotonic(TimeUnit.NANOSECONDS).map(nanos => Nonce(nanos.toString))
  }

  case class Version(override val headerValue: String = "1.0") extends ProtocolParameter {
    override val headerName: String = "oauth_version"
  }

  case class Callback(override val headerValue: String) extends ProtocolParameter {
    override val headerName: String = "oauth_callback"
  }

  case class Verifier(override val headerValue: String) extends ProtocolParameter {
    override val headerName: String = "oauth_verifier"
  }

  implicit val http4sClientOauth1SortForProtocolParameters: Order[ProtocolParameter] =
    new Order[ProtocolParameter] {
      override def compare(x: ProtocolParameter, y: ProtocolParameter): Int =
        x.headerName.compareTo(y.headerName)
    }
  private val tupleShow: Show[(String, String)] = new Show[(String, String)] {
    override def show(t: (String, String)): String = s"${t._1}=${t._2}"
  }

  implicit val oauth1HeaderShow: Show[ProtocolParameter] = new Show[ProtocolParameter] {
    override def show(t: ProtocolParameter): String = t match {
      case c: Consumer => tupleShow.contramap[Consumer](_.toTuple).show(c)
      case t: Token => tupleShow.contramap[Token](_.toTuple).show(t)
      case r: Realm => tupleShow.contramap[Realm](_.toTuple).show(r)
      case c: Custom => tupleShow.contramap[Custom](_.toTuple).show(c)
      case s: SignatureMethod => tupleShow.contramap[SignatureMethod](_.toTuple).show(s)
      case n: Nonce => tupleShow.contramap[Nonce](_.toTuple).show(n)
      case v: Version => tupleShow.contramap[Version](_.toTuple).show(v)
      case t: Timestamp => tupleShow.contramap[Timestamp](_.toTuple).show(t)
      case c: Callback => tupleShow.contramap[Callback](_.toTuple).show(c)
      case v: Verifier => tupleShow.contramap[Verifier](_.toTuple).show(v)
    }
  }
}
