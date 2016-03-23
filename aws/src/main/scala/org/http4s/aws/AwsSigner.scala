package org.http4s
package aws

import org.http4s._
import scodec.bits._
import scalaz.concurrent.Task
import java.security.MessageDigest
import scalaz.stream._
import scodec.interop.scalaz._

object AwsSigner {
  case class Key(id: String, secret: String) {
    def bytes = ByteVector.fromBase64(secret).getOrElse(throw new Exception(s"'$secret' not base64"))
  }
}

class AwsSigner(key: AwsSigner.Key, zone: String, service: String) {
  val Method = "AWS4-HMAC-SHA256"
  val Charset = java.nio.charset.Charset.forName("UTF-8")

  private def dateFormat(s: String) = {
    val f = new java.text.SimpleDateFormat(s)
    f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
    f
  }

  val fullDateFormat = dateFormat("YYYYMMdd'T'HHmmss'Z'")
  val shortDateFormat = dateFormat("YYYYMMdd")

  def hash(bv: ByteVector) = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    bv.grouped(1024 * 16) foreach { chunk =>
      digest.update(chunk.toByteBuffer)
    }

    ByteVector(digest.digest)
  }

  def bytes(s: String) = ByteVector(s.getBytes(Charset))

  def hmac(key: ByteVector, data: ByteVector) = {
    val algo = "HmacSHA256"
    val hmac = javax.crypto.Mac.getInstance(algo)

    hmac.init(new javax.crypto.spec.SecretKeySpec(key.toArray, algo))
    ByteVector(hmac.doFinal(data.toArray))
  }

  def sign(string: String, date: java.util.Date) = {
    val kSecret = bytes(s"AWS4${key.secret}")
    val kDate = hmac(kSecret, bytes(shortDateFormat.format(date)))
    val kRegion = hmac(kDate, bytes(zone))
    val kService = hmac(kRegion, bytes(service))
    val kSigning = hmac(kService, bytes("aws4_request"))
    hmac(kSigning, bytes(string))
  }

  private def now = new java.util.Date

  def apply(request: Request, date: java.util.Date = now):Task[Request] = {

    request.body.runFoldMap(a => a) map { fullBody =>

      val headers = request.headers.put(
        Header("x-amz-date", fullDateFormat.format(date))
      )

      val headersToSign = headers.put(
        Header("Host", request.uri.host.map(_.toString).getOrElse(throw new Exception("need a Host")))
      ).toList sortBy { h =>
        h.name.toString.toLowerCase
      }

      val signedHeaders = headersToSign.map(header => header.name.toString.toLowerCase).mkString(";")

      val canonicalRequest = Seq(
        request.method.name,
        request.uri.path,
        request.queryString,
        headersToSign.map({ header =>
          s"${header.name.toString.toLowerCase}:${header.value.trim}\n"
        }).mkString(""),
        signedHeaders,
        hash(fullBody).toHex
      ) mkString "\n"


      val stringToSign = Seq(
        Method,
        fullDateFormat.format(date),
        shortDateFormat.format(date) + s"/$zone/$service/aws4_request",
        hash(ByteVector(canonicalRequest.getBytes(Charset))).toHex
      ) mkString "\n"

      val auth = Seq(
        "Credential" -> s"${key.id}/${shortDateFormat.format(date)}/$zone/$service/aws4_request",
        "SignedHeaders" -> signedHeaders,
        "Signature" -> sign(stringToSign, date).toHex
      ) map { case (k, v) => s"$k=$v" } mkString ", "

      request.copy(
        headers = (headers.put(Header("Authorization", s"$Method $auth"))),
        body = Process.emit(fullBody)
      )
    }

  }
}
