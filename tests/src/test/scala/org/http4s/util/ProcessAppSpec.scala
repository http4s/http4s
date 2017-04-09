package org.http4s
package util

import scalaz.concurrent.Task
import scalaz.stream.Process

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class ProcessAppSpec extends Http4sSpec {

  "ProcessApp" should {
    "Terminate Server on a Process Failure" in {
      val myError = new Throwable("Bad Initial Process")
      class TestApp extends ProcessApp {
        override def main(args: List[String]): Process[Task, Unit] = {
          Process.fail(myError)
        }
      }
      new TestApp().main(Array.empty[String]) should_== (())
    }

    "Terminate Server on a Valid Process" in {
      class TestApp extends ProcessApp {
        override def main(args: List[String]): Process[Task, Unit] = {
          Process.empty[Task, String].append(Process.emit("Valid Process")).map(_ => ())
        }
      }
      new TestApp().main(Array.empty[String]) should_== (())
    }

    "Terminate Server on a Bad Task" in {
      val myError = new Throwable("Bad Task")
      class TestApp extends ProcessApp {
        override def main(args: List[String]): Process[Task, Unit] = {
          Process.eval(Task.fail(myError))
        }
      }
      new TestApp().main(Array.empty[String]) should_== (())
    }

    "Terminate Server on a Valid Task" in {
      var touched = false
      class TestApp extends ProcessApp {
        override def main(args: List[String]): Process[Task, Unit] = {
          Process.eval(Task{touched = true; ()})
        }
      }
      new TestApp().main(Array.empty[String]) should_== (())
    }

    "requestShutdown Shuts Down a Server From A Separate Thread" in {
      class TestApp extends ProcessApp {
        override def main(args: List[String]): Process[Task, Unit] = {
          Process.constant(())
        }
      }
      val testApp = new TestApp()
      val testAppF = Future(testApp.main(Array.empty[String]))
      testApp.requestShutdown.unsafePerformSync

      Await.result(testAppF, 60 seconds) should_== (())
    }
  }

}
