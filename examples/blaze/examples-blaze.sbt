name := "http4s-examples-blaze"

description := "Runs the examples in http4s' blaze runner"

publishArtifact := false

fork := true

libraryDependencies += metricsJson

seq(Revolver.settings: _*)




seq(
  libraryDependencies += alpn_boot,
  // Adds ALPN to the boot classpath for Spdy support
  javaOptions in run <++= (managedClasspath in Runtime) map { attList =>
    for {
      file <- attList.map(_.data)
      path = file.getAbsolutePath if path.contains("jetty.alpn")
    } yield { println(path); "-Xbootclasspath/p:" + path}
  }
)