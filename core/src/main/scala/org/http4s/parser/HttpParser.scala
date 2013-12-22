package org.http4s
package parser

import org.http4s.Header
import org.http4s.util.CaseInsensitiveString

import scalaz.{Failure, Validation, Success}

/**
 * @author Bryce Anderson
 *         Created on 12/22/13
 */

object HttpParser extends HttpParser

trait HttpParser extends SimpleHeaders {

  type HeaderValidation = Validation[ParseErrorInfo, Header]

  type HeaderParser = String => HeaderValidation

  val rules: Map[CaseInsensitiveString, HeaderParser] =
    this
      .getClass
      .getMethods
      .filter(_.getName.forall(!_.isLower)) // only the header rules have no lower-case letter in their name
      .map { method =>
      method.getName.replace('_', '-').ci -> { value: String =>
        method.invoke(this, value)
      }.asInstanceOf[HeaderParser]
    } (collection.breakOut)

  def parseHeader(header: Header): HeaderValidation = {
    header match {
      case x@ Header.RawHeader(name, value) =>
        rules.get(name) match {
          case Some(parser) => parser(value)
          case None => Success(x) // if we don't have a rule for the header we leave it unparsed
        }
      case x => Success(x) // already parsed
    }
  }

  def parseHeaders(headers: List[Header]): (List[String], List[Header]) = {
    val errors = List.newBuilder[String]
    val parsedHeaders = headers.map { header =>
      parseHeader(header) match {
        case Success(parsed) => parsed
        case Failure(error: ParseErrorInfo) => errors += error.detail; header
      }
    }
    (errors.result(), parsedHeaders)
  }
}
