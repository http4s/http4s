package org.http4s.multipart

import org.parboiled2._
import org.parboiled2.CharPredicate._

final case class Pair(key:String,value:String)
final case class PHeader(key:String,value:String, parameters:Option[Seq[Pair]])
final case class BPart(headers:Seq[PHeader],content:String)
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
  def Key          = rule { oneOrMore(Alpha | '-' )    }
  def Value         = rule { oneOrMore(!HeaderDelimiter ~ Octet)     }
  def HeaderDelimiter     = rule { CRLF |  ";"   | "\"" }
  def Header              = rule { 
                                  (capture(Key)  ~ ':' ~ capture(Value)) ~ 
                                  optional(";" ~ oneOrMore(HeaderParameters).separatedBy(";")) ~> ((k,v,p) => PHeader(k,v,p)) 
                            }

  def Parameters =  rule {oneOrMore(HeaderParameters).separatedBy(";")} 
  def HeaderParameters    = rule {  OLWS  ~ (capture(Key)  ~  OLWS ~  '=' ~  OLWS ~ optional("\"") ~ capture(Value)) ~ optional("\"") ~  OLWS ~> ((k,v) => Pair(k,v))  } 
  
  def MimePartHeaders     = rule { zeroOrMore(Header ~ CRLF ) }
  def BodyEnd             = rule {CRLF ~ DashBoundary}
  def BodyContent         = rule { capture(zeroOrMore(!BodyEnd ~ ANY)) }
  def BodyPart            = rule { (MimePartHeaders ~ CRLF ~ BodyContent) ~> ((h,b) => BPart(h,b)) }
  def Encapsulation       = rule { CRLF ~ DashBoundary ~ TransportPadding ~ CRLF ~ BodyPart }
  def MultipartBody       = rule {
                            Preamble       ~
                            DashBoundary   ~ TransportPadding          ~ CRLF ~ 
                            BodyPart       ~ zeroOrMore(Encapsulation) ~
                            CloseDelimiter ~ TransportPadding          ~
                            Epilogue       ~ EOI
                          }
  
}

object Test extends App {
  
  val building        = """
------WebKitFormBoundarycaZFo8IAKVROTEeD
Content-Disposition: form-data; name="text"

I AM A MOOSE
------WebKitFormBoundarycaZFo8IAKVROTEeD
Content-Disposition: form-data; name="file1"; filename="Graph_Databases_2e_Neo4j.pdf"
Content-Type: application/pdf


------WebKitFormBoundarycaZFo8IAKVROTEeD
Content-Disposition: form-data; name="file2"; filename="DataTypesALaCarte.pdf"
Content-Type: application/pdf


------WebKitFormBoundarycaZFo8IAKVROTEeD--
"""""
  
      
      
      
 println("moos")
 println(     new MultipartParser(building,Boundary("----WebKitFormBoundarycaZFo8IAKVROTEeD")).MultipartBody.run().recover {
   case pe:ParseError => println(pe.traces.mkString("\n\n"))
   
 })
  
  
  
}