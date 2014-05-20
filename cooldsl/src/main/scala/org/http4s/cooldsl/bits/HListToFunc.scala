package org.http4s.cooldsl.bits

import shapeless.{HList, HNil}
import scalaz.concurrent.Task
import org.http4s._
import org.http4s.Response
import scala.Some
import shapeless.::
import org.http4s.Header.`Content-Type`

/**
 * Created by Bryce Anderson on 4/27/14.
 */

/////////////////// Helpers for turning a function of may params to a function of a HList

trait HListToFunc[T <: HList, O, -F] {
  def conv(f: F): (Request,T) => Task[Response]
  def encodings: Seq[MediaType] = Nil
  def manifest: Option[Manifest[O]]
}

object HListToFunc {

  implicit def wReqFun0[O](implicit o: ObjToResponse[O]) = new HListToFunc[HNil, O, (Request) => O] {
    override def encodings: Seq[MediaType] = o.mediaTypes
    override def manifest: Option[Manifest[O]] = o.manifest
    override def conv(f: (Request) => O): (Request, HNil) => Task[Response] = (req, _) => o(f(req))
  }

  implicit def fun0[O](implicit o: ObjToResponse[O]) = new HListToFunc[HNil, O, () => O] {
    override def encodings: Seq[MediaType] = o.mediaTypes
    override def manifest: Option[Manifest[O]] = o.manifest
    override def conv(f: () => O): (Request, HNil) => Task[Response] = (_,_) => o(f())
  }

  implicit def fun1[T1, O](implicit o: ObjToResponse[O]) = new HListToFunc[T1::HNil, O, T1 => O] {
    override def encodings: Seq[MediaType] = o.mediaTypes
    override def manifest: Option[Manifest[O]] = o.manifest
    override def conv(f: (T1) => O): (Request,T1::HNil) => Task[Response] = (_,h) => o(f(h.head))
  }

  implicit def wReqfun1[T1, O](implicit o: ObjToResponse[O]) = new HListToFunc[T1::HNil, O, (Request,T1) => O] {
    override def encodings: Seq[MediaType] = o.mediaTypes
    override def manifest: Option[Manifest[O]] = o.manifest
    override def conv(f: (Request,T1) => O): (Request,T1::HNil) => Task[Response] = (req,h) => o(f(req,h.head))
  }

  implicit def fun2[T1, T2, O](implicit o: ObjToResponse[O]) = new HListToFunc[T1::T2::HNil, O, (T2, T1) => O] {
    override def encodings: Seq[MediaType] = o.mediaTypes
    override def manifest: Option[Manifest[O]] = o.manifest
    override def conv(f: (T2, T1) => O): (Request,T1::T2::HNil) => Task[Response] = { (_,h) => o(f(h.tail.head,h.head)) }
  }

  implicit def wReqfun2[T1, T2, O](implicit o: ObjToResponse[O]) = new HListToFunc[T1::T2::HNil, O, (Request, T2, T1) => O] {
    override def encodings: Seq[MediaType] = o.mediaTypes
    override def manifest: Option[Manifest[O]] = o.manifest
    override def conv(f: (Request, T2, T1) => O): (Request,T1::T2::HNil) => Task[Response] = { (req,h) =>
      o(f(req,h.tail.head,h.head))
    }
  }

  implicit def fun3[T1, T2, T3, O](implicit o: ObjToResponse[O]) = new HListToFunc[T3::T2::T1::HNil, O, (T1, T2, T3) => O] {
    override def encodings: Seq[MediaType] = o.mediaTypes
    override def manifest: Option[Manifest[O]] = o.manifest
    override def conv(f: (T1, T2, T3) => O): (Request,T3::T2::T1::HNil) => Task[Response] = { (_,h3) =>
      val t3 = h3.head
      val h2 = h3.tail
      val t2 = h2.head
      val t1 = h2.tail.head
      o(f(t1,t2,t3))
    }
  }

  implicit def wReqfun3[T1, T2, T3, O](implicit o: ObjToResponse[O]) = new HListToFunc[T3::T2::T1::HNil, O, (Request, T1, T2, T3) => O] {
    override def encodings: Seq[MediaType] = o.mediaTypes
    override def manifest: Option[Manifest[O]] = o.manifest
    override def conv(f: (Request, T1, T2, T3) => O): (Request,T3::T2::T1::HNil) => Task[Response] = { (req,h3) =>
      val t3 = h3.head
      val h2 = h3.tail
      val t2 = h2.head
      val t1 = h2.tail.head
      o(f(req,t1,t2,t3))
    }
  }

  implicit def fun4[T1, T2, T3, T4, O](implicit o: ObjToResponse[O]) = new HListToFunc[T4::T3::T2::T1::HNil, O, (T1, T2, T3, T4) => O] {
    override def encodings: Seq[MediaType] = o.mediaTypes
    override def manifest: Option[Manifest[O]] = o.manifest
    override def conv(f: (T1, T2, T3, T4) => O): (Request,T4::T3::T2::T1::HNil) => Task[Response] = { (_,h4) =>
      val t4 = h4.head
      val h3 = h4.tail
      val t3 = h3.head
      val h2 = h3.tail
      val t2 = h2.head
      val t1 = h2.tail.head
      o(f(t1,t2,t3,t4))
    }
  }

  implicit def wReqfun4[T1, T2, T3, T4, O](implicit o: ObjToResponse[O]) = new HListToFunc[T4::T3::T2::T1::HNil, O, (Request, T1, T2, T3, T4) => O] {
    override def encodings: Seq[MediaType] = o.mediaTypes
    override def manifest: Option[Manifest[O]] = o.manifest
    override def conv(f: (Request, T1, T2, T3, T4) => O): (Request,T4::T3::T2::T1::HNil) => Task[Response] = { (req,h4) =>
      val t4 = h4.head
      val h3 = h4.tail
      val t3 = h3.head
      val h2 = h3.tail
      val t2 = h2.head
      val t1 = h2.tail.head
      o(f(req,t1,t2,t3,t4))
    }
  }

}

