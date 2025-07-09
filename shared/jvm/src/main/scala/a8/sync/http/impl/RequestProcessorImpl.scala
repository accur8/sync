package a8.sync.http.impl


import a8.shared
import a8.shared.SharedImports
import a8.sync.http.*
import a8.shared.SharedImports.{*, given}
import a8.shared.json.JsonCodec
import a8.sync.Semaphore

case class RequestProcessorImpl(
  config: RequestProcessorConfig,
  backend: Backend,
  maxConnectionSemaphore: Semaphore,
)
  extends RequestProcessor
{

  import config.{maxConnections, retryConfig}

  override def execWithStringResponseBody[A](request: Request, responseBodyFn: String => A)(using Logger): A = {
    val responseActionFn =
      standardResponseActionProcessor(
        response => ResponseAction.Success(response.responseBodyAsString)
      )
    val responseBodyStr = execWithResponseActionFn(request, responseActionFn)
    responseBodyFn(responseBodyStr)
  }

  override def execWithJsonResponse[A: JsonCodec](request: Request)(using JsonResponseOptions, _root_.a8.shared.SharedImports.Logger): A = {

    def responseBodyFn(jsonStr: String): A =
      json.unsafeRead[A](jsonStr)

    execWithStringResponseBody(request, responseBodyFn)
  }

  //  !!!
//  override def execWithStringResponseBody[A](request: Request, responseBodyFn: String => A)(using Logger): A = {
//    def responseImpl(response: Response): Either[Throwable, A] = {
//      response
//        .raiseResponseErrors
//        .map(Left(_))
//        .getOrElse {
//          try {
//            Right(responseBodyFn(response.responseBodyAsString))
//          } catch {
//            case th: Throwable =>
//              Left(th)
//          }
//        }
//    }
//
//    execWithEffect(request, standardResponseProcessor(responseImpl))
//  }
//
//  def execWithResponse[A](request: Request, responseFn: Response => Attempt[A])(implicit trace: Trace, logger: Logger): Attempt[A] = {
//    def wrappedResponseFn(response: Response): Attempt[A] = {
//      response.raiseResponseErrors *> responseEffect(response)
//    }
//
//    execWithEffect(request, standardResponseProcessor(wrappedResponseEffect))
//  }
//
//  override def execWithEffect[A](request: Request, responseEffect: Either[Throwable, Response] => ResponseAction[A])(using logger: Logger): Result[A] = {
//    request match {
//      case r0: RequestImpl =>
//        runWithRetry(r0, responseEffect)
//    }
//  }

  /**
   * Will exec the request and map the response allowing for error's in the map'ing to
   * trigger a retry.
   *
   * This lifts the mapFn to run inside the scope for the retry so you can do things
   * like self manage recovery and further validating the response (like the response
   * may be json but is it the json you actually want).
   *
   */
  override def execWithResponseActionFn[A](request: Request, responseFn: Either[Throwable,Response] => ResponseAction[A])(using shared.SharedImports.Logger): A =
    !!!

  def runWithRetry[A](request: RequestImpl, responseTestFn: Either[Throwable, Response] => ResponseAction[A])(using logger: Logger): Result[A] = {

    val resolvedRetry = request.retryConfig.getOrElse(retryConfig)
    val schedule = RetryConfig.toOxSchedule(resolvedRetry)

    val resultPolicy =
      ox.resilience.ResultPolicy[Throwable, ResponseAction[A]](
        isSuccess = ra => ra.isSuccess,
        isWorthRetrying = _ => false,
      )

    def afterAttempt(attempt: Int, result: Either[Throwable,ResponseAction[A]]): Unit = {
      !!!
//      responseAction match {
//        case ResponseAction.Success(_) =>
//          logger.debug(s"retry attempt ${attempt} succeeded with response ${response.responseMetadata.statusCode.code} -- ${response.responseMetadata.statusText}")
//        case ResponseAction.Retry(_) =>
//          logger.debug(s"retry attempt ${attempt} will retry response ${response.responseMetadata.statusCode.code} -- ${response.responseMetadata.statusText}")
//        case f@ResponseAction.Fail(_, _) =>
//          logger.debug(s"retry attempt ${attempt} failed with response ${response.responseMetadata.statusCode.code} -- ${response.responseMetadata.statusText} -- context: ${f.context} -- responseInfo: ${f.responseInfo.map(_.compactJson).getOrElse("none")}")
//      }
    }

    val oxRetryConfig =
      ox.resilience.RetryConfig(
        schedule,
        resultPolicy,
        afterAttempt,
      )

    def impl: ResponseAction[A] = {
      val result = runSingleRequest(request)
      responseTestFn(result.toEither)
    }

    ox.resilience.retry(oxRetryConfig)(impl) match {
      case ResponseAction.Success(v) =>
        Result.Success(v)
      case ResponseAction.Fail(_, _) =>
        Result.Failure()
      case ResponseAction.Retryable(msg) =>
        Result.Failure(message = Some(s"reached end of retries - ${msg}"))
    }

  }

  def runSingleRequest[A](rawRequest: RequestImpl)(using logger: Logger): Result[Response] = {
    val rawRequestNoEffects: Request = rawRequest.copy(effects = Vector.empty)
    val resolvedRequest: Request =
      rawRequest
        .effects
        .foldLeft(rawRequestNoEffects)((r, fn) =>
          fn(r)
        )
    maxConnectionSemaphore
      .withPermit {
        backend.runSingleRequest(resolvedRequest.asInstanceOf[RequestImpl])
      }
  }

}
