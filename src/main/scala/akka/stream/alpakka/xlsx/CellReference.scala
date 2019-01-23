package akka.stream.alpakka.xlsx

import java.util.Locale

import akka.stream.alpakka.xml.Attribute

import scala.annotation.tailrec
import scala.util.Try

case class CellReference(name: String, colNum: Int, rowNum: Int)

object CellReference {

  private final val BaseLettersNum = 26
  private final val AsciiCodeA = 65

  private val CELL_REF = """(\s+"""

  private def convertColStringToIndex(ref: String): Int = {
    @tailrec
    def convert(refChars: List[Char], retval: Int): Int = {
      refChars.headOption match {
        case Some(char) =>
          // `c - AsciiCodeA` converts base capital letters to consecutive numbers (starting from 0), ie. 'A' -> 0, 'B' -> 1, ...
          convert(refChars.tail, retval * BaseLettersNum + (char - AsciiCodeA) + 1)
        case None => retval
      }
    }

    convert(ref.toUpperCase(Locale.ROOT).toCharArray.toList, 0)
  }

  private def splitCellRef(ref: String) = {
    val refLength = ref.length
    val refColumnPart       = ref.filterNot(c => c >= '0' && c <= '9')
    val refColumnPartLength = refColumnPart.length

    if (refLength == 0 || refColumnPartLength == refLength) None
    else Some((ref, refColumnPart, ref.substring(refColumnPartLength)))
  }

  private[xlsx] def parseRef(attrs: List[Attribute]): Option[CellReference] = {
    attrs
      .find(_.name == "r")
      .flatMap { attr =>
        splitCellRef(attr.value)
      }
      .flatMap {
        case (ref, s1, s2) =>
          Try(CellReference(ref, convertColStringToIndex(s1), Integer.parseInt(s2))).toOption
      }
  }

  private def convertIndexToColString(num: Int): String = {
    def solver(rest: Int, s: String): String = {
      if (rest == 0) {
        s
      } else {
        val (f, r) = {
          if (rest > 26) (rest / 26, rest % 26)
          else (rest, 0)
        }

        solver(r, s + (f + 65 - 1).toChar)
      }
    }
    solver(num, "")
  }

  private[xlsx] def generateRef(rowNum: Int, cellNum: Int): CellReference = {
    val first = convertIndexToColString(cellNum)
    CellReference(first + rowNum, cellNum, rowNum)
  }

}
