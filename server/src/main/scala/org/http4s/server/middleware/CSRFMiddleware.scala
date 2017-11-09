package org.http4s.server.middleware

import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.time.Clock
import javax.crypto.{KeyGenerator, Mac, SecretKey}
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

import org.http4s.{MaybeResponse, Request, Response, Status, Cookie}
import org.http4s.server.Middleware
import org.http4s.headers.{Cookie => HCookie}
import org.http4s.util.{CaseInsensitiveString, NonEmptyList}

import scalaz.{Kleisli, OptionT}
import scalaz.concurrent.Task
import scalaz.syntax.std.boolean._

case class CSRFMiddleware(config: CSRFConfiguration,
  clock: Clock = Clock.systemUTC()) {

  /** Sign our token using the current time in milliseconds as a nonce
    * Signing and generating a token is potentially a unsafe operation
    * if constructed with a bad key.
    */
  protected[middleware] def signToken(token: String): Task[String] = {
    val joined = token + "-" + clock.millis()
    for {
      instance <- Task.delay(Mac.getInstance(CSRFMiddleware.SigningAlgo))
      _ <- Task.delay(instance.init(config.key))
      out <- Task.delay(instance.doFinal(joined.getBytes("UTF-8")))
    } yield joined + "-" + Base64.getEncoder.encodeToString(out)
  }

  /** Generate a new token **/
  protected[middleware] def generateToken: Task[String] =
    signToken(CSRFMiddleware.genTokenString)

  /** Decode our CSRF token and extract the original token string to sign
    */
  protected[middleware] def extractRaw(token: String): OptionT[Task, String] =
    token.split("-") match {
      case Array(raw, nonce, signed) =>
        val res = for {
          instance <- Task.delay(Mac.getInstance(CSRFMiddleware.SigningAlgo))
          _ <- Task.delay(instance.init(config.key))
          out <- Task.delay(
            instance.doFinal(
              (raw + "-" + nonce).getBytes(StandardCharsets.UTF_8)))
          r <- Task.point(
            MessageDigest
              .isEqual(out, Base64.getDecoder.decode(signed))
              .option(raw))
        } yield r
        OptionT(res)
      case _ =>
        OptionT.none
    }

  /** Constructs a middleware that will check for the csrf token
    * presence on both the proper cookie, and header values.
    *
    * If it is a valid token, it will then embed a new one,
    * to effectively randomize the complete token while
    * avoiding the generation of a new secure random Id, to guard
    * against [BREACH](http://breachattack.com/)
    *
    */
  def validate: Middleware[Request, MaybeResponse, Request, MaybeResponse] = {
    service =>
      Kleisli[Task, Request, MaybeResponse] { r =>
        (for {
          c1 <- CSRFMiddleware.cookieFromHeaders(r, config.cookieName)
          c2 <- OptionT(
            Task.point(r.headers.get(CaseInsensitiveString(config.headerName))))
          raw1 <- extractRaw(c1.content)
          raw2 <- extractRaw(c2.value)
          response <- if (CSRFMiddleware.isEqual(raw1, raw2)) {
            OptionT[Task, MaybeResponse](service(r).map(Some(_)))
          } else {
            OptionT.none[Task, MaybeResponse]
          }
          newToken <- OptionT[Task, String](signToken(raw1).map(Some(_)))
        } yield
          response.cata(
            _.addCookie(Cookie(config.cookieName, newToken)),
            Response(Status.NotFound)))
          .getOrElse(Response(Status.Unauthorized))
      }
  }

  /** Embed a token into a response **/
  def embedNew(res: MaybeResponse): Task[Response] =
    generateToken.map(
      content =>
        res.cata(_.addCookie(Cookie(config.cookieName, content)),
          Response(Status.NotFound)))

  /** Middleware to embed a csrf token into routes that do not have one. **/
  def withNewToken: Middleware[Request, MaybeResponse, Request, MaybeResponse] =
    _.andThen(Kleisli(embedNew))

}

object CSRFMiddleware {

  /** An instance of SecureRandom to generate
    * tokens, properly seeded:
    * https://tersesystems.com/blog/2015/12/17/the-right-way-to-use-securerandom/
    */
  private lazy val CachedRandom = {
    val r = new SecureRandom()
    r.nextBytes(new Array[Byte](20))
    r
  }

  val SigningAlgo = "HmacSHA1"
  val SHA1ByteLen = 20
  val CSRFTokenLength = 32

  /** Hex encoding digits. Adapted from apache commons Hex.encodeHex **/
  private val Digits: Array[Char] = Array('0', '1', '2', '3', '4', '5', '6',
    '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

  protected def find[A](nel: NonEmptyList[A], p: A => Boolean): Option[A] =
    if (p(nel.head)) {
      Some(nel.head)
    } else {
      nel.tail.find(p)
    }

  protected def cookieFromHeaders(request: Request,
    headerName: String): OptionT[Task, Cookie] =
    OptionT(
      Task.point(HCookie
        .from(request.headers)
        .flatMap(v => CSRFMiddleware.find[Cookie](v.values, _.name == headerName))))

  /** A Constant-time string equality **/
  def isEqual(s1: String, s2: String): Boolean =
    MessageDigest.isEqual(s1.getBytes(StandardCharsets.UTF_8),
      s2.getBytes(StandardCharsets.UTF_8))

  /** Encode a string to a Hexadecimal string representation
    * Adapted from apache commons Hex.encodeHex
    */
  protected def encodeHex(data: Array[Byte]): Array[Char] = {
    val l = data.length
    val out = new Array[Char](l << 1)
    // two characters form the hex value.
    var i = 0
    var j = 0
    while (i < l) {
      out(j) = Digits((0xF0 & data(i)) >>> 4)
      j += 1
      out(j) = Digits(0x0F & data(i))
      j += 1
      i += 1
    }
    out
  }

  /** Generate an unsigned CSRF token from a `SecureRandom` **/
  protected def genTokenString: String = {
    val bytes = new Array[Byte](CSRFTokenLength)
    CachedRandom.nextBytes(bytes)
    new String(encodeHex(bytes))
  }

  /** Generate a signing Key for the CSRF token **/
  def generateSigningKey(): SecretKey =
    KeyGenerator.getInstance(SigningAlgo).generateKey()

  /** Build a new HMACSHA1 Key for our CSRF Middleware
    * from key bytes. This operation is unsafe, in that
    * any amount less than 20 bytes will throw an exception when loaded
    * into `Mac`, and any value above will be truncated (not good for entropy).
    *
    * Use for loading a key from a config file, after having generated
    * one safely
    *
    */
  def buildSigningKey(array: Array[Byte]): Task[SecretKey] =
    Task.delay(new SecretKeySpec(array.slice(0, SHA1ByteLen), SigningAlgo))

  def unsafeBuildSigningKey(array: Array[Byte]): SecretKey =
    new SecretKeySpec(array, SigningAlgo)

}

/** A basic CSRF configuration class **/
case class CSRFConfiguration(
  headerName: String = "X-Csrf-Token",
  cookieName: String = "csrf-token",
  key: SecretKey
)