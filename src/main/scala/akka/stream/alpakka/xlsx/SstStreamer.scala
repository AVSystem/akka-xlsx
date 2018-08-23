package akka.stream.alpakka.xlsx

import java.util.zip.ZipFile

import akka.stream.Materializer
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.alpakka.xml.{Characters, EndElement, ParseEvent, StartElement}
import akka.stream.contrib.ZipInputStreamSource.ZipEntryData
import akka.stream.scaladsl.{Keep, Sink, Source, StreamConverters}
import akka.util.ByteString

import scala.concurrent.Future

object SstStreamer {

  private final val EntryName = "xl/sharedStrings.xml"
  private val defaultSink = Sink.fold[Map[Int, String], (Int, String)](Map.empty)((v1, v2) => v1 + v2)

  def readSst(zipFile: ZipFile)(implicit materializer: Materializer): Future[Map[Int, String]] = {
    readSst(zipFile, defaultSink)
  }

  def readSst(
      zipFile: ZipFile,
      mapSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer): Future[Map[Int, String]] = {
    Option(zipFile.getEntry(EntryName)) match {
      case Some(entry) => read(StreamConverters.fromInputStream(() => zipFile.getInputStream(entry)), mapSink)
      case None        => Source.empty[(Int, String)].toMat(mapSink)(Keep.right).run()
    }
  }

  def readSst(source: Iterable[(ZipEntryData, ByteString)])(implicit materializer: Materializer): Future[Map[Int, String]] = {
    readSst(source, defaultSink)
  }

  def readSst(
      source: Iterable[(ZipEntryData, ByteString)],
      mapSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer): Future[Map[Int, String]] = {
    read(
      Source.fromIterator(() => source.iterator.collect { case (zipEntry, bytes) if zipEntry.name == EntryName => bytes }),
      mapSink
    )
  }


  private def read(
      inputSource: Source[ByteString, _],
      mapSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer) = {
    inputSource
      .via(XmlParsing.parser)
      .statefulMapConcat[(Int, String)](() => {
        var count                              = 0
        var si                                 = false
        var lastContent: Option[StringBuilder] = None
        (data: ParseEvent) =>
          data match {
            case StartElement("si", _, _, _, _) =>
              si = true
              Nil
            case EndElement("si") =>
              si = false
              lastContent match {
                case Some(builder) =>
                  val ret = (count, builder.toString())
                  lastContent = None
                  count += 1
                  ret :: Nil
                case None =>
                  Nil
              }
            case Characters(text) if si =>
              lastContent match {
                case Some(t) => t.append(text)
                case None    => lastContent = Some(new StringBuilder().append(text))
              }
              Nil
            case _ => Nil
          }
      })
      .toMat(mapSink)(Keep.right)
      .run()
  }

}
