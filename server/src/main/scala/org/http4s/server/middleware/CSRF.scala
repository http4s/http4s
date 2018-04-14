package org.http4s
package server
package middleware

import cats.Applicative
import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import cats.syntax.all._
import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.time.Clock
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{KeyGenerator, Mac, SecretKey}
import org.http4s.headers.{Cookie => HCookie}
import org.http4s.util.{CaseInsensitiveString, encodeHex}

/** Middleware to avoid Cross-site request forgery attacks.
  * More info on CSRF at: https://www.owasp.org/index.php/Cross-Site_Request_Forgery_(CSRF)
  *
  * This middleware is modeled after the double submit cookie pattern:
  * https://www.owasp.org/index.php/Cross-Site_Request_Forgery_(CSRF)_Prevention_Cheat_Sheet#Double_Submit_Cookie
  *
  * When a user authenticates, `embedNew` is used to send a random CSRF value as a cookie.  (Alternatively,
  * an authenticating service can be wrapped in `withNewToken`).
  *
  * By default, for requests that are unsafe (PUT, POST, DELETE, PATCH), services protected by the `validated` method in the
  * middleware will check that the csrf token is present in both the header `headerName` and the cookie `cookieName`.
  * Due to the Same-Origin policy, an attacker will be unable to reproduce this value in a
  * custom header, resulting in a `401 Unauthorized` response.
  *
  * By default, requests with safe methods (such as GET, OPTIONS, HEAD) will have a new token embedded in them if there isn't one,
  * or will receive a refreshed token based off of the previous token to mitigate the BREACH vulnerability. If a request
  * contains an invalid token, regardless of whether it is a safe method, this middleware will fail it with
  * `401 Unauthorized`. In this situation, your user(s) should clear their cookies for your page, to receive a new
  * token.
  *
  * The default can be overridden by modifying the `predicate` in `validate`. It will, by default, check if the method is safe.
  * Thus, you can provide some whitelisting capability for certain kinds of requests.
  *
  * We'd like to emphasize that you please follow proper design principles in creating endpoints, as to
  * not mutate in what should otherwise be idempotent methods (i.e no dropping your DB in a GET method, or altering
  * user data). Please do not use the CSRF protection from this middleware as a safety net for bad design.
  *
  *
  * @param headerName your CSRF header name
  * @param cookieName the CSRF cookie name
  * @param key the CSRF signing key
  * @param clock clock used as a nonce
  */
final class CSRF[F[_]] private[middleware] (
    val headerName: String = "X-Csrf-Token",
    val cookieName: String = "csrf-token",
    key: SecretKey,
    clock: Clock = Clock.systemUTC())(implicit F: Sync[F]) {

  /** Sign our token using the current time in milliseconds as a nonce
    * Signing and generating a token is potentially a unsafe operation
    * if constructed with a bad key.
    */
  private[middleware] def signToken(token: String): F[String] =
    F.delay {
      val joined = token + "-" + clock.millis()
      val mac = Mac.getInstance(CSRF.SigningAlgo)
      mac.init(key)
      val out = mac.doFinal(joined.getBytes(StandardCharsets.UTF_8))
      joined + "-" + Base64.getEncoder.encodeToString(out)
    }

  /** Generate a new token **/
  private[middleware] def generateToken: F[String] =
    signToken(CSRF.genTokenString)

  /** Decode our CSRF token and extract the original token string to sign
    */
  private[middleware] def extractRaw(token: String): OptionT[F, String] =
    token.split("-") match {
      case Array(raw, nonce, signed) =>
        OptionT(F.delay {
          val mac = Mac.getInstance(CSRF.SigningAlgo)
          mac.init(key)
          val out = mac.doFinal((raw + "-" + nonce).getBytes(StandardCharsets.UTF_8))
          if (MessageDigest.isEqual(out, Base64.getDecoder.decode(signed))) {
            Some(raw)
          } else {
            None
          }
        })
      case _ =>
        OptionT.none
    }

  /** To be only used on safe methods: if the method is safe (i.e doesn't modify data),
    * embed a new token if not present, or regenerate the current one to mitigate
    * BREACH
    */
  private[middleware] def validateOrEmbed(
      r: Request[F],
      routes: HttpRoutes[F]): OptionT[F, Response[F]] =
    CSRF.cookieFromHeaders(r, cookieName) match {
      case Some(c) =>
        OptionT.liftF(
          (for {
            raw <- extractRaw(c.content)
            res <- routes(r)
            newToken <- OptionT.liftF(signToken(raw))
          } yield res.addCookie(ResponseCookie(name = cookieName, content = newToken)))
            .getOrElse(Response[F](Status.Unauthorized)))
      case None =>
        routes(r).semiflatMap(embedNew)
    }

  /** Check for CSRF validity for an unsafe action. **/
  private[middleware] def checkCSRF(r: Request[F], routes: HttpRoutes[F]): F[Response[F]] =
    (for {
      c1 <- OptionT.fromOption[F](CSRF.cookieFromHeaders(r, cookieName))
      c2 <- OptionT.fromOption[F](r.headers.get(CaseInsensitiveString(headerName)))
      raw1 <- extractRaw(c1.content)
      raw2 <- extractRaw(c2.value)
      response <- if (CSRF.isEqual(raw1, raw2)) routes(r) else OptionT.none
      newToken <- OptionT.liftF(signToken(raw1)) //Generate a new token to guard against BREACH.
    } yield response.addCookie(ResponseCookie(name = cookieName, content = newToken)))
      .getOrElse(Response[F](Status.Unauthorized))

  /** Check predicate, then apply the correct csrf policy **/
  private[middleware] def filter(
      predicate: Request[F] => Boolean,
      r: Request[F],
      routes: HttpRoutes[F]): OptionT[F, Response[F]] =
    if (predicate(r)) {
      validateOrEmbed(r, routes)
    } else {
      OptionT.liftF(checkCSRF(r, routes))
    }

  /** Constructs a middleware that will check for the csrf token
    * presence on both the proper cookie, and header values,
    * if the predicate is not satisfied
    *
    * If it is a valid token, it will then embed a new one,
    * to effectively randomize the complete token while
    * avoiding the generation of a new secure random Id, to guard
    * against [BREACH](http://breachattack.com/)
    *
    *
    */
  def validate(predicate: Request[F] => Boolean = _.method.isSafe): HttpMiddleware[F] = { routes =>
    Kleisli { r: Request[F] =>
      filter(predicate, r, routes)
    }
  }

  /** Embed a token into a response **/
  def embedNew(res: Response[F]): F[Response[F]] =
    generateToken.map(content => res.addCookie(ResponseCookie(cookieName, content)))

  /** Middleware to embed a csrf token into routes that do not have one. **/
  def withNewToken: HttpMiddleware[F] =
    _.andThen(res => OptionT.liftF(embedNew(res)))

}

object CSRF {

  /** Default method for constructing CSRF middleware **/
  def apply[F[_]: Sync](
      headerName: String = "X-Csrf-Token",
      cookieName: String = "csrf-token",
      key: SecretKey,
      clock: Clock = Clock.systemUTC()): CSRF[F] =
    new CSRF[F](headerName, cookieName, key, clock)

  /** Sugar for instantiating a middleware by generating a key **/
  def withGeneratedKey[F[_]: Sync](
      headerName: String = "X-Csrf-Token",
      cookieName: String = "csrf-token",
      clock: Clock = Clock.systemUTC()): F[CSRF[F]] =
    generateSigningKey().map(apply(headerName, cookieName, _, clock))

  /** Sugar for pre-loading a key **/
  def withKeyBytes[F[_]: Sync](
      keyBytes: Array[Byte],
      headerName: String = "X-Csrf-Token",
      cookieName: String = "csrf-token",
      clock: Clock = Clock.systemUTC()): F[CSRF[F]] =
    buildSigningKey(keyBytes).map(apply(headerName, cookieName, _, clock))

  val SigningAlgo: String = "HmacSHA1"
  val SHA1ByteLen: Int = 20
  val CSRFTokenLength: Int = 32

  /** An instance of SecureRandom to generate
    * tokens, properly seeded:
    * https://tersesystems.com/blog/2015/12/17/the-right-way-to-use-securerandom/
    */
  private val InitialSeedArraySize: Int = 20
  private val CachedRandom: SecureRandom = {
    val r = new SecureRandom()
    r.nextBytes(new Array[Byte](InitialSeedArraySize))
    r
  }

  private[middleware] def cookieFromHeaders[F[_]: Applicative](
      request: Request[F],
      cookieName: String): Option[RequestCookie] =
    HCookie
      .from(request.headers)
      .flatMap(_.values.find(_.name == cookieName))

  /** A Constant-time string equality **/
  def isEqual(s1: String, s2: String): Boolean =
    MessageDigest.isEqual(s1.getBytes(StandardCharsets.UTF_8), s2.getBytes(StandardCharsets.UTF_8))

  /** Generate an unsigned CSRF token from a `SecureRandom` **/
  private[middleware] def genTokenString: String = {
    val bytes = new Array[Byte](CSRFTokenLength)
    CachedRandom.nextBytes(bytes)
    new String(encodeHex(bytes))
  }

  /** Generate a signing Key for the CSRF token **/
  def generateSigningKey[F[_]]()(implicit F: Sync[F]): F[SecretKey] =
    F.delay(KeyGenerator.getInstance(SigningAlgo).generateKey())

  /** Build a new HMACSHA1 Key for our CSRF Middleware
    * from key bytes. This operation is unsafe, in that
    * any amount less than 20 bytes will throw an exception when loaded
    * into `Mac`, and any value above will be truncated (not good for entropy).
    *
    * Use for loading a key from a config file, after having generated
    * one safely
    *
    */
  def buildSigningKey[F[_]](array: Array[Byte])(implicit F: Sync[F]): F[SecretKey] =
    F.delay(new SecretKeySpec(array.slice(0, SHA1ByteLen), SigningAlgo))

}
