package org.http4s.cooldsl
package bits

import org.specs2.mutable.Specification
import scodec.bits.ByteVector
import org.http4s._
import org.http4s.cooldsl.CoolService
import org.http4s.Status._

/**
 * Created by Bryce Anderson on 5/4/14.
 */
class HListToFuncTest extends Specification {

  def getBody(b: HttpBody): String = {
    new String(b.runLog.run.foldLeft(ByteVector.empty)(_ ++ _).toArray)
  }

  def checkOk(r: Request): String = getBody(service(r).run.body)

  def Get(s: String, h: Header*): Request = Request(Method.Get, Uri.fromString(s).get, headers = Headers(h:_*))

  val service = new CoolService {
    val route1 = Method.Get / "route1" |>>> { () => Ok("foo") }
    append(route1)

    val route2 = Method.Get / "route2" |>>> { () => "foo" }
    append(route2)

  }

  "HListToFunc" should {
    "Work for methods of type _ => Task[Response]" in {
      val req = Get("/route1")
      checkOk(req) should_== "foo"
    }

    "Work for methods of type _ => O where a Writable[O] exists" in {
      val req = Get("/route2")
      checkOk(req) should_== "foo"
    }
  }
}
