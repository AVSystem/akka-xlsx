package akka.stream.alpakka.xlsx

import scala.collection.immutable.TreeMap

final class Row(val rowIndex: Int, columnIndexToCell: TreeMap[Int, Cell]) {
  /**
    * @return An `Iterable` of all non-`Blank` cells. Please note that values contained by this `Iterable` are
    *         guaranteed to be returned according to ascending order of column numbers, however two consecutive items do
    *         not necessarily represent cells of adjacent columns.
    */
  def simpleCells: Iterable[Cell] = columnIndexToCell.values

  /**
    * @return A `Sequence` of all cells ordered by ascending column numbers. Two consecutive items of this `Seq`
    *         correspond to cells of adjacent columns. If a cell is missing in the `columnIndexToCell` mapping, a `Blank`
    *         cell is created with reference generated by `CellReference#generateReference`.
    */
  def cells: Seq[Cell] = (1 to columnIndexToCell.keys.lastOption.getOrElse(0))
    .map(columnIndex => columnIndexToCell.getOrElse(columnIndex, Cell.Blank(CellReference.generateReference(columnIndex, rowIndex))))

  /**
    * @param columnIndex cell number to be returned, should not be less than 1
    * @return            `Some(cell)` corresponding to an XLSX cell located in `rowIndex`-th row and `columnIndex`-th
    *                    column if it's non-empty; `None` otherwise
    */
  def getCell(columnIndex: Int): Option[Cell] = columnIndexToCell.get(columnIndex)

  override def toString: String = s"Row($rowIndex, $cells)"
}
