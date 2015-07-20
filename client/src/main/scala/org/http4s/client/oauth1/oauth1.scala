package org.http4s
package client

import java.nio.charset.StandardCharsets
import javax.crypto

import org.http4s.headers.Authorization
import org.http4s.util.string._
import org.http4s.util.{UrlCodingUtils, UrlFormCodec}

import scala.collection.mutable.ListBuffer
import scalaz.concurrent.Task

/** Basic OAuth1 message signing support
  * 
  * This feature is not considered stable.
  */
package object oauth1 {

  private val SHA1 = "HmacSHA1"
  private def UTF_8 = StandardCharsets.UTF_8
  private val OutOfBand = "oob"

  /** Sign the request with an OAuth Authorization header
    *
    * __WARNING:__ POST requests with application/x-www-form-urlencoded bodies
    *            will be entirely buffered due to signing requirements. */
  def signRequest(req: Request, consumer: Consumer, callback: Option[Uri],
                  verifier: Option[String], token: Option[Token]): Task[Request] = {
    getUserParams(req).map { case (req, params) =>
      val auth = genAuthHeader(req.method, req.uri, params, consumer, callback, verifier, token)
      req.withHeaders(auth)
    }
  }

  // Generate an authorization header with the provided user params and OAuth requirements.
  private[oauth1] def genAuthHeader(method: Method, uri: Uri, userParams: Seq[(String,String)],
                                    consumer: Consumer, callback: Option[Uri],
                                    verifier: Option[String], token: Option[Token]): Authorization = {
    val params = {
      val params = new ListBuffer[(String,String)]
      params += "oauth_consumer_key"     -> encode(consumer.key)
      params += "oauth_signature_method" -> "HMAC-SHA1"
      params += "oauth_timestamp"        -> (System.currentTimeMillis / 1000).toString
      params += "oauth_nonce"            -> System.nanoTime.toString
      params += "oauth_version"          -> "1.0"
      params += "oauth_callback"         -> callback.map(c => encode(c.renderString)).getOrElse(OutOfBand)
      token.foreach { t => params += "oauth_token" -> encode(t.value) }
      verifier.foreach { v => params += "oauth_verifier" -> encode(v) }
      params.result()
    }

    val baseString = genBaseString(method, uri, params ++ userParams.map{ case (k,v) => (encode(k), encode(v))})
    val sig = makeSHASig(baseString, consumer, token)
    val creds = GenericCredentials("OAuth".ci, params.toMap + ("oauth_signature" -> encode(sig)))

    Authorization(creds)
  }

  // baseString must already be encoded, consumer and token must not be
  private[oauth1] def makeSHASig(baseString: String, consumer: Consumer, token: Option[Token]): String = {
    val sha1 = crypto.Mac.getInstance(SHA1)
    val key = encode(consumer.secret) + "&" + token.map(t => encode(t.secret)).getOrElse("")
    sha1.init(new crypto.spec.SecretKeySpec(bytes(key), SHA1))

    val sigBytes = sha1.doFinal(bytes(baseString))
    java.util.Base64.getEncoder.encodeToString(sigBytes)
  }

  // Needs to have all params already encoded
  private[oauth1] def genBaseString(method: Method, uri: Uri, params: Seq[(String,String)]): String = {
    val paramsStr = params.map{ case (k,v) => k + "=" + v }.sorted.mkString("&")

    Seq(method.name,
      encode(uri.copy(query = Query.empty, fragment = None).renderString),
      encode(paramsStr)
    ).mkString("&")
  }

  private[oauth1] def encode(str: String): String =
    UrlCodingUtils.urlEncode(str, spaceIsPlus = false, toSkip = UrlFormCodec.urlUnreserved)

  private[oauth1] def getUserParams(req: Request): Task[(Request,Seq[(String, String)])] = {
    val qparams = req.uri.query.map{ case (k,ov) => (k, ov.getOrElse("")) }

    req.contentType match {
      case Some(t) if (req.method == Method.POST || req.method == Method.PUT) &&
                       t.mediaType == MediaType.`application/x-www-form-urlencoded` =>
        req.as[UrlForm].flatMap { urlform =>
          val bodyparams = urlform.values.toSeq
            .flatMap{ case (k,vs) => if (vs.isEmpty) Seq(k->"") else vs.map((k,_))}

          req.withBody(urlform)(UrlForm.entityEncoder(req.charset getOrElse Charset.`UTF-8`))
            .map(_ -> (qparams ++ bodyparams))
        }

      case _ => Task.now(req -> qparams)
    }
  }


  private def bytes(str: String) = str.getBytes(UTF_8)
}
