package org.http4s

import scalaz.Kleisli
import scalaz.concurrent.Task

final case class Service[A, B](run: PartialFunction[A, Task[B]]) {
  import Service._

  def apply(a: A)(implicit ev: Task[Response] =:= Task[B]): Task[B] =
    applyOrElse(a, (_: A) => ev(HttpService.notFound))

  def get(a: A): Option[Task[B]] =
    run.lift(a)

  def applyOrElse(a: A, default: A => Task[B]): Task[B] =
    run.applyOrElse(a, default)

  def orElse(fallback: Service[A, B]) =
    Service(run orElse fallback.run)

  def ||(fallback: Service[A, B]): Service[A, B] =
    orElse(fallback)

/*
  def ap[C](f: Service[A, B => C]): Service[A, C] =
    Service(a => F.ap(f.run(a))(run(a)))

  def dimap[C, D](f: C => A)(g: B => D)(implicit F: Functor[F]): Service[F, C, D] =
    Service(c => F.map(run(f(c)))(g))
 */

  def map[C](f: B => C): Service[A, C] =
    Service(run.andThen(_.map(f)))

/*
  def mapF[N[_], C](f: F[B] => N[C]): Service[N, A, C] =
    Service(run andThen f)

  def flatMap[C](f: B => Service[F, A, C])(implicit F: FlatMap[F]): Service[F, A, C] =
    Service((r: A) => F.flatMap[B, C](run(r))((b: B) => f(b).run(r)))
 */

  // Not sure why cats has this and `andThen`?
  def flatMapF[C](f: B => Task[C]): Service[A, C] =
    andThen(f)

  /* for compatibility with scalaz */
  @deprecated("Use andThen instead", "0.15")
  def flatMapK[C](f: B => Task[C]): Service[A, C] =
    andThen(f)

  def andThen[C](f: B => Task[C]): Service[A, C] =
    Service(run.andThen(_.flatMap(f)))

/*
  def andThen[C](k: Service[F, B, C])(implicit F: FlatMap[F]): Service[F, A, C] =
    this andThen k.run

  def compose[Z](f: Z => F[A])(implicit F: FlatMap[F]): Service[F, Z, B] =
    Service((z: Z) => F.flatMap(f(z))(run))

  def compose[Z](k: Service[F, Z, A])(implicit F: FlatMap[F]): Service[F, Z, B] =
    this compose k.run

  def traverse[G[_]](f: G[A])(implicit F: Applicative[F], G: Traverse[G]): F[G[B]] =
    G.traverse(f)(run)

  def lift[G[_]](implicit G: Applicative[G]): Service[λ[α => G[F[α]]], A, B] =
    Service[λ[α => G[F[α]]], A, B](a => Applicative[G].pure(run(a)))
 */

  def local[AA](f: AA => A): Service[AA, B] =
    Service(new PartialFunction[AA, Task[B]] {
      def apply(aa: AA): Task[B] =
        run.apply(f(aa))
      def isDefinedAt(aa: AA): Boolean =
        run.isDefinedAt(f(aa))
      override def applyOrElse[A1 <: AA, B1 >: Task[B]](a1: A1, default: A1 => B1): B1 = {
        val a = f(a1)
        if (run.isDefinedAt(a)) run(a)
        else default(a1)
      }
    })

/*
  def transform[G[_]](f: FunctionK[F, G]): Service[G, A, B] =
    Service(a => f(run(a)))

  def lower(implicit F: Applicative[F]): Service[F, A, F[B]] =
    Service(a => F.pure(run(a)))

  def first[C](implicit F: Functor[F]): Service[F, (A, C), (B, C)] =
    Service{ case (a, c) => F.fproduct(run(a))(_ => c)}

  def second[C](implicit F: Functor[F]): Service[F, (C, A), (C, B)] =
    Service{ case (c, a) => F.map(run(a))(c -> _)}

  def apply(a: A): F[B] = run(a)
 */

  def kleisli(implicit ev: Task[Response] =:= Task[B]): Kleisli[Task, A, B] =
    Kleisli((a: A) => run.applyOrElse(a, (_: A) => ev(HttpService.notFound)))
}

object Service {
  def lift[A, B](f: A => Task[B]): Service[A, B] =
    Service(new PartialFunction[A, Task[B]] {
      def apply(a: A): Task[B] =
        f(a)
      def isDefinedAt(a: A): Boolean =
        true
    })

  def const[A, B](b: Task[B]): Service[A, B] =
    lift((_: A) => b)

  def constVal[A, B](b: B): Service[A, B] =
    const(Task.delay(b))
}

/*
object Service extends ServiceInstances with ServiceFunctions

private[data] sealed trait ServiceFunctions {

  def lift[F[_], A, B](x: F[B]): Service[F, A, B] =
    Service(_ => x)

  def pure[F[_], A, B](x: B)(implicit F: Applicative[F]): Service[F, A, B] =
    Service(_ => F.pure(x))

  def ask[F[_], A](implicit F: Applicative[F]): Service[F, A, A] =
    Service(F.pure)

  def local[M[_], A, R](f: R => R)(fa: Service[M, R, A]): Service[M, R, A] =
    Service(f andThen fa.run)
}

private[data] sealed abstract class ServiceInstances extends ServiceInstances0 {

  implicit def catsDataMonoidForService[F[_], A, B](implicit M: Monoid[F[B]]): Monoid[Service[F, A, B]] =
    new ServiceMonoid[F, A, B] { def FB: Monoid[F[B]] = M }

  implicit def catsDataMonoidKForService[F[_]](implicit M: Monad[F]): MonoidK[λ[α => Service[F, α, α]]] =
    new ServiceMonoidK[F] { def F: Monad[F] = M }

  implicit val catsDataMonoidKForServiceId: MonoidK[λ[α => Service[Id, α, α]]] =
    catsDataMonoidKForService[Id]

  implicit def catsDataArrowForService[F[_]](implicit ev: Monad[F]): Arrow[Service[F, ?, ?]] =
    new ServiceArrow[F] { def F: Monad[F] = ev }

  implicit val catsDataArrowForServiceId: Arrow[Service[Id, ?, ?]] =
    catsDataArrowForService[Id]

  implicit def catsDataChoiceForService[F[_]](implicit ev: Monad[F]): Choice[Service[F, ?, ?]] =
    new Choice[Service[F, ?, ?]] {
      def id[A]: Service[F, A, A] = Service(ev.pure)

      def choice[A, B, C](f: Service[F, A, C], g: Service[F, B, C]): Service[F, Either[A, B], C] =
        Service(_.fold(f.run, g.run))

      def compose[A, B, C](f: Service[F, B, C], g: Service[F, A, B]): Service[F, A, C] =
        f.compose(g)
    }

  implicit val catsDataChoiceForServiceId: Choice[Service[Id, ?, ?]] =
    catsDataChoiceForService[Id]

  implicit def catsDataMonadReaderForServiceId[A]: MonadReader[Service[Id, A, ?], A] =
    catsDataMonadReaderForService[Id, A]

  implicit def catsDataContravariantForService[F[_], C]: Contravariant[Service[F, ?, C]] =
    new Contravariant[Service[F, ?, C]] {
      override def contramap[A, B](fa: Service[F, A, C])(f: (B) => A): Service[F, B, C] =
        fa.local(f)
    }

  implicit def catsDataTransLiftForService[A]: TransLift.AuxId[Service[?[_], A, ?]] =
    new TransLift[Service[?[_], A, ?]] {
      type TC[M[_]] = Trivial

      def liftT[M[_], B](ma: M[B])(implicit ev: Trivial): Service[M, A, B] = Service.lift(ma)
    }

  implicit def catsDataApplicativeErrorForService[F[_], A, E](implicit AE: ApplicativeError[F, E]): ApplicativeError[Service[F, A, ?], E]
    = new ServiceApplicativeError[F, A, E] { implicit def AF: ApplicativeError[F, E]  = AE }
}

private[data] sealed abstract class ServiceInstances0 extends ServiceInstances1 {
  implicit def catsDataSplitForService[F[_]](implicit ev: FlatMap[F]): Split[Service[F, ?, ?]] =
    new ServiceSplit[F] { def F: FlatMap[F] = ev }

  implicit def catsDataStrongForService[F[_]](implicit ev: Functor[F]): Strong[Service[F, ?, ?]] =
    new ServiceStrong[F] { def F: Functor[F] = ev }

  implicit def catsDataFlatMapForService[F[_]: FlatMap, A]: FlatMap[Service[F, A, ?]] = new FlatMap[Service[F, A, ?]] {
    def flatMap[B, C](fa: Service[F, A, B])(f: B => Service[F, A, C]): Service[F, A, C] =
      fa.flatMap(f)

    def map[B, C](fa: Service[F, A, B])(f: B => C): Service[F, A, C] =
      fa.map(f)

    def tailRecM[B, C](b: B)(f: B => Service[F, A, Either[B, C]]): Service[F, A, C] =
      Service[F, A, C]({ a => FlatMap[F].tailRecM(b) { f(_).run(a) } })
  }

  implicit def catsDataRecursiveTailRecMForService[F[_]: RecursiveTailRecM, A]: RecursiveTailRecM[Service[F, A, ?]] =
    RecursiveTailRecM.create[Service[F, A, ?]]

  implicit def catsDataSemigroupForService[F[_], A, B](implicit M: Semigroup[F[B]]): Semigroup[Service[F, A, B]] =
    new ServiceSemigroup[F, A, B] { def FB: Semigroup[F[B]] = M }

  implicit def catsDataSemigroupKForService[F[_]](implicit ev: FlatMap[F]): SemigroupK[λ[α => Service[F, α, α]]] =
    new ServiceSemigroupK[F] { def F: FlatMap[F] = ev }

}

private[data] sealed abstract class ServiceInstances1 extends ServiceInstances2 {
  implicit def catsDataApplicativeForService[F[_], A](implicit A : Applicative[F]): Applicative[Service[F, A, ?]] = new ServiceApplicative[F, A] {
    implicit def F: Applicative[F] = A
  }
}

private[data] sealed abstract class ServiceInstances2 extends ServiceInstances3 {
  implicit def catsDataApplyForService[F[_]: Apply, A]: Apply[Service[F, A, ?]] = new Apply[Service[F, A, ?]] {
    def ap[B, C](f: Service[F, A, B => C])(fa: Service[F, A, B]): Service[F, A, C] =
      fa.ap(f)

    override def product[B, C](fb: Service[F, A, B], fc: Service[F, A, C]): Service[F, A, (B, C)] =
      Service(a => Apply[F].product(fb.run(a), fc.run(a)))

    def map[B, C](fa: Service[F, A, B])(f: B => C): Service[F, A, C] =
      fa.map(f)
  }
}

private[data] sealed abstract class ServiceInstances3 extends ServiceInstances4 {
  implicit def catsDataFunctorForService[F[_]: Functor, A]: Functor[Service[F, A, ?]] = new Functor[Service[F, A, ?]] {
    def map[B, C](fa: Service[F, A, B])(f: B => C): Service[F, A, C] =
      fa.map(f)
  }
}

private[data] sealed abstract class ServiceInstances4 {

  implicit def catsDataMonadReaderForService[F[_]: Monad, A]: MonadReader[Service[F, A, ?], A] =
    new MonadReader[Service[F, A, ?], A] {
      def pure[B](x: B): Service[F, A, B] =
        Service.pure[F, A, B](x)

      def flatMap[B, C](fa: Service[F, A, B])(f: B => Service[F, A, C]): Service[F, A, C] =
        fa.flatMap(f)

      val ask: Service[F, A, A] = Service(Monad[F].pure)

      def local[B](f: A => A)(fa: Service[F, A, B]): Service[F, A, B] =
        Service(f.andThen(fa.run))

      def tailRecM[B, C](b: B)(f: B => Service[F, A, Either[B, C]]): Service[F, A, C] =
        Service[F, A, C]({ a => FlatMap[F].tailRecM(b) { f(_).run(a) } })
    }
}

private trait ServiceArrow[F[_]] extends Arrow[Service[F, ?, ?]] with ServiceSplit[F] with ServiceStrong[F] {
  implicit def F: Monad[F]

  def lift[A, B](f: A => B): Service[F, A, B] =
    Service(a => F.pure(f(a)))

  def id[A]: Service[F, A, A] =
    Service(a => F.pure(a))

  override def second[A, B, C](fa: Service[F, A, B]): Service[F, (C, A), (C, B)] =
    super[ServiceStrong].second(fa)

  override def split[A, B, C, D](f: Service[F, A, B], g: Service[F, C, D]): Service[F, (A, C), (B, D)] =
    super[ServiceSplit].split(f, g)
}

private trait ServiceSplit[F[_]] extends Split[Service[F, ?, ?]] {
  implicit def F: FlatMap[F]

  def split[A, B, C, D](f: Service[F, A, B], g: Service[F, C, D]): Service[F, (A, C), (B, D)] =
    Service{ case (a, c) => F.flatMap(f.run(a))(b => F.map(g.run(c))(d => (b, d))) }

  def compose[A, B, C](f: Service[F, B, C], g: Service[F, A, B]): Service[F, A, C] =
    f.compose(g)
}

private trait ServiceStrong[F[_]] extends Strong[Service[F, ?, ?]] {
  implicit def F: Functor[F]

  override def lmap[A, B, C](fab: Service[F, A, B])(f: C => A): Service[F, C, B] =
    fab.local(f)

  override def rmap[A, B, C](fab: Service[F, A, B])(f: B => C): Service[F, A, C] =
    fab.map(f)

  override def dimap[A, B, C, D](fab: Service[F, A, B])(f: C => A)(g: B => D): Service[F, C, D] =
    fab.dimap(f)(g)

  def first[A, B, C](fa: Service[F, A, B]): Service[F, (A, C), (B, C)] =
    fa.first[C]

  def second[A, B, C](fa: Service[F, A, B]): Service[F, (C, A), (C, B)] =
    fa.second[C]
}

private trait ServiceSemigroup[F[_], A, B] extends Semigroup[Service[F, A, B]] {
  implicit def FB: Semigroup[F[B]]

  override def combine(a: Service[F, A, B], b: Service[F, A, B]): Service[F, A, B] =
    Service[F, A, B](x => FB.combine(a.run(x), b.run(x)))
}

private trait ServiceMonoid[F[_], A, B] extends Monoid[Service[F, A, B]] with ServiceSemigroup[F, A, B] {
  implicit def FB: Monoid[F[B]]

  override def empty: Service[F, A, B] = Service[F, A, B](a => FB.empty)
}

private trait ServiceSemigroupK[F[_]] extends SemigroupK[λ[α => Service[F, α, α]]] {
  implicit def F: FlatMap[F]

  override def combineK[A](a: Service[F, A, A], b: Service[F, A, A]): Service[F, A, A] = a compose b
}

private trait ServiceMonoidK[F[_]] extends MonoidK[λ[α => Service[F, α, α]]] with ServiceSemigroupK[F] {
  implicit def F: Monad[F]

  override def empty[A]: Service[F, A, A] = Service(F.pure[A])
}


private trait ServiceApplicativeError[F[_], A, E] extends ServiceApplicative[F, A] with ApplicativeError[Service[F, A, ?], E] {
  type K[T] = Service[F, A, T]

  implicit def AF: ApplicativeError[F, E]

  implicit def F: Applicative[F] = AF

  def raiseError[B](e: E): K[B] = Service(_ => AF.raiseError(e))

  def handleErrorWith[B](kb: K[B])(f: E => K[B]): K[B] = Service { a: A =>
    AF.handleErrorWith(kb.run(a))((e: E) => f(e).run(a))
  }

}


private trait ServiceApplicative[F[_], A] extends Applicative[Service[F, A, ?]] {
  implicit def F: Applicative[F]

  def pure[B](x: B): Service[F, A, B] =
    Service.pure[F, A, B](x)

  def ap[B, C](f: Service[F, A, B => C])(fa: Service[F, A, B]): Service[F, A, C] =
    fa.ap(f)

  override def map[B, C](fb: Service[F, A, B])(f: B => C): Service[F, A, C] =
    fb.map(f)

  override def product[B, C](fb: Service[F, A, B], fc: Service[F, A, C]): Service[F, A, (B, C)] =
    Service(a => Applicative[F].product(fb.run(a), fc.run(a)))
}
 */

object Fart {
  val service = HttpService {
    case req => Task.now(Response())
  }
}
