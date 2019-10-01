package org.http4s.client.oauth1

import cats.Show
import cats.kernel.Order
import cats.implicits._
sealed trait Header {
  val headerName: String
  val headerValue: String
  def toTuple: (String, String) =
    headerName -> encode(headerValue)
}

object Header {

  case class Consumer(override val headerValue: String, secret: String) extends Header {
    override val headerName: String = "oauth_consumer_key"
  }

  case class Token(override val headerValue: String, secret: String) extends Header {
    override val headerName: String = "oauth_token"
  }

  case class Realm(override val headerValue: String) extends Header {
    override val headerName: String = "realm"
  }

  case class Custom(headerName: String, headerValue: String) extends Header

  case class SignatureMethod(override val headerValue: String = "HMAC-SHA1") extends Header {
    override val headerName: String = "oauth_signature_method"
  }

  case class Timestamp(
      override val headerValue: String = (System.currentTimeMillis() / 1000).toString)
      extends Header {
    override val headerName: String = "oauth_timestamp"
  }

  case class Nonce(override val headerValue: String = System.nanoTime.toString) extends Header {
    override val headerName: String = "oauth_nonce"
  }

  case class Version(override val headerValue: String = "1.0") extends Header {
    override val headerName: String = "oauth_version"
  }

  case class Callback(override val headerValue: String) extends Header {
    override val headerName: String = "oauth_callback"
  }

  case class Verifier(override val headerValue: String) extends Header {
    override val headerName: String = "oauth_verifier"
  }

  implicit val sortHeaders: Order[Header] = new Order[Header] {
    override def compare(x: Header, y: Header): Int = x.headerName.compareTo(y.headerName)
  }
  val tupleShow: Show[(String, String)] = new Show[(String, String)] {
    override def show(t: (String, String)): String = s"${t._1}=${t._2}"
  }

  implicit val oauth1HeaderShow: Show[Header] = new Show[Header] {
    override def show(t: Header): String = t match {
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
