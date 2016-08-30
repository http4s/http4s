package com.example.http4s

import org.http4s.Uri
import org.http4s.Uri.IPv4
import org.http4s.zipkin.core.Endpoint

import scalaz.concurrent.Task

package object zipkin {
  def getConfig: Task[Config] = for {
    endpoint <- getEndpointFromConsole
    nextServiceName <- getNextServiceNameConsole
  } yield Config(
    endpoint = endpoint,
    nextServiceName = nextServiceName)

  def getNextServiceNameConsole: Task[String] = Task.delay {
    scala.Console.readLine("Enter next service name: ")
  }

  def getEndpointFromConsole: Task[Endpoint] = {
    def getPort: Task[Int] = Task.delay {
      Integer.parseInt(scala.Console.readLine("Enter port: "))
    }

    def getAddress: Task[String] = Task.delay {
      scala.Console.readLine("Enter IP: ")
    }

    def getServiceName: Task[String] = Task.delay {
      scala.Console.readLine("Enter server name: ")
    }

    for {
      ip <- getAddress
      port <- getPort
      name <- getServiceName
    } yield Endpoint(ip, None, port, name)
  }

  type ServiceName = String
  type UrlString = String

  def fakeServiceDiscovery(name: ServiceName): Task[Uri] = Task.now {
    val host = IPv4("127.0.0.1")
    val port = name match {
      case "two" => 8082
      case "three" => 8083
    }
    Uri(authority =
      Option(Uri.Authority(host = host, port = Option(port))))
  }


}
