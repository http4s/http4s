package org.http4s
package internal

import cats.effect.{IO, Resource}
import cats.effect.laws.discipline.arbitrary._
import cats.implicits._
import java.util.concurrent.atomic.AtomicBoolean
import org.scalacheck.{Arbitrary, Gen}

class AllocatedSpec extends Http4sSpec {
  "allocated" should {
    "allocate same value as the resource" in prop { resource: Resource[IO, Int] =>
      (for {
        a0 <- Resource(allocated(resource)).use(IO.pure).attempt
        a1 <- resource.use(IO.pure).attempt
      } yield a0 must_== a1).unsafeRunSync()
    }

    "not release until close is invoked" in {
      val released = new AtomicBoolean(false)
      prop { resource: Resource[IO, Int] =>
        released.set(false)
        (for {
          att <- allocated(resource).attempt
          _ <- IO(released.get() must beFalse)
          a0 <- att.fold(_ => IO.pure(ok), _._2 >> IO(released.get() must beTrue))
        } yield a0).unsafeRunSync()
      }.setGen(genResource(implicitly, implicitly, Arbitrary(Gen.const(IO(released.set(true))))))
    }
  }
}
