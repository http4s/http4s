package org.http4s.websocket

import java.nio.charset.StandardCharsets._
import java.security.MessageDigest
import java.util.Base64
import scala.util.Random

private[http4s] object WebSocketHandshake {

  /** Creates a new [[ClientHandshaker]] */
  def clientHandshaker(host: String): ClientHandshaker = new ClientHandshaker(host)

  /** Provides the initial headers and a 16 byte Base64 encoded random key for websocket connections */
  class ClientHandshaker(host: String) {

    /** Randomly generated 16-byte key in Base64 encoded form */
    val key = {
      val bytes = new Array[Byte](16)
      Random.nextBytes(bytes)
      Base64.getEncoder.encodeToString(bytes)
    }

    /** Initial headers to send to the server */
    val initHeaders: List[(String, String)] =
      ("Host", host) :: ("Sec-WebSocket-Key", key) :: clientBaseHeaders

    /** Check if the server response is a websocket handshake response */
    def checkResponse(headers: Traversable[(String, String)]): Either[String, Unit] =
      if (!headers.exists {
          case (k, v) => k.equalsIgnoreCase("Connection") && valueContains("Upgrade", v)
        }) {
        Left("Bad Connection header")
      } else if (!headers.exists {
          case (k, v) => k.equalsIgnoreCase("Upgrade") && v.equalsIgnoreCase("websocket")
        }) {
        Left("Bad Upgrade header")
      } else
        headers
          .find { case (k, _) => k.equalsIgnoreCase("Sec-WebSocket-Accept") }
          .map {
            case (_, v) if genAcceptKey(key) == v => Right(())
            case (_, v) => Left(s"Invalid key: $v")
          }
          .getOrElse(Left("Missing Sec-WebSocket-Accept header"))
  }

  /** Checks the headers received from the client and if they are valid, generates response headers */
  def serverHandshake(
      headers: Traversable[(String, String)]): Either[(Int, String), Seq[(String, String)]] =
    if (!headers.exists { case (k, _) => k.equalsIgnoreCase("Host") }) {
      Left((-1, "Missing Host Header"))
    } else if (!headers.exists {
        case (k, v) => k.equalsIgnoreCase("Connection") && valueContains("Upgrade", v)
      }) {
      Left((-1, "Bad Connection header"))
    } else if (!headers.exists {
        case (k, v) => k.equalsIgnoreCase("Upgrade") && v.equalsIgnoreCase("websocket")
      }) {
      Left((-1, "Bad Upgrade header"))
    } else if (!headers.exists {
        case (k, v) => k.equalsIgnoreCase("Sec-WebSocket-Version") && valueContains("13", v)
      }) {
      Left((-1, "Bad Websocket Version header"))
    } // we are past most of the 'just need them' headers
    else
      headers
        .find {
          case (k, v) =>
            k.equalsIgnoreCase("Sec-WebSocket-Key") && decodeLen(v) == 16
        }
        .map {
          case (_, v) =>
            val respHeaders = Seq(
              ("Upgrade", "websocket"),
              ("Connection", "Upgrade"),
              ("Sec-WebSocket-Accept", genAcceptKey(v))
            )

            Right(respHeaders)
        }
        .getOrElse(Left((-1, "Bad Sec-WebSocket-Key header")))

  /** Check if the headers contain an 'Upgrade: websocket' header */
  def isWebSocketRequest(headers: Traversable[(String, String)]): Boolean =
    headers.exists {
      case (k, v) => k.equalsIgnoreCase("Upgrade") && v.equalsIgnoreCase("websocket")
    }

  private def decodeLen(key: String): Int = Base64.getDecoder.decode(key).length

  private def genAcceptKey(str: String): String = {
    val crypt = MessageDigest.getInstance("SHA-1")
    crypt.reset()
    crypt.update(str.getBytes(US_ASCII))
    crypt.update(magicString)
    val bytes = crypt.digest()
    Base64.getEncoder.encodeToString(bytes)
  }

  private[websocket] def valueContains(key: String, value: String): Boolean = {
    val parts = value.split(",").map(_.trim)
    parts.foldLeft(false)((b, s) =>
      b || {
        s.equalsIgnoreCase(key) ||
        s.length > 1 &&
        s.startsWith("\"") &&
        s.endsWith("\"") &&
        s.substring(1, s.length - 1).equalsIgnoreCase(key)
    })
  }

  private val magicString =
    "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(US_ASCII)

  private val clientBaseHeaders = List(
    ("Connection", "Upgrade"),
    ("Upgrade", "websocket"),
    ("Sec-WebSocket-Version", "13")
  )
}
