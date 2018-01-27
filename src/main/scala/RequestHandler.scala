
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

  var incInt = 0 //increment int for mixer addresses

  def receive: Receive = {

//    case GetTransactions =>
//      log.debug("Received Tx Request")
//      sender() ! {
//        if(getAllJobCoinTransactions.code != 200) Error else Response(getAllJobCoinTransactions.body)
//      }
    case moa: MixerOutAccounts => {  //TODO generate a dummyMixer account and pass it along
      context.actorOf(MixerService.props()) ! MixThis(moa.primaryAccount, "dummyMixer1", moa.accounts) //TODO send a MixThis class which includes the incInt as part of the mixer in address
      sender ! Response(s"Sent your info to the Mixer.  Your mixer address is dummyMixer1.  Please send funds to be mixed to that address. Thanks")
    }
    case Request => {
      println("got here")
      sender() ! Response("ok")
    }
    case _ => println("error");Error

  }
}
