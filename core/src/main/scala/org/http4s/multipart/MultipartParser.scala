package org.http4s
package multipart

import scodec.bits.ByteVector

import scalaz.stream.Process

import org.http4s.headers._
import org.http4s.util.string._

import org.parboiled2._
import org.parboiled2.CharPredicate._

class MultipartParser(val input: ParserInput, val boundary:Boundary) extends Parser {

  def Octet               = rule { "\u0000" - "\u00FF" }
  //TODO: This is a bit naughty
  //This is a nicer way to write it val NL = rule { optional('\r') ~ '\n' } from 
  //https://github.com/sirthias/parboiled2/blob/master/examples/src/main/scala/org/parboiled2/examples/CsvParser.scala#L3
  def CRLF                = rule { str("\r\n") | str("\n") }
  def Dash                = rule { str("--") }
  //http://www.rfc-editor.org/std/std68.txt
  def LWS                 = rule { anyOf(" \t") }  
  def OLWS                = rule { optional(LWS)}
  def DashBoundary        = rule { Dash ~ boundary.value }
  def Delimiter           = rule { CRLF ~ DashBoundary }
  def TransportPadding    = rule { zeroOrMore(LWS) }
  def CloseDelimiter      = rule { Delimiter ~ Dash }
  def DiscardText         = rule { zeroOrMore( !DashBoundary ~ ANY) }
  def Preamble            = DiscardText 
  def Epilogue            = DiscardText
  // See https://www.ietf.org/rfc/rfc2045.txt Section 5.1
  def Key                 = rule { oneOrMore(Alpha | '-' )    }
  def Value               = rule { oneOrMore(!HeaderDelimiter ~ Octet)     }
  def HeaderDelimiter     = rule { CRLF |  ";"   | "\"" }
  //  Avoid name collision with http4sHeader.
  def HeaderRule          = rule { 
                                  (capture(Key)  ~ ':' ~ capture(Value)) ~ 
                                  optional(";" ~ oneOrMore(HeaderParameters).separatedBy(";")) ~> ((k,v,p) => toHttp4sHeader(k,v,p) ) 
                            }
  def Parameters          = rule { oneOrMore(HeaderParameters).separatedBy(";") } 
  def HeaderParameters    = rule { OLWS  ~ (capture(Key)  ~  OLWS ~  '=' ~  OLWS ~ optional("\"") ~ capture(Value)) ~ optional("\"") ~  OLWS ~> ((k,v) => (k -> v))  } 
  def MimePartHeaders     = rule { zeroOrMore(HeaderRule ~ CRLF ) }
  def BodyEnd             = rule { CRLF ~ DashBoundary}
  def BodyContent         = rule { capture(zeroOrMore(!BodyEnd ~ ANY)) ~>  (s => EntityEncoder.Entity(Process.emit(ByteVector(s.getBytes))))}
  def BodyPart            = rule { (MimePartHeaders ~ CRLF ~ BodyContent) ~> ((h,b) => toMultipartPart(h,b))  }
  def Encapsulation       = rule { CRLF ~ DashBoundary ~ TransportPadding ~ CRLF ~ BodyPart }
  def MultipartBody       = rule {
                            Preamble       ~
                            DashBoundary   ~ TransportPadding          ~ CRLF ~ 
                            BodyPart       ~ zeroOrMore(Encapsulation) ~
                            CloseDelimiter ~ TransportPadding          ~
                            Epilogue       ~ EOI  ~> ( (h,e) => toMultipart( h +: e ))
                          }
  
  import scalaz._
  import Scalaz._
  
  private def toHttp4sHeader(key:String,value:String, parameters:Option[Seq[(String, String)]]): ParseFailure \/ Header = parameters match {
    case None         =>  Header.Raw(key.ci,value).right
    case Some(params) =>  
      val map = params.toList.toMap
      val none:ParseFailure \/ Header = ParseFailure("Missing 'name' parameter. ").left 
      val some:String => ParseFailure \/ Header = {name =>
         `Content-Disposition`(name,map - ("name")).right
       }
       map.get("name").fold(none)(some)
  }   
  
  private def toMultipartPart(_headers:Seq[ParseFailure \/ Header],body:EntityEncoder.Entity): Seq[ParseFailure] \/ Part = {
    val headers = _headers.partition(_.isLeft) match {
      case (Nil,headers) => Headers(headers.flatMap(_.toOption).toList).right
      case (errs,_)    => errs.flatMap(_.swap.toOption).left
    }
    val part: Headers => Seq[ParseFailure] \/ Part = { headers =>      
      headers.get(`Content-Disposition`).fold[Seq[ParseFailure] \/ Part](
            Seq(ParseFailure("Missing Content-Disposition header")).left)(
            a => FormData(multipart.Name(a.dispositionType),None, body).right)
    }
    headers fold(ers => ers.left,part)
  }   
  
  
  private def toMultipart(_parts:Seq[Seq[ParseFailure] \/ Part]):Seq[ParseFailure] \/ Multipart = {
    _parts.partition(_.isLeft) match {
      case (Nil,parts) => Multipart(parts.flatMap(_.toOption)).right
      case (errs,_)    => errs.flatMap(_.swap.toOption).flatten.left
    }   
  } 
}