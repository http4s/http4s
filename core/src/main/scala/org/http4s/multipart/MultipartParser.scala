package org.http4s.multipart

import org.parboiled2._
import org.parboiled2.CharPredicate._

final case class Pair(key:String,value:String)
final case class BPart(headers:Seq[Pair],content:String)
class MultipartParser(val input: ParserInput, val boundary:Boundary) extends Parser {

  def Octet               = rule { "\u0000" - "\u00FF" }
  //TODO: This is a bit naughty
  def CRLF                = rule { str("\r\n") | str("\n") }
  def Dash                = rule { str("--") }
  
  //http://www.rfc-editor.org/std/std68.txt
  def LWS                 = rule { anyOf(" \t") }  
  //def AltBoundaryChar     = rule { "'" | "(" | ")" |
  //                                 "+" | "_" | "," | "-" | "." |
  //                                 "/" | ":" | "=" | "?"}
  //def BCharsNoSpace       = rule { Alpha | Digit | AltBoundaryChar }
  //def BChars              = rule { BCharsNoSpace | " "}
  //TODO: should be 0 to 69
  //def Boundary            = rule { (1 to 69) times BChars ~ BCharsNoSpace }
  def DashBoundary        = rule { Dash ~ str(boundary.value) }
  def Delimiter           = rule { CRLF ~ DashBoundary }
  def TransportPadding    = rule { zeroOrMore(LWS) }

  def CloseDelimiter      = rule { Delimiter ~ Dash }
  def DiscardText         = rule { zeroOrMore( !DashBoundary ~ ANY) }
  def Preamble            = DiscardText 
  def Epilogue            = DiscardText
  // See https://www.ietf.org/rfc/rfc2045.txt Section 5.1
  def Header              = rule { (capture(HeaderName)  ~ ':' ~ capture(HeaderValue))  ~> ((k,v) => Pair(k,v)) }
  def HeaderName          = rule { oneOrMore(Alpha | '-' )    }
  def HeaderValue         = rule { oneOrMore(!CRLF ~ Octet)     }
  def MimePartHeaders     = rule { zeroOrMore(Header ~ CRLF ) }
  def BodyEnd             = rule {CRLF ~ DashBoundary}
  def BodyContent         = rule { capture(zeroOrMore(!BodyEnd ~ ANY)) }
  def BodyPart            = rule { (MimePartHeaders ~ CRLF ~ BodyContent) ~> ((h,b) => BPart(h,b)) }
   //oneOrMore(field).separatedBy(ctx.fieldDelimiter)
  
  //                            OptionalFrontMatter            ~
  //                   optional(CRLF           ~ Epilogue)    
  def Encapsulation       = rule { CRLF ~ DashBoundary ~ TransportPadding ~ CRLF ~ BodyPart }
  
  def MultipartBody       = rule {
                            Preamble       ~
                            DashBoundary   ~ TransportPadding          ~ CRLF ~ 
                            BodyPart       ~ zeroOrMore(Encapsulation) ~
                            CloseDelimiter ~ TransportPadding          ~
                            Epilogue
                          }
  //Working 
  //def Building           = rule {  DashBoundary  ~ TransportPadding ~ CRLF}
  //def Building           = rule { MimePartHeaders  }
  //def Building           = rule { MimePartHeaders ~ CRLF }
  //def Building           = rule { MimePartHeaders ~ CRLF ~ zeroOrMore(Octet)}
  //def Building           = rule { BodyPart}
  //def Building = rule {DashBoundary   ~ TransportPadding          ~ CRLF ~  BodyPart       ~ zeroOrMore(Encapsulation) }
  //Not working as expected 
  //    def Building           = rule { CRLF ~ DashBoundary ~ TransportPadding ~ CRLF ~ zeroOrMore(BodyPart).separatedBy(CRLF ~ DashBoundary ~ TransportPadding ~ CRLF  ) }
  
}

object Test extends App {
  
// Working 
//      val building       = """Content-Disposition: form-data; name="text"
//Content-Type: text/plain
//
//I AM A MOOSE
//I am some content
//""""
      val building        = """------WebKitFormBoundarycaZFo8IAKVROTEeD
Content-Disposition: form-data; name="text"
Content-Type: text/plain

I AM A MOOSE
------WebKitFormBoundarycaZFo8IAKVROTEeD
Content-Disposition: form-data; name="file1"; filename="Graph_Databases_2e_Neo4j.pdf"
Content-Type: text/plain


MORE CONTENTS

------WebKitFormBoundarycaZFo8IAKVROTEeD
Content-Disposition: form-data; name="file2"; filename="DataTypesALaCarte.pdf"
Content-Type: text/plain

EVEN MORE CONTENTS
------WebKitFormBoundarycaZFo8IAKVROTEeD--
"""
  
      
      
      
 println("moos")
 println(     new MultipartParser(building,Boundary("----WebKitFormBoundarycaZFo8IAKVROTEeD")).MultipartBody.run().recover {
   case pe:ParseError => println(pe.traces.mkString("\n\n"))
   
 })
  
  
  
}