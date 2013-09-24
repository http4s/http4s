package org.http4s

object Routes {
   def apply[F[_]](route: HttpService[F]) = new Routes(route)
}
class Routes[F[_]](val route: HttpService[F])
