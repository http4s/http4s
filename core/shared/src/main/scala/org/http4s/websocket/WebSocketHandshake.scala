/*
 * Copyright 2013 http4s.org
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

package org.http4s.websocket

import cats.effect.SyncIO
import cats.syntax.either._
import org.http4s.crypto.Hash
import org.http4s.crypto.HashAlgorithm
import scodec.bits._

import java.util.Base64
import scala.util.Random

@deprecated(
  "Retained for binary compatibility. Side-effecting. Only used by blaze-server.",
  "0.23.13",
)
private[http4s] object WebSocketHandshake {

  /** Creates a new [[ClientHandshaker]] */
  def clientHandshaker(host: String): ClientHandshaker = new ClientHandshaker(host)

  /** Provides the initial headers and a 16 byte Base64 encoded random key for websocket connections */
  class ClientHandshaker(host: String) {

    /** Randomly generated 16-byte key in Base64 encoded form */
    val key: String = {
      val bytes = new Array[Byte](16)
      Random.nextBytes(bytes)
      Base64.getEncoder.encodeToString(bytes)
    }

    /** Initial headers to send to the server */
    val initHeaders: List[(String, String)] =
      ("Host", host) :: ("Sec-WebSocket-Key", key) :: clientBaseHeaders

    /** Check if the server response is a websocket handshake response */
    def checkResponse(headers: Iterable[(String, String)]): Either[String, Unit] =
      if (
        !headers.exists { case (k, v) =>
          k.equalsIgnoreCase("Connection") && valueContains("Upgrade", v)
        }
      )
        Left("Bad Connection header")
      else if (
        !headers.exists { case (k, v) =>
          k.equalsIgnoreCase("Upgrade") && v.equalsIgnoreCase("websocket")
        }
      )
        Left("Bad Upgrade header")
      else
        headers
          .find { case (k, _) => k.equalsIgnoreCase("Sec-WebSocket-Accept") }
          .map {
            case (_, v) if genAcceptKey(key) == v => Either.unit
            case (_, v) => Left(s"Invalid key: $v")
          }
          .getOrElse(Left("Missing Sec-WebSocket-Accept header"))
  }

  /** Checks the headers received from the client and if they are valid, generates response headers */
  def serverHandshake(
      headers: Iterable[(String, String)]
  ): Either[(Int, String), collection.Seq[(String, String)]] =
    if (!headers.exists { case (k, _) => k.equalsIgnoreCase("Host") })
      Left((-1, "Missing Host Header"))
    else if (
      !headers.exists { case (k, v) =>
        k.equalsIgnoreCase("Connection") && valueContains("Upgrade", v)
      }
    )
      Left((-1, "Bad Connection header"))
    else if (
      !headers.exists { case (k, v) =>
        k.equalsIgnoreCase("Upgrade") && v.equalsIgnoreCase("websocket")
      }
    )
      Left((-1, "Bad Upgrade header"))
    else if (
      !headers.exists { case (k, v) =>
        k.equalsIgnoreCase("Sec-WebSocket-Version") && valueContains("13", v)
      }
    )
      Left((-1, "Bad Websocket Version header"))
    // we are past most of the 'just need them' headers
    else
      headers
        .find { case (k, v) =>
          k.equalsIgnoreCase("Sec-WebSocket-Key") && decodeLen(v) == 16
        }
        .map { case (_, v) =>
          val respHeaders = collection.Seq(
            ("Upgrade", "websocket"),
            ("Connection", "Upgrade"),
            ("Sec-WebSocket-Accept", genAcceptKey(v)),
          )

          Right(respHeaders)
        }
        .getOrElse(Left((-1, "Bad Sec-WebSocket-Key header")))

  /** Check if the headers contain an 'Upgrade: websocket' header */
  def isWebSocketRequest(headers: Iterable[(String, String)]): Boolean =
    headers.exists { case (k, v) =>
      k.equalsIgnoreCase("Upgrade") && v.equalsIgnoreCase("websocket")
    }

  private def decodeLen(key: String): Int = Base64.getDecoder.decode(key).length

  private def genAcceptKey(str: String): String = (for {
    data <- SyncIO.fromEither(ByteVector.encodeAscii(str))
    digest <- Hash[SyncIO].digest(HashAlgorithm.SHA1, data ++ magicString)
  } yield digest.toBase64).unsafeRunSync()

  private[websocket] def valueContains(key: String, value: String): Boolean = {
    val parts = value.split(",").map(_.trim)
    parts.foldLeft(false)((b, s) =>
      b || {
        s.equalsIgnoreCase(key) ||
        s.length > 1 &&
        s.startsWith("\"") &&
        s.endsWith("\"") &&
        s.substring(1, s.length - 1).equalsIgnoreCase(key)
      }
    )
  }

  private val magicString =
    asciiBytes"258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

  private val clientBaseHeaders = List(
    ("Connection", "Upgrade"),
    ("Upgrade", "websocket"),
    ("Sec-WebSocket-Version", "13"),
  )
}
