package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directive0, Directives, Route}
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.ui.process.ProcessObjectsFinder
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessRepository}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import shapeless.syntax.typeable._

import scala.concurrent.{ExecutionContext, Future}

class SignalsResources(modelData: Map[String, ModelData],
                       processRepository: FetchingProcessRepository,
                       val processAuthorizer:AuthorizeProcess)
                      (implicit ec: ExecutionContext)
  extends Directives
    with Argonaut62Support
    with RouteWithUser
    with AuthorizeProcessDirectives {

  import pl.touk.nussknacker.ui.codec.UiCodecs._

  def route(implicit user: LoggedUser): Route = {
    pathPrefix("signal" / Segment / Segment) { (signalType, processId) =>
      canDeploy(processId) {

        post {
          //Map[String, String] should be enough for now
          entity(as[Map[String, String]]) { params =>
            complete {
              processRepository.fetchLatestProcessDetailsForProcessId(processId).map[ToResponseMarshallable] {
                case Some(process) =>
                  modelData(process.processingType).dispatchSignal(signalType, processId, params.mapValues(_.asInstanceOf[AnyRef]))
                case None =>
                  HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
              }
            }
          }
        }
      }
    } ~ path("signal") {
      get {
        complete {
          prepareSignalDefinitions
        }
      }
    }
  }

  private def prepareSignalDefinitions(implicit user: LoggedUser): Future[Map[String, SignalDefinition]] = {
    //TODO: only processes that are deployed right now??
    processRepository.fetchAllProcessesDetails().map { processList =>
      ProcessObjectsFinder.findSignals(processList, modelData.values.map(_.processDefinition))
    }
  }

}


//TODO: when parameters are List[Parameter] there is some argonaut issue
case class SignalDefinition(name: String, parameters: List[String], availableProcesses: List[String])
