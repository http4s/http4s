package org.http4s.server.middleware.authentication

import org.http4s.{Uri, Request, Response, BasicCredentials, Headers, GenericCredentials}
import org.http4s.server.HttpService
import org.http4s.Challenge
import org.http4s.Status._
import org.http4s.headers._
import org.http4s.parser.HttpParser
import org.http4s.util.CaseInsensitiveString

import org.specs2.mutable.Specification

class AuthenticationSpec extends Specification {

  val service = HttpService {
    case r if r.pathInfo == "/" => Response(Ok).withBody("foo")
    case r => Response.notFound(r)
  }

  val realm = "Test Realm"
  val username = "Test User"
  val password = "Test Password"
 
  val auth_store : PartialFunction[(String, String), String] = {
    case (r, u) if r == realm && u == username => password
  }

  val basic = new BasicAuthentication( realm, auth_store )( service )

  "BasicAuthentication" should {
    "Respond to a request without authentication with 401" in {
      val req = Request(uri = Uri(path = "/"))
      val res = basic(req).run

      res.isDefined must_== true
      res.get.status must_== Unauthorized
      res.get.headers.get(`WWW-Authenticate`).map(_.value) must beSome(Challenge("Basic", realm, Nil.toMap ).toString)
    }

    "Respond to a request with unknown username with 401" in {
      val req = Request(uri = Uri(path = "/"), headers = Headers(Authorization(BasicCredentials("Wrong User", password))))
      val res = basic(req).run

      res.isDefined must_== true
      res.get.status must_== Unauthorized
      res.get.headers.get(`WWW-Authenticate`).map(_.value) must beSome(Challenge("Basic", realm, Nil.toMap ).toString)
    }

    "Respond to a request with wrong password with 401" in {
      val req = Request(uri = Uri(path = "/"), headers = Headers(Authorization(BasicCredentials(username, "Wrong Password"))))
      val res = basic(req).run

      res.isDefined must_== true
      res.get.status must_== Unauthorized
      res.get.headers.get(`WWW-Authenticate`).map(_.value) must beSome(Challenge("Basic", realm, Nil.toMap ).toString)
    }

    "Respond to a request with correct credentials" in {
      val req = Request(uri = Uri(path = "/"), headers = Headers(Authorization(BasicCredentials(username, password))))
      val res = basic(req).run

      res.isDefined must_== true
      res.get.status must_== Ok
    }
  }

  val digest = new DigestAuthentication( realm, auth_store )( service )

  private def parse( value : String ) = HttpParser.WWW_AUTHENTICATE(value).fold(err => sys.error(s"Couldn't parse: $value"), identity)

  "DigestAuthentication" should {
    "Respond to a request without authentication with 401" in {
      val req = Request(uri = Uri(path = "/"))
      val res = digest(req).run

      res.isDefined must_== true
      res.get.status must_== Unauthorized
      val opt = res.get.headers.get(`WWW-Authenticate`).map(_.value) 
      opt.isDefined must beTrue
      val challenge = parse(opt.get).values.head
      (challenge match { 
        case Challenge("Digest", realm, _) => true 
        case _ => false
      }) must_== true
    }

    "Respond to a request with correct credentials" in {
      // Get auth data
      val req = Request(uri = Uri(path = "/"))
      val res = digest(req).run

      res.isDefined must_== true
      res.get.status must_== Unauthorized
      val opt = res.get.headers.get(`WWW-Authenticate`).map(_.value) 
      opt.isDefined must beTrue
      val challenge = parse(opt.get).values.head
      (challenge match { 
        case Challenge("Digest", realm, _) => true 
        case _ => false
      }) must_== true

      // Second request with credentials
      val method = "GET"
      val uri = "/"
      val qop = "auth"
      val nc = "00000001"
      val cnonce = "abcdef"
      val nonce = challenge.params("nonce")
      
      val response = DigestUtil.computeResponse( method, username, realm, password, uri, nonce, nc, cnonce, qop )
      val params : Map[String, String] = Map( "username" -> username, "realm" -> realm, "nonce" -> nonce,
                     "uri" -> uri, "qop" -> qop, "nc" -> nc, "cnonce" -> cnonce, "response" -> response , 
                     "method" -> method )
      val header = Authorization(GenericCredentials(CaseInsensitiveString("Digest"), params))

      val req2 = Request(uri = Uri(path = "/"), headers = Headers(header))
      val res2 = digest(req2).run

      res2.isDefined must_== true
      res2.get.status must_== Ok     

      // Digest prevents replay
      val res3 = digest(req2).run
      res3.isDefined must_== true
      res3.get.status must_== Unauthorized
    }

    "Respond to invalid requests with 401" in {
      val method = "GET"
      val uri = "/"
      val qop = "auth"
      val nc = "00000001"
      val cnonce = "abcdef"
      val nonce = "abcdef"

      val response = DigestUtil.computeResponse( method, username, realm, password, uri, nonce, nc, cnonce, qop )
      val params : Map[String, String] = Map( "username" -> username, "realm" -> realm, "nonce" -> nonce,
                     "uri" -> uri, "qop" -> qop, "nc" -> nc, "cnonce" -> cnonce, "response" -> response ,
                     "method" -> method )

      val expected = (0 to params.size).map( i => (true, Unauthorized))
      val result = (0 to params.size).map( i => {
        val invalid_params = params.take(i) ++ params.drop(i+1)
        val header = Authorization(GenericCredentials(CaseInsensitiveString("Digest"), invalid_params))
        val req = Request(uri = Uri(path = "/"), headers = Headers(header))
        val res = digest(req).run

        (res.isDefined, res.get.status)
      } )

      expected must_== result
    }
  }
}
