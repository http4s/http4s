package org.http4s.aws.middleware

import java.util.Date

import cats.data.Kleisli
import cats.effect.Sync
import cats.implicits._
import fs2.Stream
import org.http4s.client.Client
import org.http4s.{Header, Request}
import java.nio.charset.StandardCharsets

import org.http4s.internal.BytesToHex.bytesToHex

object AwsSigner {
  def apply[F[_]: Sync](id: String, secret: String, zone: String, service: String)(
      client: Client[F]): Client[F] = {
    val signer = new AwsSigner(Key(id, secret), zone, service)
    client.copy(open = Kleisli { req =>
      signer[F](req, Sync[F].delay(new Date)).flatMap(client.open(_))
    })
  }

  private final case class Key(id: String, secret: String)
}

/** Implements AWS request signing v4 as an http4s middleware.
  * Signing described here: https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html
  */
class AwsSigner(key: AwsSigner.Key, zone: String, service: String) {
  val Method = "AWS4-HMAC-SHA256"
  val Charset = StandardCharsets.UTF_8

  private def dateFormat(s: String) = {
    val f = new java.text.SimpleDateFormat(s)
    f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
    f
  }

  val fullDateFormat = dateFormat("YYYYMMdd'T'HHmmss'Z'")
  val shortDateFormat = dateFormat("YYYYMMdd")

  private def hash(bytes: Array[Byte]) = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    bytes.grouped(1024 * 16).foreach { chunk =>
      digest.update(chunk)
    }
    digest.digest
  }

  private def bytes(s: String) = s.getBytes(Charset)

  private def hmac(key: Array[Byte], data: Array[Byte]) = {
    val algo = "HmacSHA256"
    val hmac = javax.crypto.Mac.getInstance(algo)

    hmac.init(new javax.crypto.spec.SecretKeySpec(key.toArray, algo))
    hmac.doFinal(data.toArray)
  }

  private def sign(string: String, date: java.util.Date) = {
    val kSecret = bytes(s"AWS4${key.secret}")
    val kDate = hmac(kSecret, bytes(shortDateFormat.format(date)))
    val kRegion = hmac(kDate, bytes(zone))
    val kService = hmac(kRegion, bytes(service))
    val kSigning = hmac(kService, bytes("aws4_request"))
    hmac(kSigning, bytes(string))
  }

  def apply[F[_]: Sync](request: Request[F], getDate: F[Date]): F[Request[F]] =
    request.body.compile.to[Array].flatMap { fullBody =>
      getDate.map { date =>
        val amzDate = Header("x-amz-date", fullDateFormat.format(date))

        val amzHost = Header(
          "Host",
          request.uri.host
            .map(_.toString)
            .getOrElse(throw new Exception("need a Host"))) // TODO fix
        val headersToSign = request.headers
          .put(amzDate)
          .put(amzHost)
          .toList
          .sortBy { h =>
            h.name.toString.toLowerCase
          }

        val signedHeaders = headersToSign
          .map(header => header.name.toString.toLowerCase)
          .mkString(";")

        val canonicalRequest = Seq(
          request.method.name,
          request.uri.path,
          request.queryString,
          headersToSign
            .map { header =>
              s"${header.name.toString.toLowerCase}:${header.value.trim}\n"
            }
            .mkString(""),
          signedHeaders,
          bytesToHex(hash(fullBody))
        ).mkString("\n")

        val stringToSign = Seq(
          Method,
          fullDateFormat.format(date),
          shortDateFormat.format(date) + s"/$zone/$service/aws4_request",
          bytesToHex(hash(canonicalRequest.getBytes(Charset)))
        ).mkString("\n")

        val auth = Seq(
          "Credential" -> s"${key.id}/${shortDateFormat.format(date)}/$zone/$service/aws4_request",
          "SignedHeaders" -> signedHeaders,
          "Signature" -> bytesToHex(sign(stringToSign, date))
        ).map { case (k, v) => s"$k=$v" }.mkString(", ")

        request
          .putHeaders(Header("Authorization", s"$Method $auth"), amzDate, amzHost)
          .withBodyStream(Stream.emits(fullBody))
      }
    }
}
