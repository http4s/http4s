package org.http4s.load

import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.http.Predef._
import scala.concurrent.duration._

class DemoSimulation extends Simulation {

  val httpConf = http.baseURL(s"http://demo.http4s.org")

  val getHomeScenario =
    scenario("Get Home")
      .repeat(5, "n") {
        exec(http("get ${n}").get("/#/home"))
        .pause(2)
    }

  setUp(
    getHomeScenario.inject(rampUsers(100) over (2.seconds))
  ).protocols(httpConf)

}
