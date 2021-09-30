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
      val referenceColumn = reference.filterNot(c => c >= '0' && c <= '9')

      if (reference.isEmpty || referenceColumn.isEmpty || referenceColumn.length == reference.length) None
      else Some((reference, referenceColumn, reference.substring(referenceColumn.length)))
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

      Try(convert(column.toUpperCase(Locale.ROOT), 0))
    }

    def convertRowStringToIndex(row: String) = Try(row.toInt).filter(_ > 0)

    attributes.find(_.name == "r")
      .flatMap(referenceAttribute => splitCellReference(referenceAttribute.value))
      .flatMap { case (reference, referenceColumn, referenceRow) =>
        (for {
          columnIndex <- convertColumnStringToIndex(referenceColumn)
          rowIndex    <- convertRowStringToIndex(referenceRow)
        } yield CellReference(reference, columnIndex, rowIndex)).toOption
      }
  }

  @throws[IllegalArgumentException]("if either columnIndex or rowIndex is not positive")
  private[xlsx] def generateReference(columnIndex: Int, rowIndex: Int): CellReference = {
    require(columnIndex > 0 && rowIndex > 0)

    val columnString = {
      @tailrec
      def convert(indexRest: Int, currentString: String): String = {
        if (indexRest == 0) currentString
        else {
          val (lastLetterNum, remainder) = ((indexRest - 1) % BaseLettersNum, (indexRest - 1) / BaseLettersNum)
          convert(remainder, s"${(lastLetterNum + AsciiCodeA).toChar}$currentString")
        }
      }

      convert(columnIndex, "")
    }

    CellReference(columnString + rowIndex.toString, columnIndex, rowIndex)
  }
}
