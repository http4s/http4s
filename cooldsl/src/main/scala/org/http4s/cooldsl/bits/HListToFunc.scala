package org.http4s.cooldsl.bits

import shapeless.{HList, HNil}
import scalaz.concurrent.Task
import org.http4s._
import org.http4s.Response
import scala.Some
import shapeless.::

/**
 * Created by Bryce Anderson on 4/27/14.
 */

/////////////////// Helpers for turning a function of may params to a function of a HList

trait HListToFunc[H <: HList, +O, -F] {
  def conv(f: F): H => Task[Response]
}

object HListToFunc {

  implicit def fun0[O](implicit o: ObjToResponse[O]) = new HListToFunc[HNil, O, () => O] {
    override def conv(f: () => O): (HNil) => Task[Response] = { h => o(f()) }
  }

  implicit def fun1[T1, O](implicit o: ObjToResponse[O]) = new HListToFunc[T1::HNil, O, T1 => O] {
    override def conv(f: (T1) => O): (T1::HNil) => Task[Response] = { h => o(f(h.head)) }
  }

  implicit def fun2[T1, T2, O](implicit o: ObjToResponse[O]) = new HListToFunc[T1::T2::HNil, O, (T2, T1) => O] {
    override def conv(f: (T2, T1) => O): (T1::T2::HNil) => Task[Response] = { h => o(f(h.tail.head,h.head)) }
  }

  implicit def fun3[T1, T2, T3, O](implicit o: ObjToResponse[O]) = new HListToFunc[T3::T2::T1::HNil, O, Function3[T1, T2, T3, O]] {
    override def conv(f: (T1, T2, T3) => O): (T3::T2::T1::HNil) => Task[Response] = { h3 =>
      val t3 = h3.head
      val h2 = h3.tail
      val t2 = h2.head
      val t1 = h2.tail.head
      o(f(t1,t2,t3))
    }
  }

  implicit def fun4[T1, T2, T3, T4, O](implicit o: ObjToResponse[O]) = new HListToFunc[T4::T3::T2::T1::HNil, O, Function4[T1, T2, T3, T4, O]] {
    override def conv(f: (T1, T2, T3, T4) => O): (T4::T3::T2::T1::HNil) => Task[Response] = { h4 =>
      val t4 = h4.head
      val h3 = h4.tail
      val t3 = h3.head
      val h2 = h3.tail
      val t2 = h2.head
      val t1 = h2.tail.head
      o(f(t1,t2,t3, t4))
    }
  }

}

