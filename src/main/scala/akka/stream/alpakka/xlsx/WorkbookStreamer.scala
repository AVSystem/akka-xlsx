package akka.stream.alpakka.xlsx


import org.apache.pekko.connectors.xlsx.ZipInputStreamSource.ZipEntryData
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.connectors.xml.javadsl.XmlParsing
import org.apache.pekko.stream.connectors.xml.{EndElement, ParseEvent, StartElement}
import org.apache.pekko.stream.scaladsl.{Source, StreamConverters}
import org.apache.pekko.util.ByteString

import java.io.FileNotFoundException
import java.util.zip.ZipFile
import scala.concurrent.Future
import scala.util.Try

object WorkbookStreamer {

  private final val EntryName = "xl/workbook.xml"
  private final val WorkbookNotFoundExceptionMsg = "Workbook entry could not be found."

  def readWorkbook(zipFile: ZipFile)(implicit materializer: Materializer): Future[Map[String, Int]] = {
    Option(zipFile.getEntry(EntryName))
      .map(entry => read(StreamConverters.fromInputStream(() => zipFile.getInputStream(entry))))
      .getOrElse(Future.failed(new FileNotFoundException(WorkbookNotFoundExceptionMsg)))
  }

  def readWorkbook(source: Iterable[(ZipEntryData, ByteString)])(implicit materializer: Materializer): Future[Map[String, Int]] = {
    val filteredIterator = source.iterator.collect { case (zipEntry, bytes) if zipEntry.name == EntryName => bytes }
    if (filteredIterator.isEmpty) Future.failed(new FileNotFoundException(WorkbookNotFoundExceptionMsg))
    else read(Source.fromIterator(() => filteredIterator))
  }


  private def read(inputSource: Source[ByteString, _])(implicit materializer: Materializer): Future[Map[String, Int]] = {
    inputSource
      .via(XmlParsing.parser())
      .statefulMapConcat[(String, Int)](() => {
        var insideSheets: Boolean = false
        (data: ParseEvent) =>
          data match {
            case StartElement("sheets", _, _, _, _) =>
              insideSheets = true
              Nil
            case StartElement("sheet", attrs, _, _, _) if insideSheets =>
              val nameValue = attrs.find(_.name == "name").map(_.value)
              val idValue   = attrs.find(_.name == "sheetId").flatMap(a => Try(Integer.parseInt(a.value)).toOption)
              val sheet = for {
                name <- nameValue
                id   <- idValue
              } yield (name, id)
              sheet match {
                case Some(s) => s :: Nil
                case None    => Nil
              }
            case EndElement("sheet") => // ignored since we can get everything from attrs?
              Nil
            case EndElement("sheets") =>
              insideSheets = false
              Nil
            case _ => Nil
          }
      })
      .runFold(Map.empty[String, Int])((v1, v2) => v1 + v2)
  }

}
