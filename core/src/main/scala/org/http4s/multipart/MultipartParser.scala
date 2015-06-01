package org.http4s.multipart

import org.parboiled2._
import org.parboiled2.CharPredicate._

class MultipartParser(val input: ParserInput) extends Parser {

  def Octet               = rule { "\u0000" - "\u00FF" }
  def CRLF                = rule { str("\r\n") }
  def Dash                = rule { str("--") }
  def LWS                 = rule { optional(CRLF) ~ oneOrMore(anyOf(" \t")) }  
  def OptWS               = rule { zeroOrMore(LWS) }
  def BCharsNoSpace       = rule { Alpha | Digit }
  def BChars              = rule { Alpha | Digit | " "}
  //TODO: should be 0 to 69
  def Boundary            = rule { (1 to 69) times BChars ~ BCharsNoSpace }
  def DashBoundary        = rule { Dash ~ Boundary }
  def Delimiter           = rule { CRLF ~ DashBoundary}
  def TransportPadding    = rule { zeroOrMore(LWS)}
  def Encapsulation       = rule { Delimiter ~ TransportPadding ~ CRLF ~ BodyPart}
  def CloseDelimiter      = rule {Delimiter ~ Dash}
  def DiscardText         = rule { optional(zeroOrMore( Visible) ~ CRLF) ~ zeroOrMore( Visible) }
  def Preamble            = DiscardText
  def OptionalFrontMatter = rule {optional(Preamble       ~ CRLF)} 
  def Epilogue            = DiscardText
  // See https://www.ietf.org/rfc/rfc2045.txt Section 5.1
  def Header              = rule {  HeaderName ~ ":" ~ HeaderValue  }
  def HeaderName          = rule {  oneOrMore(Alpha ++ "-" )  }
  def HeaderValue         = rule {  oneOrMore(Alpha) ~ "/" ~ oneOrMore(Alpha)  }
  def MimePartHeaders     = rule { zeroOrMore(Header ~ CRLF ) }
  def BodyPart            = rule { MimePartHeaders ~ CRLF ~ zeroOrMore(Octet)}
  def MultipartBody       = rule {
                            OptionalFrontMatter            ~
                            DashBoundary   ~ TransportPadding          ~ CRLF ~ 
                            BodyPart       ~ zeroOrMore(Encapsulation) ~
                            CloseDelimiter ~ TransportPadding          ~ 
                   optional(CRLF           ~ Epilogue)    
                          }
  
  def Building           = rule {
                            OptionalFrontMatter            ~
                            DashBoundary   ~ TransportPadding          ~ CRLF }
}

object Test extends App {
  
      val body       = """
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
      """
      val building       = """------WebKitFormBoundarycaZFo8IAKVROTEeD
"""
      
      println("moos")
 println(     new MultipartParser(building).Building.run().recover {
  case e: ParseError => e.traces.mkString("\n\n\n","\n\n\n","\n\n\n")
})
  
  
  
}