
package bb.mixer

import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.HttpTransactions._


object RequestHandler {
  def props(): Props = {
    Props(classOf[RequestHandler])
  }
}

case class Request(request: String)
case object GetTransactions
case class Response(payload: String)
case object Error

class RequestHandler extends Actor with ActorLogging {


  def receive: Receive = {

//    case GetTransactions =>
//      log.debug("Received Tx Request")
//      sender() ! {
//        if(getAllJobCoinTransactions.code != 200) Error else Response(getAllJobCoinTransactions.body)
//      }
    case moa: MixerOutAccounts => {
      context.actorOf(MixerService.props()) ! moa
      sender ! Response("Sent OutAccounts to Mixer")
    }
    case Request => {
      println("got here")
      sender() ! Response("ok")
    }
    case _ => println("error");Error

  }
}
