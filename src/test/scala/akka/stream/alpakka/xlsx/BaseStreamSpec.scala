package akka.stream.alpakka.xlsx

import akka.actor.ActorSystem
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait BaseStreamSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  protected implicit val system: ActorSystem = ActorSystem("test")

  override protected def afterAll(): Unit = {
    Await.ready(system.terminate(), 5.seconds)
    super.afterAll()
  }
}