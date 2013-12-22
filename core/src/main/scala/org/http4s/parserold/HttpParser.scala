package org.http4s
package parserold

import org.parboiled.scala._
import org.http4s.Header
import org.http4s.util.CaseInsensitiveString

import org.http4s.parser.ParseErrorInfo

/**
 * Parser for all HTTP headers as defined by
 *  [[http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html]]
 */
object HttpParser extends Http4sParser with ProtocolParameterRules with AdditionalRules with CommonActions
  with AcceptCharsetHeader
  with AcceptEncodingHeader
  with AcceptHeader
  with AcceptLanguageHeader
  with AcceptRangesHeader
  with AuthorizationHeader
  with CacheControlHeader
  with ContentEncodingHeader
  with ContentTypeHeader
  with CookieHeaders
  with SimpleHeaders
  with WwwAuthenticateHeader
  {

  // all string literals automatically receive a trailing optional whitespace
  override implicit def toRule(string :String): Rule0 =
    super.toRule(string) ~ BasicRules.OptWS

  val rules: Map[CaseInsensitiveString, Rule1[Header]] =
    HttpParser
      .getClass
      .getMethods
      .filter(_.getName.forall(!_.isLower)) // only the header rules have no lower-case letter in their name
      .map { method =>
        method.getName.replace('_', '-').ci -> method.invoke(HttpParser).asInstanceOf[Rule1[Header]]
      } (collection.breakOut)

  def parseHeader(header: Header): Either[String, Header] = {
    header match {
      case x@ Header.RawHeader(name, value) =>
        rules.get(name) match {
          case Some(rule) => parse(rule, value).left.map("Illegal HTTP header '" + name + "': " + _.formatPretty)
          case None => Right(x) // if we don't have a rule for the header we leave it unparsed
        }
      case x => Right(x) // already parsed
    }
  }

  def parseHeaders(headers: List[Header]): (List[String], List[Header]) = {
    val errors = List.newBuilder[String]
    val parsedHeaders = headers.map { header =>
      parseHeader(header) match {
        case Right(parsed) => parsed
        case Left(error) => errors += error; header
      }
    }
    (errors.result(), parsedHeaders)
  }

  def parseContentType(contentType: String): Either[ParseErrorInfo, ContentType] =
    parse(ContentTypeHeaderValue, contentType).left.map(_.withFallbackSummary("Illegal Content-Type"))

  /**
   * Warms up the spray.http module by triggering the loading of most classes in this package,
   * so as to increase the speed of the first usage.
   */
  def warmUp() {
    HttpParser.parseHeaders(List(
      Header("Accept", "*/*,text/plain,custom/custom"),
      Header("Accept-Charset", "*,UTF-8"),
      Header("Accept-Encoding", "gzip,custom"),
      Header("Accept-Language", "*,nl-be,custom"),
      Header("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="),
      Header("Cache-Control", "no-cache"),
      Header("Connection", "close"),
      Header("Content-Disposition", "form-data"),
      Header("Content-Encoding", "deflate"),
      Header("Content-Length", "42"),
      Header("Content-Type", "application/json"),
      Header("Cookie", "http4s=cool"),
      Header("Host", "http4s.org"),
      Header("X-Forwarded-For", "1.2.3.4"),
      Header("Fancy-Custom-Header", "yeah")
    ))

    QueryParser.parseQueryString("a=b&c=d")
  }
}