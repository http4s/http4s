import com.typesafe.tools.mima.core._
import com.typesafe.tools.mima.plugin.MimaKeys.binaryIssueFilters

name := "http4s-server"

description := "Server bindings for http4s"

libraryDependencies ++= Seq(
  metricsCore
)

mimaSettings

mimaReportSettings

binaryIssueFilters ++= Seq(
  ProblemFilters.exclude[IncompatibleResultTypeProblem]("org.http4s.server.staticcontent.MemoryCache.org$http4s$server$staticcontent$MemoryCache$$cacheMap")
)