package akka.stream.alpakka.xlsx

import org.scalatest.enablers.Sequencing._
import org.scalatest.{ Matchers, WordSpec }

import scala.collection.mutable

final class RowTest extends WordSpec with Matchers {
  val column1 = Cell.Text("column1", CellReference("A2", 1, 2))
  val column2 = Cell.Text("column2", CellReference("B2", 2, 2))
  val column4 = Cell.Numeric(BigDecimal(2137), CellReference("D2", 4, 2))
  val column5 = Cell.Blank(CellReference("E2", 5, 2))
  val column7 = Cell.Bool(false, CellReference("G2", 7, 2))

  val nonEmptyRow = new Row(rowIndex = 2, columnIndexToCell = mutable.TreeMap(
    4 -> column4, 2 -> column2, 1 -> column1, 7 -> column7, 5 -> column5
  ))

  "Row" should {
    "return all defined columns ordered according to ascending column numbers" in {
      val simpleCells = nonEmptyRow.simpleCells.toSeq
      simpleCells.size shouldBe 5
      simpleCells should contain inOrderOnly (column1, column2, column4, column5, column7)
    }

    "return a sequence of all columns with missing ones converted to `Blank`s, according to ascending column numbers" in {
      val cells = nonEmptyRow.cells
      cells.size shouldBe 7
      cells should contain inOrderOnly
      (column1, column2, Cell.Blank(CellReference("C2", 3, 2)), column4, column5, Cell.Blank(CellReference("F2", 6, 2)), column7)
    }
  }
}
