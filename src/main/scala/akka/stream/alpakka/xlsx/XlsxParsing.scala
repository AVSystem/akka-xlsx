package akka.stream.alpakka.xlsx


import org.apache.pekko.NotUsed
import org.apache.pekko.connectors.xlsx.ZipInputStreamSource
import org.apache.pekko.connectors.xlsx.ZipInputStreamSource.ZipEntryData
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.connectors.xml.scaladsl.XmlParsing
import org.apache.pekko.stream.connectors.xml.{Characters, EndElement, StartElement}
import org.apache.pekko.stream.scaladsl.{Sink, Source, StreamConverters}
import org.apache.pekko.util.ByteString

import java.io.{FileNotFoundException, PipedInputStream, PipedOutputStream}
import java.util.zip.{ZipFile, ZipInputStream}
import scala.collection.immutable.TreeMap
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object XlsxParsing {

  def fromZipFile(file: ZipFile, sheetId: Int)(implicit materializer: Materializer): Source[Row, NotUsed] = {
    fromZipFile(file, sheetId, SstStreamer.defaultSink)
  }

  def fromZipFile(file: ZipFile, sheetName: String)(implicit materializer: Materializer): Source[Row, NotUsed] = {
    fromZipFile(file, sheetName, SstStreamer.defaultSink)
  }

  def fromZipFile(
      file: ZipFile,
      sheetId: Int,
      sstSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer): Source[Row, NotUsed] = {
    readFromFile(file, SheetType.Id(sheetId), sstSink)
  }

  def fromZipFile(
      file: ZipFile,
      sheetName: String,
      sstSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer): Source[Row, NotUsed] = {
    readFromFile(file, SheetType.Name(sheetName), sstSink)
  }

  def fromStream(
      source: Source[ByteString, _],
      sheetId: Int
  )(implicit materializer: Materializer, executionContext: ExecutionContext): Source[Row, NotUsed] = {
    fromStream(source, sheetId, SstStreamer.defaultSink)
  }

  def fromStream(
      source: Source[ByteString, _],
      sheetName: String
  )(implicit materializer: Materializer, executionContext: ExecutionContext): Source[Row, NotUsed] = {
    fromStream(source, sheetName, SstStreamer.defaultSink)
  }

  def fromStream(
      source: Source[ByteString, _],
      sheetId: Int,
      sstSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer, executionContext: ExecutionContext): Source[Row, NotUsed] = {
    fromStream(source, SheetType.Id(sheetId), sstSink)
  }

  def fromStream(
      source: Source[ByteString, _],
      sheetName: String,
      sstSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer, executionContext: ExecutionContext): Source[Row, NotUsed] = {
    fromStream(source, SheetType.Name(sheetName), sstSink)
  }

  private def fromStream(
      source: Source[ByteString, _],
      sheetType: SheetType,
      sstSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer, executionContext: ExecutionContext): Source[Row, NotUsed] = {
    val nextStepInputStream = new PipedInputStream()
    //Don't inline! Pipe needs to be connected before reading, otherwise `java.io.IOException: Pipe not connected` is possible
    val outputStream = new PipedOutputStream(nextStepInputStream)
    source.to(StreamConverters.fromOutputStream(() => outputStream)).run()
    readFromStream(ZipInputStreamSource(() => new ZipInputStream(nextStepInputStream)), sheetType, sstSink)
  }


  private def nillable(s: => Any): List[Row] = {
    s
    Nil
  }

  private def buildCell(
      typ: Option[CellType],
      value: Option[String],
      lastFormula: Option[String],
      numFmtId: Option[Int],
      workbook: Workbook,
      ref: CellReference
  ): Cell = {
    def parseValue = {
      // the default cell type is always NUMERIC
      typ.getOrElse(CellType.NUMERIC) match {
        case CellType.BLANK                     => Cell.Blank(ref)
        case CellType.INLINE | CellType.FORMULA => Cell.parseInline(value, ref)
        case CellType.STRING                    => Cell.parseString(value, workbook.sst, ref)
        case CellType.BOOLEAN                   => Cell.parseBoolean(value, ref)
        case CellType.ERROR                     => Cell.Error(new Exception("cell type is invalid"), ref)
        case CellType.NUMERIC                   => Cell.parseNumeric(value, numFmtId.flatMap(id => workbook.styles.get(id)), ref)
      }
    }
    lastFormula match {
      case Some(formula) => Cell.Formula(parseValue, formula, ref)
      case None          => parseValue
    }
  }

  private def sheetEntryName(sheetType: SheetType, workbook: Workbook) = {
    def sheetEntryName(sheetId: Int) = s"xl/worksheets/sheet$sheetId.xml"

    sheetType match {
      case SheetType.Name(sheetName) => workbook.sheets.get(sheetName).map(sheetEntryName)
      case SheetType.Id(sheetId) => Some(sheetEntryName(sheetId))
    }
  }

  private def worksheetNotFoundExceptionMsg(sheetType: SheetType) = s"Workbook sheet $sheetType could not be found."

  private def readFromFile(
      file: ZipFile,
      sheetType: SheetType,
      sstSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer) = {
    val workbookSource = Source
      .future(SstStreamer.readSst(file, sstSink))
      .flatMapConcat(sst => Source.future(StyleStreamer.readStyles(file)).map((sst, _)))
      .flatMapConcat { case (sst, styles) =>
        Source.future(WorkbookStreamer.readWorkbook(file)).map(sheets => Workbook(sst, sheets, styles))
      }
    val sheetSourceCreator =
      (workbook: Workbook) => StreamConverters.fromInputStream(() =>
        sheetEntryName(sheetType, workbook)
          .flatMap(sheet => Option(file.getEntry(sheet)))
          .map(file.getInputStream)
          .getOrElse(throw new FileNotFoundException(worksheetNotFoundExceptionMsg(sheetType)))
      )
    read(workbookSource, sheetSourceCreator)
  }

  private def readFromStream(
      source: Source[(ZipEntryData, ByteString), Future[Long]],
      sheetType: SheetType,
      sstSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer, executionContext: ExecutionContext) = {
    val zipEntries = source.runWith(Sink.seq)
    val workbookSource = Source
      .future(zipEntries.flatMap(SstStreamer.readSst(_, sstSink)))
      .flatMapConcat(sst => Source.future(zipEntries.flatMap(StyleStreamer.readStyles)).map((sst, _)))
      .flatMapConcat { case (sst, styles) =>
        Source.future(zipEntries.flatMap(WorkbookStreamer.readWorkbook)).map(sheets => Workbook(sst, sheets, styles))
      }
    val sheetSourceCreator =
      (workbook: Workbook) => Source.futureSource {
        val optionalSheetName = sheetEntryName(sheetType, workbook)
        zipEntries.map { source =>
          Source.fromIterator(() => {
            val filteredIterator = source.iterator.collect { case (zipEntry, bytes) if optionalSheetName.contains(zipEntry.name) => bytes }
            if (filteredIterator.isEmpty) throw new FileNotFoundException(worksheetNotFoundExceptionMsg(sheetType))
            filteredIterator
          })
        }
      }
    read(workbookSource, sheetSourceCreator)
  }

  private def read(workbookSource: Source[Workbook, _], sheetSourceCreator: Workbook => Source[ByteString, _]) = {
    workbookSource.flatMapConcat { workbook =>
      sheetSourceCreator(workbook)
        .via(XmlParsing.parser)
        .statefulMapConcat[Row](() => {
          var insideRow: Boolean = false
          var insideCol: Boolean = false
          var insideValue: Boolean = false
          var insideFormula: Boolean = false
          var cellType: Option[CellType] = None
          var lastContent: Option[String] = None
          var lastFormula: Option[String] = None
          var cellList: mutable.Builder[(Int, Cell), TreeMap[Int, Cell]] = TreeMap.newBuilder
          var rowNum = 1
          var cellNum = 1
          var ref: Option[CellReference] = None
          var numFmtId: Option[Int] = None

          {
            case StartElement("row", _, _, _, _) =>
              nillable { insideRow = true }
            case StartElement("c", attrs, _, _, _) if insideRow =>
              nillable {
                ref = CellReference.parseReference(attrs)
                numFmtId = attrs.find(_.name == "s").flatMap(a => Try(Integer.parseInt(a.value)).toOption)
                cellType = attrs.find(_.name == "t").map(a => CellType.parse(a.value))
                insideCol = true
              }
            case StartElement("v", _, _, _, _) if insideCol =>
              nillable { insideValue = true }
            case StartElement("f", _, _, _, _) if insideCol =>
              nillable { insideFormula = true }
            case Characters(text) if insideValue =>
              nillable { lastContent = Some(lastContent.map(_ + text).getOrElse(text)) }
            case Characters(text) if insideFormula =>
              nillable { lastFormula = Some(lastFormula.map(_ + text).getOrElse(text)) }
            case EndElement("v") if insideValue =>
              nillable { insideValue = false }
            case EndElement("f") if insideFormula =>
              nillable { insideFormula = false }
            case EndElement("c") if insideCol =>
              nillable {
                val simpleRef = ref.getOrElse(CellReference("", cellNum, rowNum))
                val cell = buildCell(cellType, lastContent, lastFormula, numFmtId, workbook, simpleRef)
                cellList += (simpleRef.columnIndex -> cell)
                numFmtId = None
                ref = None
                cellNum += 1
                insideCol = false
                cellType = None
                lastContent = None
                lastFormula = None
              }
            case EndElement("row") if insideRow =>
              val ret = new Row(rowNum, cellList.result())
              rowNum += 1
              cellNum = 1
              cellList = TreeMap.newBuilder
              insideRow = false
              ret :: Nil
            case _ => Nil // ignore unused stuff
          }
        })
    }
  }

}
