package bb.mixer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.util.Timeout
import scala.collection.concurrent.{TrieMap}
import scala.concurrent.duration._
import akka.http.scaladsl.marshallers.sprayjson._
import spray.json._

import scala.io.StdIn

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val outAccounts = jsonFormat2(MixerOutAddresses)
  implicit val mixThis = jsonFormat3(MixFundsIn)
  implicit val transaction = jsonFormat4(Transaction)
  implicit val transactions = jsonFormat2(Transactions)
  implicit val statReq = jsonFormat1(StatusRequest)
}


object MixerMain extends JsonSupport {

  val config = Configs

  val host = config.host
  val port = config.port

  val addressInToMixerIn = TrieMap[String, String]()
  val addressInToMixerOut = TrieMap[String, Seq[String]]() //mix funds, send to these
  val mixerInToAddressIn = TrieMap[String, String]()

  def main(args: Array[String]): Unit = {

    mixerInToAddressIn.update(config.houseMixAddress, config.houseGlobalInAddress) //TODO make this real via config or soemthing
    addressInToMixerOut.update(config.houseGlobalInAddress, Seq(config.houseMainAddress)) //TODO do we need this?

    implicit val system = ActorSystem("mixer")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val requestHandler = system.actorOf(RequestHandler.props(), "requestHandler")

    val txPoller = system.actorOf(TxLogPoller.props(), "txPoller") //start another child actor to poll the tx logs

    //Define the route
    val route: Route = {

      implicit val timeout = Timeout(20.seconds)

      path("api") {
        pathEndOrSingleSlash {
          complete(StatusCodes.OK,
            s"""All mixers are operational.
               |
               |How to use:
               | First you need to fund your address:  Use https://jobcoin.gemini.com/puppy
               |
               | To be assigned a mixer use this URL: http://$host:$port/api/assignmixer
               | To start a mixing session use this URL: http://$host:$port/api/mixfunds
               | To see the status of your mixer use this URL: http://$host:$port/api/mixstatus/<mixer address>
               |
               |Example mixing using curl:
               |
               | curl -H "Content-Type: application/json" -d '{ "fromAddress" : "MyAddress", "addresses" : ["alt1", "alt2", "alt3"] }' http://$host:$port/api/assignmixer
               |   --- the above will response w/ the mixer address you should use for this fromAddress going forward.
               | curl -H "Content-Type: application/json" -d '{ "fromAddress" : "MyAddress", "mixerAddress" : "mixerIn1", "amount" : "75" }' http://$host:$port/api/mixfunds
               |   --- the above will send your funds from fromAddress to the mixer address you specify (above, mixerIn1).
               |                  *** BE CERTAIN THE MIXER ADDRESS IS CORRECT OR YOU FORFEIT YOUR FUNDS! ***
               |
               |You can refresh this URL http://$host:$port/api/mixstatus/mixerIn1 to see the status of your mixer (ie, balance and all transactions)
             """.stripMargin)
        }
      } ~
        path("api" / "assignmixer") {
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
        } ~
        path("api" / "mixstatus" / Segment) { address =>
          get {
            onSuccess(requestHandler ? StatusRequest(address)) {
              case response: Response => {
                complete(StatusCodes.OK, response.payload)
              }
              case err: Error => {
                complete(StatusCodes.OK, err.error)
              }
              case _ =>
                complete(StatusCodes.InternalServerError)
            }
          }
        }
    }


    //Startup, and listen for requests
    val bindingFuture = Http().bindAndHandle(route, host, port)

    //set up a transaction polling schedule
    val cancellable = system.scheduler.schedule(5 seconds, Duration(config.pollInterval, SECONDS), txPoller, "poll")

    println(s"Waiting for requests at http://$host:$port/...\nHit RETURN to terminate")

    StdIn.readLine()

    //Shutdown
    bindingFuture.flatMap(_.unbind())
    cancellable.cancel
    system.terminate()

  }

}
