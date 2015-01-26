package org.http4s
package client

import java.nio.charset.StandardCharsets

import org.http4s.util.{UrlFormCodec, UrlCodingUtils}
import org.http4s.headers.Authorization
import org.http4s.util.string._

import javax.crypto

object oauth {

  private val SHA1 = "HmacSHA1"
  private def UTF_8 = StandardCharsets.UTF_8

  val OutOfBounds = "oob"

  case class Consumer(key: String, secret: String)
  case class Token(value: String, secret: String)

  def signRequest(req: Request, consumer: Consumer, callback: Option[Uri],
                  verifier: Option[String], token: Option[Token]): Request = {
    val auth = genAuthHeader(req.method, req.uri, getUserParams(req),
                             consumer, callback, verifier, token)
    req.withHeaders(auth)
  }

  def genAuthHeader(method: Method, uri: Uri, userParams: Seq[(String,String)], consumer: Consumer,
                    callback: Option[Uri], verifier: Option[String], token: Option[Token]): Authorization = {

    val params = Seq(
      "oauth_consumer_key" -> consumer.key,
      "oauth_signature_method" -> "HMAC-SHA1",
      "oauth_timestamp" -> (System.currentTimeMillis / 1000).toString,
      "oauth_nonce" -> System.nanoTime.toString,
      "oauth_version" -> "1.0",
      "oauth_callback" -> callback.map(_.renderString).getOrElse(OutOfBounds)
    ) ++ token.map { t => "oauth_token" -> t.value } ++
      verifier.map { v => "oauth_verifier" -> v }

    val baseString = genBaseString(method, uri, params ++ userParams)
    val sig = makeSHASig(baseString, consumer, token)
    val creds = GenericCredentials("OAuth".ci, params.toMap + ("oauth_signature" -> sig))

    Authorization(creds)
  }

  private[client] def makeSHASig(baseString: String, consumer: Consumer, token: Option[Token]): String = {
    val sha1 = crypto.Mac.getInstance(SHA1)
    val key = consumer.secret.formEncode + "&" + token.map(_.secret.formEncode).getOrElse("")
    sha1.init(new crypto.spec.SecretKeySpec(bytes(key), SHA1))

    val sigBytes = sha1.doFinal(bytes(baseString))
    net.iharder.Base64.encodeBytes(sigBytes)
  }

  private[client] def genBaseString(method: Method, uri: Uri, params: Seq[(String,String)]): String = {
    val paramsStr = params.map{ case (k,v) =>
      encode(k) + "=" + encode(v)
    }.sorted.mkString("&").urlEncode

    Seq(method.name.urlEncode,
      encode(uri.copy(query = Query.empty, fragment = None).renderString),
      encode(paramsStr)
    ).mkString("&")
  }

  private def encode(str: String): String =
    UrlCodingUtils.urlEncode(str, spaceIsPlus = false, toSkip = UrlFormCodec.urlUnreserved)

  private def getUserParams(req: Request): Seq[(String, String)] = {
    val qparams = req.uri.query.map{ case (k,ov) => (k, ov.getOrElse("")) }
    val bodyParams = req.contentType.map { ct =>
      if (ct.mediaType == MediaType.`application/x-www-form-urlencoded` &&
                (req.method == Method.POST || req.method == Method.PUT) ) {
        // TODO: run run sucks.
        UrlForm.entityDecoder.decode(req).run.run.fold(_ => Map.empty, urlForm => {
          urlForm.values.toSeq.flatMap{ case (k,vs) => if (vs.isEmpty) Seq(k->"") else vs.map((k,_))}
        })
      } else Map.empty
    }.getOrElse(Map.empty)

    qparams ++ bodyParams
  }


  private def bytes(str: String) = str.getBytes(UTF_8)



}
