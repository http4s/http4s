package org.http4s.multipart

import org.parboiled2._
import org.parboiled2.CharPredicate._

final case class Pair(key:String,value:String)
final case class BPart(headers:Seq[Pair],content:String)
class MultipartParser(val input: ParserInput) extends Parser {

  def Octet               = rule { "\u0000" - "\u00FF" }
  //TODO: This is a bit naughty
  def CRLF                = rule { str("\r\n") | str("\n") }
  def Dash                = rule { str("--") }
  
  //http://www.rfc-editor.org/std/std68.txt
  def LWS                 = rule { anyOf(" \t") }  
  def AltBoundaryChar     = rule { "'" | "(" | ")" |
                                   "+" | "_" | "," | "-" | "." |
                                   "/" | ":" | "=" | "?"}
  def BCharsNoSpace       = rule { Alpha | Digit | AltBoundaryChar }
  def BChars              = rule { BCharsNoSpace | " "}
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
  def Header              = rule { (capture(HeaderName)  ~ ':' ~ capture(HeaderValue))  ~> ((k,v) => Pair(k,v)) }
  def HeaderName          = rule { oneOrMore(Alpha | '-' )    }
  def HeaderValue         = rule { oneOrMore(!CRLF ~ ANY)     }
  def MimePartHeaders     = rule { zeroOrMore(Header ~ CRLF ) }
  def BodyContent         = rule { capture(zeroOrMore(Octet)) }
  def BodyPart            = rule { (MimePartHeaders ~ CRLF ~ BodyContent) ~> ((h,b) => BPart(h,b))}

  //                            OptionalFrontMatter            ~
  //                   optional(CRLF           ~ Epilogue)    
  def MultipartBody       = rule {

                            DashBoundary   ~ TransportPadding          ~ CRLF ~ 
                            BodyPart       ~ zeroOrMore(Encapsulation) ~
                            CloseDelimiter ~ TransportPadding           

                          }
  //Working 
  //def Building           = rule {  DashBoundary  ~ TransportPadding ~ CRLF}
  //def Building           = rule { MimePartHeaders  }
  //def Building           = rule { MimePartHeaders ~ CRLF }
  //def Building           = rule { MimePartHeaders ~ CRLF ~ zeroOrMore(Octet)}
  //def Building           = rule { BodyPart}
    def Building           = rule { zeroOrMore(Encapsulation)}      
}

object Test extends App {
  
// Working 
//      val building       = """Content-Disposition: form-data; name="text"
//Content-Type: text/plain
//
//I AM A MOOSE
//I am some content
//""""
      val building        = """
------WebKitFormBoundarycaZFo8IAKVROTEeD
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

"""
  
      
      
      
      println("moos")
 println(     new MultipartParser(building).Building.run().recover {
   case pe:ParseError => println(pe.traces.mkString("\n\n"))
   
 })
  
  
  
}