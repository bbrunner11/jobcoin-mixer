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
  implicit val outAccounts = jsonFormat2(MixerOutAddresses) // MixerOutAccounts[String, List[String])
  implicit val mixThis = jsonFormat3(MixFundsIn) // MixThis(String, String, String)
}


object MixerMain extends JsonSupport {

  val host = "localhost"
  val port = 8080

  val addressInToMixerIn = scala.collection.concurrent.TrieMap[String, String]()
  val addressInToMixerOut = scala.collection.concurrent.TrieMap[String, Seq[String]]()
  val primaryToLastActivity = scala.collection.concurrent.TrieMap[String, java.util.Date]() //TODO get rid of this?
  val mixerInToAddressIn = scala.collection.concurrent.TrieMap[String, String]()

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem("mixer")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val requestHandler = system.actorOf(RequestHandler.props(), "requestHandler")

    val txPoller = system.actorOf(TxLogPoller.props(), "txPoller") //start another child actor to poll the tx logs

    //Define the route
    val route: Route = {

      implicit val timeout = Timeout(20.seconds)

      path("api" / "health") {
        get {
          complete(StatusCodes.OK, "Everything is great!")
        }
      } ~
        path("api" / "startmixer") {
          post {
            entity(as[MixerOutAddresses]) { mixerOut =>
              onSuccess(requestHandler ? mixerOut) {
                case response: Response => {
                  complete(StatusCodes.OK, response.payload)
                }
                case err: Error => {
                  complete(StatusCodes.OK, err.error)
                }
                case _ =>
                  complete(StatusCodes.InternalServerError)
              }
              //TODO need a failure directive here
            }
          }
        } ~
        path("api" / "mixfunds") {
          post {
            entity(as[MixFundsIn]) { mixThis =>
              onSuccess(requestHandler ? mixThis) {
                case response: Response => {
                  complete(StatusCodes.OK, response.payload)
                }
                case err: Error => {
                  complete(StatusCodes.OK, err.error)
                }
                case _ => complete(StatusCodes.InternalServerError)
              }
            }
          }
        }


    }

    //Startup, and listen for requests
    val bindingFuture = Http().bindAndHandle(route, host, port)

    //set up a transaction polling schedule
    val cancellable = system.scheduler.schedule(0 milliseconds, 15000 milliseconds, txPoller, "poll") //TODO make this a Runnable and send a message so threads are queued??

    println(s"Waiting for requests at http://$host:$port/...\nHit RETURN to terminate")

      StdIn.readLine()

      //Shutdown
      bindingFuture.flatMap(_.unbind())
      cancellable.cancel
      system.terminate()

  }

}
