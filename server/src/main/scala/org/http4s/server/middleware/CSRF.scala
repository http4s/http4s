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
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{KeyGenerator, Mac, SecretKey}
import org.http4s.headers.{Cookie => HCookie}
import org.http4s.headers.{Host, Origin, Referer, `X-Forwarded-For`}
import org.http4s.util.{CaseInsensitiveString, decodeHexString, encodeHexString}
import org.http4s.Uri.Scheme

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
  * custom header, resulting in a `403 Forbidden` response.
  *
  * By default, requests with safe methods (such as GET, OPTIONS, HEAD) will have a new token embedded in them if there isn't one,
  * or will receive a refreshed token based off of the previous token to mitigate the BREACH vulnerability. If a request
  * contains an invalid token, regardless of whether it is a safe method, this middleware will fail it with
  * `403 Forbidden`. In this situation, your user(s) should clear their cookies for your page, to receive a new
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
final class CSRF[F[_], G[_]] private[middleware] (
    val headerName: String = "X-Csrf-Token",
    val cookieName: String = "csrf-token",
    key: SecretKey,
    clock: Clock = Clock.systemUTC(),
    headerCheck: Request[G] => Boolean,
    onFailure: Response[G],
    secure: Boolean,
    createIfNotFound: Boolean)(implicit F: Sync[F], G: Applicative[G]) { self =>
  import CSRF._

  /** Sign our token using the current time in milliseconds as a nonce
    * Signing and generating a token is potentially a unsafe operation
    * if constructed with a bad key.
    */
  private[middleware] def signToken(rawToken: String): F[CSRFToken] =
    F.delay {
      val joined = rawToken + "-" + clock.millis()
      val mac = Mac.getInstance(CSRF.SigningAlgo)
      mac.init(key)
      val out = mac.doFinal(joined.getBytes(StandardCharsets.UTF_8))
      lift(joined + "-" + encodeHexString(out))
    }

  /** Generate a new token **/
  def generateToken: F[CSRFToken] =
    signToken(CSRF.genTokenString)

  /** Create a Response cookie from a signed CSRF token
    *
    * @param token the signed csrf token
    * @return
    */
  def createResponseCookie(token: CSRFToken): ResponseCookie =
    ResponseCookie(name = cookieName, content = token, httpOnly = true, secure = self.secure)

  /** Extract a csrftoken, if present, from the request,
    * then generate a new token signature
    * @return newly refreshed toen
    */
  def refreshedToken(r: Request[G]): OptionT[F, CSRFToken] =
    OptionT
      .fromOption[F](CSRF.cookieFromHeaders(r, cookieName))
      .flatMap(c => extractRaw(c.content))
      .semiflatMap(signToken)

  /** Decode our CSRF token and extract the original token string to sign
    */
  def extractRaw(rawToken: String): OptionT[F, String] =
    rawToken.split("-") match {
      case Array(raw, nonce, signed) =>
        val mac = Mac.getInstance(CSRF.SigningAlgo)
        mac.init(key)
        val out = mac.doFinal((raw + "-" + nonce).getBytes(StandardCharsets.UTF_8))
        decodeHexString(signed) match {
          case Some(decoded) =>
            if (MessageDigest.isEqual(out, decoded)) {
              OptionT(F.pure(Some(raw)))
            } else {
              OptionT(F.pure(None))
            }
          case None =>
            OptionT(F.pure(None))
        }
      case _ =>
        OptionT(F.pure(None))
    }

  /** To be only used on safe methods: if the method is safe (i.e doesn't modify data)
    * and a token is present, validate and regenerate it for BREACH to be impractical
    */
  private[middleware] def validate(r: Request[G], response: F[Response[G]]): F[Response[G]] =
    CSRF.cookieFromHeaders(r, cookieName) match {
      case Some(c) =>
        (for {
          raw <- extractRaw(c.content)
          res <- OptionT.liftF(response)
          newToken <- OptionT.liftF(signToken(raw))
        } yield res.addCookie(createResponseCookie(newToken)))
          .getOrElse(Response[G](Status.Forbidden))
      case None =>
        if (createIfNotFound)
          response.flatMap(embedNew)
        else response
    }

  /** Check for CSRF validity for an unsafe action.
    *
    * Exposed to users in case of manual plumbing of csrf token
    * (i.e websocket or query param)
    */
  def checkCSRFToken(r: Request[G], respAction: F[Response[G]], rawToken: String): F[Response[G]] =
    if (!headerCheck(r))
      F.pure(onFailure)
    else
      (for {
        c1 <- OptionT.fromOption[F](CSRF.cookieFromHeaders(r, cookieName))
        raw1 <- extractRaw(c1.content)
        raw2 <- extractRaw(rawToken)
        response <- if (CSRF.isEqual(raw1, raw2)) OptionT.liftF(respAction)
        else OptionT.none[F, Response[G]]
        newToken <- OptionT.liftF(signToken(raw1)) //Generate a new token to guard against BREACH.
      } yield response.addCookie(ResponseCookie(name = cookieName, content = newToken)))
        .getOrElse(onFailure)

  /** Check for CSRF validity for an unsafe action.
    *
    * Check for the default header value
    */
  def checkCSRFDefault(r: Request[G], http: F[Response[G]]): F[Response[G]] =
    r.headers.get(CaseInsensitiveString(headerName)) match {
      case Some(h) =>
        checkCSRFToken(r, http, h.value)
      case None =>
        F.pure(onFailure)
    }

  /** Check predicate, then apply the correct csrf policy **/
  def filter(
      predicate: Request[G] => Boolean = _.method.isSafe,
      r: Request[G],
      http: Http[F, G]): F[Response[G]] =
    if (predicate(r)) {
      validate(r, http.run(r))
    } else {
      checkCSRFDefault(r, http(r))
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
  def validate(predicate: Request[G] => Boolean = _.method.isSafe)
    : Middleware[F, Request[G], Response[G], Request[G], Response[G]] = { http =>
    Kleisli { r: Request[G] =>
      filter(predicate, r, http)
    }
  }

  /** Embed a token into a response **/
  def embedNew(res: Response[G]): F[Response[G]] =
    generateToken.map(content => res.addCookie(ResponseCookie(cookieName, content)))

  /** Middleware to embed a csrf token into routes that do not have one. **/
  def withNewToken: Middleware[F, Request[G], Response[G], Request[G], Response[G]] =
    _.andThen(embedNew _)

}

object CSRF {

  //Newtype hax
  type CSRFToken <: String
  private[CSRF] def lift(s: String): CSRFToken = s.asInstanceOf[CSRFToken]

  /** Default method for constructing CSRF middleware **/
  def apply[F[_]: Sync, G[_]: Applicative](
      headerName: String = "X-Csrf-Token",
      cookieName: String = "csrf-token",
      key: SecretKey,
      clock: Clock = Clock.systemUTC(),
      onFailure: Response[G] = Response[G](Status.Forbidden),
      secure: Boolean = false,
      createIfNotFound: Boolean = true,
      headerCheck: Request[G] => Boolean): CSRF[F, G] =
    new CSRF[F, G](
      headerName,
      cookieName,
      key,
      clock,
      headerCheck,
      onFailure,
      secure,
      createIfNotFound)

  /** Default method for constructing CSRF middleware **/
  def default[F[_]: Sync, G[_]: Applicative](
      headerName: String = "X-Csrf-Token",
      cookieName: String = "csrf-token",
      key: SecretKey,
      clock: Clock = Clock.systemUTC(),
      onFailure: Response[G] = Response[G](Status.Forbidden),
      host: String,
      sc: Scheme,
      port: Option[Int],
      secure: Boolean = false,
      createIfNotFound: Boolean = true): CSRF[F, G] =
    new CSRF[F, G](
      headerName,
      cookieName,
      key,
      clock,
      defaultOriginCheck(_, host, sc, port),
      onFailure,
      secure,
      createIfNotFound)

  /** Check origin matches our proposed origin.
    *
    * @param r
    * @param host
    * @param sc
    * @param port
    * @tparam F
    * @return
    */
  def defaultOriginCheck[F[_]](
      r: Request[F],
      host: String,
      sc: Scheme,
      port: Option[Int]): Boolean =
    r.headers
      .get(Origin)
      .flatMap(o => Uri.fromString(o.value).toOption)
      .exists(u => u.host.exists(_.value == host) && u.scheme.contains(sc) && u.port == port) || r.headers
      .get(Referer)
      .exists(u =>
        u.uri.host.exists(_.value == host) && u.uri.scheme.contains(sc) && u.uri.port == port)

  def proxyOriginCheck[F[_]](r: Request[F], host: Host, xff: `X-Forwarded-For`): Boolean =
    r.headers.get(Host).contains(host) || r.headers.get(`X-Forwarded-For`).contains(xff)

  /** Sugar for instantiating a middleware by generating a key **/
  def withGeneratedKey[F[_]: Sync, G[_]: Applicative](
      headerName: String = "X-Csrf-Token",
      cookieName: String = "csrf-token",
      clock: Clock = Clock.systemUTC(),
      onFailure: Response[G] = Response[G](Status.Forbidden),
      secure: Boolean = false,
      createIfNotFound: Boolean = true,
      headerCheck: Request[G] => Boolean): F[CSRF[F, G]] =
    generateSigningKey().map(
      apply(headerName, cookieName, _, clock, onFailure, secure, createIfNotFound, headerCheck))

  /** Sugar for pre-loading a key **/
  def withKeyBytes[F[_]: Sync, G[_]: Applicative](
      keyBytes: Array[Byte],
      headerName: String = "X-Csrf-Token",
      cookieName: String = "csrf-token",
      clock: Clock = Clock.systemUTC(),
      onFailure: Response[G] = Response[G](Status.Forbidden),
      secure: Boolean = false,
      createIfNotFound: Boolean = true,
      headerCheck: Request[G] => Boolean): F[CSRF[F, G]] =
    buildSigningKey(keyBytes).map(
      apply(headerName, cookieName, _, clock, onFailure, secure, createIfNotFound, headerCheck))

  val SigningAlgo: String = "HmacSHA1"
  val SHA1ByteLen: Int = 20
  val CSRFTokenLength: Int = 32

  /** An instance of SecureRandom to generate
    * tokens, properly seeded:
    * https://tersesystems.com/blog/2015/12/17/the-right-way-to-use-securerandom/
    */
  private val InitialSeedArraySize: Int = 20
  private val CachedRandom: SecureRandom = {
    //Todo: OS dependent check to use /urandom instead
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
    encodeHexString(bytes)
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
