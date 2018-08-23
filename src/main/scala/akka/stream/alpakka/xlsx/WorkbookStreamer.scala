package akka.stream.alpakka.xlsx

import java.util.zip.ZipFile

import akka.stream.Materializer
import akka.stream.alpakka.xml.javadsl.XmlParsing
import akka.stream.alpakka.xml.{EndElement, ParseEvent, StartElement}
import akka.stream.contrib.ZipInputStreamSource.ZipEntryData
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString

import scala.concurrent.Future
import scala.util.Try

object WorkbookStreamer {

  private final val EntryName = "xl/workbook.xml"
  private final val InvalidFileExceptionMsg = "Invalid xlsx file - no workbook entry"

  def readWorkbook(zipFile: ZipFile)(implicit materializer: Materializer): Future[Map[String, Int]] = {
    Option(zipFile.getEntry(EntryName)) match {
      case Some(entry) => read(StreamConverters.fromInputStream(() => zipFile.getInputStream(entry)))
      case None        => Future.failed(new Exception(InvalidFileExceptionMsg))
    }
  }

  def readWorkbook(source: Iterable[(ZipEntryData, ByteString)])(implicit materializer: Materializer): Future[Map[String, Int]] = {
    read(Source.fromIterator(() => {
      val filteredIterator = source.iterator.collect { case (zipEntry, bytes) if zipEntry.name == EntryName => bytes }
      if (filteredIterator.isEmpty) throw new Exception(InvalidFileExceptionMsg)
      filteredIterator
    }))
  }


  private def read(inputSource: Source[ByteString, _])(implicit materializer: Materializer): Future[Map[String, Int]] = {
    inputSource
      .via(XmlParsing.parser)
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
