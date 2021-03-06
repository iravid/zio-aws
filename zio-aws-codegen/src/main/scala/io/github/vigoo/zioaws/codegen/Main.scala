package io.github.vigoo.zioaws.codegen

import io.github.vigoo.clipp.ParserFailure
import io.github.vigoo.clipp.zioapi._
import io.github.vigoo.zioaws.codegen.generator.GeneratorFailure
import io.github.vigoo.zioaws.codegen.loader.ModelId
import zio._

object Main extends App {
  sealed trait Error
  case class ReflectionError(reason: Throwable) extends Error
  case class ParserError(error: ParserFailure) extends Error
  case class GeneratorError(error: GeneratorFailure) extends Error

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val app = for {
      svcs <- config.parameters[Parameters].map(_.serviceList)
      ids <- svcs match {
        case Some(ids) => ZIO.succeed(ids.toSet)
        case None => loader.findModels().mapError(ReflectionError)
      }
      _ <- ZIO.foreachPar(ids) { id =>
        for {
          _ <- console.putStrLn(s"Generating $id")
          model <- loader.loadCodegenModel(id).mapError(ReflectionError)
          _ <- generator.generateServiceCode(id, model).mapError(GeneratorError)
        } yield ()
      }
      _ <- console.putStrLn(s"Generating build.sbt")
      _ <- generator.generateBuildSbt(ids).mapError(GeneratorError)
      _ <- console.putStrLn(s"Copying zio-aws-core")
      _ <- generator.copyCoreProject().mapError(GeneratorError)
    } yield ExitCode.success

    val cfg = config.fromArgsWithUsageInfo(args, Parameters.spec).mapError(ParserError)
    val modules = loader.live ++ (cfg >+> generator.live)
    app.provideCustomLayer(modules).catchAll {
      case ReflectionError(exception) =>
        console.putStrErr(exception.toString).as(ExitCode.failure)
      case ParserError(parserFailure) =>
        console.putStrLnErr(s"Parser failure: $parserFailure").as(ExitCode.failure)
      case GeneratorError(generatorError) =>
        console.putStrLnErr(s"Code generator failure: ${generatorError}").as(ExitCode.failure)
    }
  }
}
