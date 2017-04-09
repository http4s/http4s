package org.http4s
package util

import scalaz.concurrent.Task
import scalaz.stream.Process

class ProcessAppSpec extends Http4sSpec {

  "ProcessApp" should {
    "Terminate Server on a Initial Process" in {
      val myError = new Throwable("Bad Initial Process")
      class TestApp extends ProcessApp {
        override def main(args: List[String]): Process[Task, Unit] = {
          Process.fail(myError)
        }
      }
      new TestApp().main(Array.empty[String]) should_== (())
    }

    "Terminate Server on a Bad Second Value" in {
      val myError = new Throwable("Bad Secondary Process")
      class TestApp extends ProcessApp {
        override def main(args: List[String]): Process[Task, Unit] = {
          Process.emit(()).append(Process.fail(myError))
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

    "Terminate Server on Bad Task" in {
      val myError = new Throwable("Bad Task")
      class TestApp extends ProcessApp {
        override def main(args: List[String]): Process[Task, Unit] = {
          Process.eval(Task.fail(myError))
        }
      }
      new TestApp().main(Array.empty[String]) should_== (())
    }

    "Terminate On A Valid Task" in {
      class TestApp extends ProcessApp {
        override def main(args: List[String]): Process[Task, Unit] = {
          Process.emit(Task("Valid Task"))
        }
      }
      new TestApp().main(Array.empty[String]) should_== (())
    }
  }

}
