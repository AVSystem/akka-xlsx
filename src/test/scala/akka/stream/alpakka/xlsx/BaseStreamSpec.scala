package akka.stream.alpakka.xlsx

import org.apache.pekko.actor.ActorSystem
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

trait BaseStreamSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  protected implicit val system: ActorSystem = ActorSystem("test")

  override protected def afterAll(): Unit = {
    Await.ready(system.terminate(), 5.seconds)
    super.afterAll()
  }
}