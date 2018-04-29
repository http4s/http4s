package controllers

import cats.effect.IO
import com.example.http4s.ExampleService
import fs2.Scheduler
import javax.inject.Inject
import org.http4s.server.play.PlayRouteBuilder
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter

import scala.concurrent.ExecutionContext

class Http4sRouter @Inject()(implicit executionContext: ExecutionContext) extends SimpleRouter {

  private implicit val scheduler: Scheduler = {
    val (sched, _) = Scheduler
      .allocate[IO](corePoolSize = Http4sRouter.PoolSize, threadPrefix = Http4sRouter.ThreadPrefix)
      .unsafeRunSync()
    sched
  }

  override def routes: Routes = new PlayRouteBuilder[IO](new ExampleService[IO].service).build
}

object Http4sRouter {
  val PoolSize = 4
  val ThreadPrefix = "http4s-play-scheduler"
}
