
name := "http4s-blazecore"

description := "blaze core for client and server backends for http4s"

fork := true

libraryDependencies ++= Seq(
  blaze
)

mimaSettings ++ {
  import com.typesafe.tools.mima.core._
  import com.typesafe.tools.mima.core.ProblemFilters._
  import com.typesafe.tools.mima.plugin.MimaKeys.binaryIssueFilters
  Seq(
    binaryIssueFilters ++= Seq(
      ProblemFilters.exclude[MissingMethodProblem]("org.http4s.blaze.websocket.Http4sWSStage.org$http4s$blaze$websocket$Http4sWSStage$$alive"),
      ProblemFilters.exclude[MissingMethodProblem]("org.http4s.blaze.websocket.Http4sWSStage.org$http4s$blaze$websocket$Http4sWSStage$$alive_="),
      ProblemFilters.exclude[MissingMethodProblem]("org.http4s.blaze.websocket.Http4sWSStage.org$http4s$blaze$websocket$Http4sWSStage$$go$1")
    )
  )
}
