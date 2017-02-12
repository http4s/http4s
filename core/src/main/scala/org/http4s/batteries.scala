package org.http4s

protected[http4s] trait Batteries0 extends AnyRef
    with cats.syntax.AllSyntax
    with cats.instances.AllInstances
    with fs2.interop.cats.Instances
    with util.ByteChunkSyntax
    with syntax.StringSyntax
    with util.ChunkInstances
    with util.NonEmptyListSyntax
{
  implicit def StreamCatsOps[F[_], A](self: fs2.Stream[F, A]): fs2.interop.cats.StreamCatsOps[F, A] =
    fs2.interop.cats.StreamCatsOps(self)
}

/** An all-batteries included import for internal use in http4s.  This
  * is convenient on the master branch and reduces merge conflicts for
  * those maintaining ports to alternative stacks.
  */
protected[http4s] object batteries extends Batteries0
{
  implicit class MoreFunctorSyntax[F[_], A](self: F[A])(implicit F: cats.Functor[F]) {
    def widen[B >: A]: F[B] =
      self.asInstanceOf[F[B]] // F.widen(self) in cats-0.7
  }

  def left[A](a: A): Either[A, Nothing] =
    Left(a)

  def right[B](b: B): Either[Nothing, B] =
    Right(b)
}
