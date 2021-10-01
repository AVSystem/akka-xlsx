package akka.stream.alpakka.xlsx

import java.util.zip.ZipFile
import akka.stream.Materializer
import akka.stream.alpakka.xlsx.ZipInputStreamSource.ZipEntryData
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.alpakka.xml.{Characters, EndElement, StartElement}
import akka.stream.scaladsl.{Keep, Sink, Source, StreamConverters}
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

object SstStreamer {
  private final val EntryName = "xl/sharedStrings.xml"
  private final val StringItemTag = "si"

  private[xlsx] val defaultSink = Sink.seq[(Int, String)].mapMaterializedValue(_.map(_.toMap)(ExecutionContext.fromExecutor(_.run())))


  def readSst(zipFile: ZipFile)(implicit materializer: Materializer): Future[Map[Int, String]] = {
    readSst(zipFile, defaultSink)
  }

  def readSst(
      zipFile: ZipFile,
      mapSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer): Future[Map[Int, String]] = {
    Option(zipFile.getEntry(EntryName))
      .map(entry => read(StreamConverters.fromInputStream(() => zipFile.getInputStream(entry)), mapSink))
      .getOrElse(Future.successful(Map.empty))
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
        var sharedStringIndex                          = 0
        var isInsideStringItemTag                      = false
        var sharedStringBuilder: Option[StringBuilder] = None

        {
          case StartElement(StringItemTag, _, _, _, _) =>
            isInsideStringItemTag = true
            Nil
          case EndElement(StringItemTag) =>
            val sharedStringEntry = sharedStringBuilder match {
              case Some(builder) =>
                sharedStringBuilder = None
                (sharedStringIndex, builder.toString()) :: Nil
              case None =>
                Nil
            }
            isInsideStringItemTag = false
            sharedStringIndex += 1
            sharedStringEntry
          case Characters(text) if isInsideStringItemTag =>
            sharedStringBuilder match {
              case Some(builder) => builder.append(text)
              case None          => sharedStringBuilder = Some(new StringBuilder(text))
            }
            Nil
          case _ => Nil
        }
      })
      .toMat(mapSink)(Keep.right)
      .run()
  }
}
