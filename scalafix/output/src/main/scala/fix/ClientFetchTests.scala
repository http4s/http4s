package fix

import cats.data.Chain
import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.client.Client

object ClientFetchTests {

  trait MyClientTrait[G[_]] extends Client[G] {
    final def doFetchR1[A](req: Request[G], f: Response[G] => G[A])(implicit G: Sync[G]): G[A] =
      run(req).use(f)

    final def doFetchFR1[A](req: G[Request[G]], f: Response[G] => G[A])(implicit G: Sync[G]): G[A] =
      req.flatMap(this.run(_).use[G, A](f))

    final def doFetchR2[B](req: Request[G], f: Response[G] => G[B])(implicit G: Sync[G]): G[B] =
      run(req).use[G, B](f)

    final def doFetchFR2[B](req: G[Request[G]], f: Response[G] => G[B])(implicit G: Sync[G]): G[B] =
      req.flatMap(this.run(_).use(f))

    final def doFetchR3[A](f: Response[G] => G[A])(implicit G: Sync[G]): G[A] =
      run(Request[G]()).use(f(_))

    final def doFetchFR3[A](f: Response[G] => G[A])(implicit G: Sync[G]): G[A] =
      G.pure(Request[G]()).flatMap(run(_).use(f(_)))

    final def doFetchR4[B](f: Response[G] => G[B])(implicit G: Sync[G]): G[B] =
      // The applied type `B` is not going to be preserved.
      this.run(Request[G]()).use(f.apply)

    final def doFetchFR4[B](f: Response[G] => G[B])(implicit G: Sync[G]): G[B] =
      // The applied type `B` is not going to be preserved.
      G.pure(Request[G]()).flatMap(this.run(_).use(f.apply))
  }

  abstract class MyClient[F[_]: Sync] extends MyClientTrait[F] {
    override def fetch[A](req: Request[F])(f: Response[F] => F[A]): F[A] = this.run(req).use(f)
    override def fetch[A](req: F[Request[F]])(f: Response[F] => F[A]): F[A] =
      req.flatMap(this.run(_).use(f))

    final def doMoreFetchR1[A](req: Request[F], f: Response[F] => F[A]): F[A] =
      this.run(req).use[F, A](f)

    final def doMoreFetchFR1[B](req: F[Request[F]], f: Response[F] => F[B]): F[B] =
      req.flatMap(this.run(_).use[F, B](f))
  }

  /** Pretends to look like the real `Client[F]` */
  trait AlienClient[F[_]] {
    def run(req: Request[F]): Resource[F, Response[F]]
    def fetch[A](req: Request[F])(f: Response[F] => F[A]): F[A]
    def fetch[A](req: F[Request[F]])(f: Response[F] => F[A]): F[A]
  }

  //
  // Tests for `fetch(req: Request[F])`
  //

  def doClientFetchR1[F[_]: Sync, A](
      myClient: Client[F],
      req: Request[F],
      f: Response[F] => F[A]): F[A] =
    myClient.run(req).use[F, A](f)

  def doClientFetchR2[G[_]: Sync, B](
      `my-client`: MyClient[G],
      req: Request[G],
      f: Response[G] => G[B]): G[B] =
    `my-client`.run(req).use(f)

  def doClientFetchR3[H[_]: Sync, B](
      client: MyClientTrait[H],
      req: Request[H],
      f: Response[H] => H[B]): H[B] =
    client.run(req).use[H, B](res => f(res))

  def doClientFetchR4[G[_]: Sync, A](
      client: Client[G],
      req: Request[G],
      f: Response[G] => G[A]): G[A] =
    client.run(req).use(res => f(res))

  def doClientFetchR5[F[_]: Sync, A](
      client: Client[F],
      req: Request[F],
      f: Response[F] => F[A]): F[A] =
    client.run(req).use[F, A] { case res @ Response(_, _, _, _, _) => f(res) }

  def doClientFetchR6[G[_]: Sync, B](
      clients: Chain[MyClientTrait[G]],
      req: Request[G],
      f: Response[G] => G[B]): G[Chain[B]] =
    clients.traverse(_.run(req).use(f))

  def doClientFetchR7[H[_]: Sync, B](
      clients: Chain[MyClient[H]],
      req: Request[H],
      f: Response[H] => H[B]): H[Chain[B]] =
    clients.traverse(_.run(req).use[H, B](f))

  def doClientFetchR8[G[_]: Sync, A](
      myClient: Client[G],
      req: Request[G],
      f: Response[G] => G[A]): (Response[G] => G[A]) => G[A] =
    myClient.run(req).use(_)

  def doClientFetchR9[F[_]: Sync, A](
      cli: Client[F],
      getReq: () => Request[F],
      f: Response[F] => F[A]): F[A] =
    cli.run(getReq()).use(f)

  def doClientFetchR10[G[_]: Sync, B](
      cli: Client[G],
      getReq: () => Request[G],
      f: Response[G] => G[B]): G[B] =
    cli.run(getReq()).use[G, B](f)

  def doClientFetchR11[H[_]: Sync, B](
      cli: Client[H],
      getReq: () => Request[H],
      f: Response[H] => H[B]): H[B] =
    cli.run {
      val req = getReq()
      req
    }.use[H, B](f)

  //
  // Tests for `fetch(req: F[Request[F]])`
  //

  def doClientFetchFR1[G[_]: Sync, A](
      myClient: MyClientTrait[G],
      req: G[Request[G]],
      f: Response[G] => G[A]): G[A] =
    req.flatMap(myClient.run(_).use(f))

  def doClientFetchFR2[F[_]: Sync, A](
      myClient: MyClient[F],
      req: F[Request[F]],
      f: Response[F] => F[A]): F[A] =
    req.flatMap(myClient.run(_).use(f))

  def doClientFetchFR3[H[_]: Sync, B](
      client: Client[H],
      req: H[Request[H]],
      f1: Response[H] => H[B],
      f2: Response[H] => H[B]): H[B] =
    req.flatMap(client.run(_).use {
      case res if res.status.isSuccess => f1(res)
      case res => f2(res)
    })

  def doClientFetchFR4[G[_]: Sync, B](
      clients: Chain[MyClient[G]],
      req: G[Request[G]],
      f: Response[G] => G[B]): G[Chain[B]] =
    clients.traverse(client => req.flatMap(client.run(_).use(f)))

  def doClientFetchFR5[F[_]: Sync, A](
      clients: Chain[MyClientTrait[F]],
      req: F[Request[F]],
      f: Response[F] => F[A]): F[Chain[A]] =
    clients.traverse(client => req.flatMap(client.run(_).use(f)))

  def doClientFetchFR6[H[_]: Sync, A](
      cl: MyClient[H],
      getReq: () => H[Request[H]],
      f: Response[H] => H[A]): H[A] =
    getReq().flatMap(cl.run(_).use[H, A](f))

  //
  // Negative tests
  //

  def doNegClientFetch1[F[_], A](
      myClient: Client[F],
      req: Request[F],
      f: Response[F] => F[A]): (Response[F] => F[A]) => F[A] =
    myClient.fetch(req)

  def doNegClientFetch2[F[_], A](
      myClient: Client[F],
      req: F[Request[F]],
      f: Response[F] => F[A]): (Response[F] => F[A]) => F[A] =
    myClient.fetch[A](req)

  def doNegClientFetch3[F[_], A](
      client: AlienClient[F],
      req: Request[F],
      f: Response[F] => F[A]): F[A] =
    client.fetch(req)(f)

  def doNegClientFetch4[F[_], A](
      client: AlienClient[F],
      req: F[Request[F]],
      f: Response[F] => F[A]): F[A] =
    client.fetch[A](req)(f)
}
