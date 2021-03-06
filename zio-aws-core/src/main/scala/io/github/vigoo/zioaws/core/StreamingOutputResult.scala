package io.github.vigoo.zioaws.core

import zio.Chunk
import zio.stream.ZStream

case class StreamingOutputResult[Response](response: Response,
                                           output: ZStream[Any, AwsError, Chunk[Byte]]) {
  def map[R](f: Response => R): StreamingOutputResult[R] =
    copy(response = f(response))
}
