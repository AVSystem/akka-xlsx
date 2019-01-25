package akka.stream.alpakka.xlsx

import java.util.Locale

import akka.stream.alpakka.xml.Attribute

import scala.annotation.tailrec
import scala.util.Try

final case class CellReference(fullName: String, columnIndex: Int, rowIndex: Int)

object CellReference {
  private final val BaseLettersNum = ('A' to 'Z').size
  private final val AsciiCodeA     = 'A'
  private final val AsciiCodeZ     = 'Z'


  private[xlsx] def parseReference(attributes: List[Attribute]): Option[CellReference] = {
    def splitCellReference(reference: String) = {
      val referenceLength       = reference.length
      val referenceColumn       = reference.filterNot(c => c >= '0' && c <= '9')
      val referenceColumnLength = referenceColumn.length

      if (referenceLength == 0 || referenceColumnLength == 0 || referenceColumnLength == referenceLength) None
      else Some((reference, referenceColumn, reference.substring(referenceColumnLength)))
    }

    def convertColumnStringToIndex(column: String) = {
      @tailrec
      def convert(referencePart: String, currentIndex: Int): Int = {
        referencePart.headOption match {
          case Some(char) if AsciiCodeA <= char && char <= AsciiCodeZ =>
            // `char - AsciiCodeA` converts base capital letters to consecutive numbers (starting from 0), ie. 'A' -> 0, 'B' -> 1, ...
            convert(referencePart.tail, currentIndex * BaseLettersNum + (char - AsciiCodeA) + 1)
          case Some(invalidLetterNum) =>
            throw new IllegalArgumentException(s"Unexpected letter of code $invalidLetterNum found.")
          case None => currentIndex
        }
      }

      convert(column.toUpperCase(Locale.ROOT), 0)
    }

    def convertRowStringToIndex(row: String) = Try(row.toInt).filter(_ > 0).get

    attributes.find(_.name == "r")
      .flatMap(referenceAttribute => splitCellReference(referenceAttribute.value))
      .flatMap { case (reference, referenceColumn, referenceRow) =>
        Try(CellReference(reference, convertColumnStringToIndex(referenceColumn), convertRowStringToIndex(referenceRow))).toOption
      }
  }

  @throws[IllegalArgumentException]("if either columnIndex or rowIndex is not positive")
  private[xlsx] def generateReference(columnIndex: Int, rowIndex: Int): CellReference = {
    require(columnIndex > 0 && rowIndex > 0)

    val columnString = {
      def extractLetterNum(number: Int): (Int, Int) = (number % BaseLettersNum, number / BaseLettersNum)

      @tailrec
      def convert(indexRest: Int, currentString: String): String = {
        if (indexRest == 0) currentString
        else {
          val (lastLetterNum, remainder) = extractLetterNum(indexRest - 1)
          convert(remainder, (lastLetterNum + AsciiCodeA).toChar + currentString)
        }
      }

      convert(columnIndex, "")
    }

    CellReference(columnString + rowIndex.toString, columnIndex, rowIndex)
  }
}
