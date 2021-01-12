package akka.stream.alpakka.xlsx

import org.scalacheck.{Gen, Shrink}
import org.scalatest.enablers.Sequencing._
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.annotation.nowarn
import scala.collection.immutable.TreeMap
import scala.util.Try

final class RowTest extends WordSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  val column1 = Cell.Text("column1", CellReference("A2", 1, 2))
  val column2 = Cell.Text("column2", CellReference("B2", 2, 2))
  val column4 = Cell.Numeric(BigDecimal(2137), CellReference("D2", 4, 2))
  val column5 = Cell.Blank(CellReference("E2", 5, 2))
  val column7 = Cell.Bool(false, CellReference("G2", 7, 2))

  val nonEmptyRow = new Row(rowIndex = 2, columnIndexToCell = TreeMap(
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

    "not crash when row is empty" in {
      val emptyRow = Try(new Row(rowIndex = 2, columnIndexToCell = TreeMap.empty))
        .getOrElse(fail("Exception was thrown during creation of Row with empty Cell map"))

      emptyRow.cells.size shouldEqual 0
      emptyRow.simpleCells.size shouldEqual 0
    }

    "contain all the cells from input TreeMap in Row.cells" in {
      val rowIndex = 1
      val cellMapGen: Gen[TreeMap[Int, Cell]] = Gen.buildableOf[TreeMap[Int, Cell], (Int, Cell)] {
        Gen.posNum[Int].map { columnIndex =>
          columnIndex -> Cell.Text("w/e", CellReference.generateReference(columnIndex, rowIndex))
        }
      }

      @nowarn //up-to-date scalacheck uses deprecated Stream instead of LazyList for now
      implicit def shrink[A] = Shrink[A](_ => Stream.empty) //scalacheck's shrink is useless for custom gens

      forAll(cellMapGen) { cellMap =>
        val row = new Row(rowIndex, cellMap)
        val cellsSeq = row.cells
        cellsSeq.size shouldEqual Try(cellMap.keys.max).getOrElse(0)
        cellMap.foreach { case (idx, cell) =>
          cellsSeq(idx - 1) shouldEqual cell
        }
      }
    }
  }
}
