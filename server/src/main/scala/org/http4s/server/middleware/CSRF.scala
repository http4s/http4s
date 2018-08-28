package org.http4s
package server
package middleware

import cats.{Applicative}
import cats.data.{EitherT, Kleisli}
import cats.effect.Sync
import cats.syntax.all._
import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.time.Clock
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{KeyGenerator, Mac, SecretKey}
import org.http4s.headers.{Cookie => HCookie}
import org.http4s.headers.{Host, Origin, Referer, `X-Forwarded-For`}
import org.http4s.util.{CaseInsensitiveString}
import org.http4s.internal.{decodeHexString, encodeHexString}
import org.http4s.Uri.Scheme
import scala.util.control.NoStackTrace

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
    val headerName: CaseInsensitiveString,
    val cookieName: String,
    clock: Clock,
    secure: Boolean,
    onFailure: Response[G],
    createIfNotFound: Boolean,
    key: SecretKey,
    headerCheck: Request[G] => Boolean)(implicit F: Sync[F], G: Applicative[G]) { self =>
  import CSRF._

  /** Sign our token using the current time in milliseconds as a nonce
    * Signing and generating a token is potentially a unsafe operation
    * if constructed with a bad key.
    */
  def signToken[M[_]](rawToken: String)(implicit F: Sync[M]): M[CSRFToken] =
    F.delay {
      val joined = rawToken + "-" + clock.millis()
      val mac = Mac.getInstance(CSRF.SigningAlgo)
      mac.init(key)
      val out = mac.doFinal(joined.getBytes(StandardCharsets.UTF_8))
      lift(joined + "-" + encodeHexString(out))
    }

  /** Generate a new token **/
  def generateToken[M[_]](implicit F: Sync[M]): M[CSRFToken] =
    signToken[M](CSRF.genTokenString)

  /** Create a Response cookie from a signed CSRF token
    *
    * @param token the signed csrf token
    * @return
    */
  def createResponseCookie(token: CSRFToken): ResponseCookie =
    ResponseCookie(
      name = cookieName,
      content = unlift(token),
      httpOnly = true,
      secure = self.secure)

  def createRequestCookie(token: CSRFToken): RequestCookie =
    RequestCookie(name = cookieName, content = unlift(token))

  /** Extract a `CsrfToken`, if present, from the request,
    * then try to generate a new token signature, or fail with a validation error
    * @return newly refreshed token
    */
  def refreshedToken[M[_]](r: Request[G])(
      implicit F: Sync[M]): EitherT[M, CSRFCheckFailed, CSRFToken] =
    CSRF.cookieFromHeaders(r, cookieName) match {
      case Some(c) =>
        EitherT(F.pure(extractRaw(c.content)))
          .semiflatMap(signToken[M])
      case None =>
        EitherT(F.pure(Left(CSRFCheckFailed)))
    }

  /** Extract a `CsrfToken`, if present, from the request,
    * then try generate a new token signature, or fail with a validation error.
    * If not present, generate a new token
    * @return newly refreshed token
    */
  def refreshOrCreate[M[_]](r: Request[G])(
      implicit F: Sync[M]): EitherT[M, CSRFCheckFailed, CSRFToken] =
    CSRF.cookieFromHeaders(r, cookieName) match {
      case Some(c) =>
        EitherT(F.pure(extractRaw(c.content)))
          .semiflatMap(signToken[M])
      case None =>
        EitherT.liftF(generateToken[M])
    }

  /** Decode our CSRF token, check the signature
    * and extract the original token string to sign
    */
  def extractRaw(rawToken: String): Either[CSRFCheckFailed, String] =
    rawToken.split("-") match {
      case Array(raw, nonce, signed) =>
        val mac = Mac.getInstance(CSRF.SigningAlgo)
        mac.init(key)
        val out = mac.doFinal((raw + "-" + nonce).getBytes(StandardCharsets.UTF_8))
        decodeHexString(signed) match {
          case Some(decoded) =>
            if (MessageDigest.isEqual(out, decoded)) {
              Right(raw)
            } else {
              Left(CSRFCheckFailed)
            }
          case None =>
            Left(CSRFCheckFailed)
        }
      case _ =>
        Left(CSRFCheckFailed)
    }

  /** To be only used on safe methods: if the method is safe (i.e doesn't modify data)
    * and a token is present, validate and regenerate it for BREACH to be impractical
    */
  private[middleware] def validate(r: Request[G], response: F[Response[G]])(
      implicit F: Sync[F]): F[Response[G]] =
    CSRF.cookieFromHeaders(r, cookieName) match {
      case Some(c) =>
        (for {
          raw <- F.fromEither(extractRaw(c.content))
          res <- response
          newToken <- signToken[F](raw)
        } yield res.addCookie(createResponseCookie(newToken)))
          .recover {
            case CSRFCheckFailed => Response[G](Status.Forbidden)
          }
      case None =>
        if (createIfNotFound) {
          response.flatMap(r => embedNewInCookie(r))
        } else response
    }

  /** Check for CSRF validity for an unsafe action.
    *
    * Exposed to users in case of manual plumbing of csrf token
    * (i.e websocket or query param)
    */
  def checkCSRFToken(r: Request[G], respAction: F[Response[G]], rawToken: String)(
      implicit F: Sync[F]): F[Response[G]] =
    if (!headerCheck(r)) {
      F.pure(onFailure)
    } else {
      (for {
        c1 <- CSRF.cookieFromHeadersF[F, G](r, cookieName)
        raw1 <- F.fromEither(extractRaw(c1.content))
        raw2 <- F.fromEither(extractRaw(rawToken))
        response <- if (CSRF.isEqual(raw1, raw2)) respAction
        else F.raiseError[Response[G]](CSRFCheckFailed)
        newToken <- signToken[F](raw1) //Generate a new token to guard against BREACH.
      } yield response.addCookie(ResponseCookie(name = cookieName, content = unlift(newToken))))
        .recover {
          case CSRFCheckFailed => Response[G](Status.Forbidden)
        }
    }

  /** Check for CSRF validity for an unsafe action.
    *
    * Check for the default header value
    */
  def checkCSRFDefault(r: Request[G], http: F[Response[G]]): F[Response[G]] =
    r.headers.get(headerName) match {
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

  def embedInResponseCookie(r: Response[G], token: CSRFToken): Response[G] =
    r.addCookie(createResponseCookie(token))

  def embedInRequestCookie(r: Request[G], token: CSRFToken): Request[G] =
    r.addCookie(createRequestCookie(token))

  /** Embed a token into a response **/
  def embedNewInCookie[M[_]: Sync](res: Response[G]): M[Response[G]] =
    generateToken[M].map(embedInResponseCookie(res, _))
}

object CSRF {

  //Newtype hax. Remove when we have a better story for newtypes
  type CSRFToken
  private[CSRF] def lift(s: String): CSRFToken = s.asInstanceOf[CSRFToken]
  def unlift(s: CSRFToken): String = s.asInstanceOf[String]
  final case object CSRFCheckFailed extends Exception("CSRF Check failed") with NoStackTrace
  type CSRFCheckFailed = CSRFCheckFailed.type

  /** Default method for constructing CSRF middleware **/
  def apply[F[_]: Sync, G[_]: Applicative](
      headerName: CaseInsensitiveString = CaseInsensitiveString("X-Csrf-Token"),
      cookieName: String = "csrf-token",
      clock: Clock = Clock.systemUTC(),
      secure: Boolean = false,
      onFailure: Response[G] = Response[G](Status.Forbidden),
      createIfNotFound: Boolean = true,
      key: SecretKey,
      headerCheck: Request[G] => Boolean): CSRF[F, G] =
    new CSRF[F, G](
      headerName,
      cookieName,
      clock,
      secure,
      onFailure,
      createIfNotFound,
      key,
      headerCheck)

  /** Default method for constructing CSRF middleware **/
  def default[F[_]: Sync, G[_]: Applicative](
      headerName: CaseInsensitiveString = CaseInsensitiveString("X-Csrf-Token"),
      cookieName: String = "csrf-token",
      clock: Clock = Clock.systemUTC(),
      secure: Boolean = false,
      onFailure: Response[G] = Response[G](Status.Forbidden),
      createIfNotFound: Boolean = true,
      key: SecretKey,
      host: String,
      sc: Scheme,
      port: Option[Int]): CSRF[F, G] =
    new CSRF[F, G](
      headerName,
      cookieName,
      clock,
      secure,
      onFailure,
      createIfNotFound,
      key,
      defaultOriginCheck(_, host, sc, port))

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
      headerName: CaseInsensitiveString = CaseInsensitiveString("X-Csrf-Token"),
      cookieName: String = "csrf-token",
      clock: Clock = Clock.systemUTC(),
      secure: Boolean = false,
      onFailure: Response[G] = Response[G](Status.Forbidden),
      createIfNotFound: Boolean = true,
      headerCheck: Request[G] => Boolean): F[CSRF[F, G]] =
    generateSigningKey().map(
      apply(headerName, cookieName, clock, secure, onFailure, createIfNotFound, _, headerCheck))

  /** Sugar for pre-loading a key **/
  def withKeyBytes[F[_]: Sync, G[_]: Applicative](
      keyBytes: Array[Byte],
      headerName: CaseInsensitiveString = CaseInsensitiveString("X-Csrf-Token"),
      cookieName: String = "csrf-token",
      clock: Clock = Clock.systemUTC(),
      onFailure: Response[G] = Response[G](Status.Forbidden),
      secure: Boolean = false,
      createIfNotFound: Boolean = true,
      headerCheck: Request[G] => Boolean): F[CSRF[F, G]] =
    buildSigningKey(keyBytes).map(
      apply(headerName, cookieName, clock, secure, onFailure, createIfNotFound, _, headerCheck))

  val SigningAlgo: String = "HmacSHA1"
  val SHA1ByteLen: Int = 20
  val CSRFTokenLength: Int = 32

  /** An instance of SecureRandom to generate
    * tokens, properly seeded:
    * https://tersesystems.com/blog/2015/12/17/the-right-way-to-use-securerandom/
    *
    * Note: The user is responsible for setting the proper
    * SecureRandom use via jvm flags.
    */
  private val InitialSeedArraySize: Int = 20
  private val CachedRandom: SecureRandom = {
    val r = new SecureRandom()
    r.nextBytes(new Array[Byte](InitialSeedArraySize))
    r
  }

  private[CSRF] def cookieFromHeadersF[F[_], G[_]](request: Request[G], cookieName: String)(
      implicit F: Sync[F]): F[RequestCookie] =
    cookieFromHeaders[G](request, cookieName) match {
      case Some(e) => F.pure(e)
      case None => F.raiseError(CSRFCheckFailed)
    }

  private[middleware] def cookieFromHeaders[F[_]](
      request: Request[F],
      cookieName: String): Option[RequestCookie] =
    HCookie
      .from(request.headers)
      .flatMap(_.values.find(_.name == cookieName))

  /** A Constant-time string equality **/
  def tokensEqual(s1: CSRFToken, s2: CSRFToken): Boolean =
    isEqual(unlift(s1), unlift(s2))

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
