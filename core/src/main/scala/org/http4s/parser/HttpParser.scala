package org.http4s
package parser

import org.parboiled.scala._
import org.http4s.Header.RawHeader

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

  val rules: Map[String, Rule1[Header]] =
    HttpParser
      .getClass
      .getMethods
      .filter(_.getName.forall(!_.isLower)) // only the header rules have no lower-case letter in their name
      .map { method =>
        method.getName.toLowerCase.replace('_', '-') -> method.invoke(HttpParser).asInstanceOf[Rule1[Header]]
      } (collection.breakOut)

  def parseHeader(header: Header): Either[String, Header] = {
    header match {
      case x@ Header.RawHeader(name, value) =>
        rules.get(x.lowercaseName) match {
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
      RawHeader("Accept", "*/*,text/plain,custom/custom"),
      RawHeader("Accept-Charset", "*,UTF-8"),
      RawHeader("Accept-Encoding", "gzip,custom"),
      RawHeader("Accept-Language", "*,nl-be,custom"),
      RawHeader("Authorization", "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="),
      RawHeader("Cache-Control", "no-cache"),
      RawHeader("Connection", "close"),
      RawHeader("Content-Disposition", "form-data"),
      RawHeader("Content-Encoding", "deflate"),
      RawHeader("Content-Length", "42"),
      RawHeader("Content-Type", "application/json"),
      RawHeader("Cookie", "http4s=cool"),
      RawHeader("Host", "http4s.org"),
      RawHeader("X-Forwarded-For", "1.2.3.4"),
      RawHeader("Fancy-Custom-Header", "yeah")
    ))

    QueryParser.parseQueryString("a=b&c=d")
  }
}