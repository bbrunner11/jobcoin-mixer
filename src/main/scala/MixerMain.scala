package bb.mixer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import akka.http.scaladsl.marshallers.sprayjson._
import spray.json._
import scala.io.StdIn

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val accounts = jsonFormat2(MixerOutAccounts) // contains List[String]
}


object MixerMain extends JsonSupport {

  val host = "localhost"
  val port = 8080

  val primaryToMixerIn = scala.collection.concurrent.TrieMap[String, String]()

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem("mixer")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val requestHandler = system.actorOf(RequestHandler.props(), "requestHandler")

    //Define the route
    val route: Route = {

      implicit val timeout = Timeout(20.seconds)

      path("api" / "mixme") {
        get {
          complete(StatusCodes.OK, "Everything is great!")
        }
      } ~
        post {
          entity(as[MixerOutAccounts]) { mixerOut =>
            onSuccess(requestHandler ? mixerOut) {
              case response: Response => {
                complete(StatusCodes.OK, response.payload)
              }
              case _ =>
                complete(StatusCodes.InternalServerError)
            }
            //TODO need a failure directive here
          }

        }
      //}
    }




    //      path("transactions") {
    //        get {
    //          onSuccess(requestHandler ? GetTransactions) {
    //            case response: Response =>
    //              complete(StatusCodes.OK, response.payload)
    //            case _ =>
    //              complete(StatusCodes.InternalServerError)
    //          }
    //        }
    //      }
    //
    //      path("startmixer") {
    //
    //        post {
    //          entity(as[MixerOutAccounts]) { mixerOut =>
    //            onSuccess(requestHandler ? mixerOut) {
    //              case response: Response => {
    //               // mixerService ! mixerOut //start mixer, TODO check for Success??
    //                complete(StatusCodes.OK, response.payload)
    //              }
    //              case _ =>
    //                complete(StatusCodes.InternalServerError)
    //            }
    //          }
    //        }
    //      }


    //Startup, and listen for requests
    val bindingFuture = Http().bindAndHandle(route, host, port)
    println(s"Waiting for requests at http://$host:$port/...\nHit RETURN to terminate")
    StdIn.readLine()

    //Shutdown
    bindingFuture.flatMap(_.unbind())
    system.terminate()
  }

}
