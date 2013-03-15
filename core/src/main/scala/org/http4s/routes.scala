package org.http4s

import concurrent.ExecutionContext

object Routes {
   def apply(route: Route)(implicit executor: ExecutionContext = ExecutionContext.global) = new Routes(route)
}
class Routes(val route: Route)(implicit executor: ExecutionContext = ExecutionContext.global)
