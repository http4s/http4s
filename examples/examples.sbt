name := "http4s-examples"

description := "Common code for http4s examples on various backends"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.7"
)

// Adds ALPN to the boot classpath for HTTP/2 support
javaOptions in run ++= {
   for {
     file <- (managedClasspath in Runtime).value.map(_.data)
     path = file.getAbsolutePath if path.contains("jetty.alpn")
   } yield "-Xbootclasspath/p:" + path
}

publishArtifact := false
