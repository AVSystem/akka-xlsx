package akka.stream.alpakka.xlsx

import akka.stream.alpakka.xml.Attribute
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class CellReferenceTest extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  "CellReference" should {
    "return None if a cell reference attribute is missing" in {
      // case: no reference attribute
      CellReference.parseReference(List.empty) shouldBe None
      // case: type attribute referring to a Shared String Table item
      CellReference.parseReference(List(Attribute("t", "s"))) shouldBe None
    }

    "return None for an illegal reference" in {
      // case: empty reference
      CellReference.parseReference(List(Attribute("r", ""))) shouldBe None
      // case: empty column part
      CellReference.parseReference(List(Attribute("r", "12"))) shouldBe None
      // case: empty row part
      CellReference.parseReference(List(Attribute("r", "B"))) shouldBe None
      // case: illegal column pattern
      CellReference.parseReference(List(Attribute("r", "Ć1"))) shouldBe None
      CellReference.parseReference(List(Attribute("r", "AJĆ1"))) shouldBe None
      // case: illegal row pattern
      CellReference.parseReference(List(Attribute("r", "A0"))) shouldBe None
      CellReference.parseReference(List(Attribute("r", "A-10"))) shouldBe None
      CellReference.parseReference(List(Attribute("r", "A+10"))) shouldBe None
      // case: illegal characters
      CellReference.parseReference(List(Attribute("r", ".A1"))) shouldBe None
      CellReference.parseReference(List(Attribute("r", "A:1"))) shouldBe None
      CellReference.parseReference(List(Attribute("r", "A1,"))) shouldBe None
      CellReference.parseReference(List(Attribute("r", "\"A1"))) shouldBe None
      CellReference.parseReference(List(Attribute("r", "A1\""))) shouldBe None
      CellReference.parseReference(List(Attribute("r", "A 1"))) shouldBe None
      // case: illegal combination of legitimate characters
      CellReference.parseReference(List(Attribute("r", "1A"))) shouldBe None
      CellReference.parseReference(List(Attribute("r", "A1A"))) shouldBe None
      CellReference.parseReference(List(Attribute("r", "1A1"))) shouldBe None
    }

    "create appropriate objects for valid references" in {
      def columnStringGenerator(length: Int) = Gen.listOfN(length, Gen.alphaUpperChar).map(_.mkString)

      val cellReferences = for {
        columnString <- Gen.frequency((1 to 5).map(length => (1, columnStringGenerator(length))): _*)
        rowIndex     <- Gen.posNum[Int]
      } yield (columnString, rowIndex)

      forAll(cellReferences) { case (columnString, rowIndex) =>
        whenever(columnString.nonEmpty && rowIndex > 0) {
          val combinedReference = columnString + rowIndex
          val columnIndex = columnString.reverseIterator
            .zipWithIndex
            .foldLeft(0) { case (result, (letter, index)) => result + (letter - 64) * math.pow(26, index).toInt }

          CellReference.parseReference(List(Attribute("r", combinedReference))) shouldBe
            Option(CellReference(combinedReference, columnIndex, rowIndex))
        }
      }
    }
  }

  it should {
    "throw IllegalArgumentException on invalid input" in {
      // case: illegal column index
      an[IllegalArgumentException] shouldBe thrownBy { CellReference.generateReference(-1, 5) }
      an[IllegalArgumentException] shouldBe thrownBy { CellReference.generateReference(0, 5) }
      // case: illegal row index
      an[IllegalArgumentException] shouldBe thrownBy { CellReference.generateReference(5, -1) }
      an[IllegalArgumentException] shouldBe thrownBy { CellReference.generateReference(5, 0) }
    }

    "create appropriate references for valid input" in {
      val inputIndices = for {
        columnIndex <- Gen.posNum[Int]
        rowIndex    <- Gen.posNum[Int]
      } yield (columnIndex, rowIndex)

      forAll(inputIndices) { case (columnIndex, rowIndex) =>
        whenever(columnIndex > 0 && rowIndex > 0) {
          val columnString = {
            var currentIndex  = columnIndex
            val resultBuilder = new StringBuilder()

            while (currentIndex > 0) {
              currentIndex -= 1
              resultBuilder.append((currentIndex % 26 + 65).toChar)
              currentIndex /= 26
            }
            resultBuilder.reverse.toString
          }

          CellReference.generateReference(columnIndex, rowIndex) shouldBe
            CellReference(columnString + rowIndex, columnIndex, rowIndex)
        }
      }
    }
  }
}
