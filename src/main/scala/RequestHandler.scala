
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
case class Error(error: String)

class RequestHandler extends Actor with ActorLogging {

  var incInt = 0 //increment int for mixer addresses

  def receive: Receive = {

//    case GetTransactions =>
//      log.debug("Received Tx Request")
//      sender() ! {
//        if(getAllJobCoinTransactions.code != 200) Error else Response(getAllJobCoinTransactions.body)
//      }
    //TODO generate a dummyMixer account and pass it along to both mixer calls
    case moa: MixerOutAccounts => {
      context.actorOf(MixerService.props()) ! MixerOutAccounts(moa.primaryAccount, moa.accounts)
      sender ! Response(s"Sent your info to the Mixer.  Your mixer address is dummyMixer1.  Please send funds to be mixed to that address. Thanks")
    }
    case mt: MixThis => {
      sendJobCoinTransaction(mt.fromAddress, mt.mixerAddress, mt.amount) match {
        case r if(r.code) == 200 => {
          context.actorOf(MixerService.props()) ! MixThis(mt.fromAddress, "dummyMixer1", mt.amount) //TODO send a MixThis class which includes the incInt as part of the mixer in address
          sender ! Response(s"Sent your info to the Mixer. Your mix will be done momentarily. Thanks")
        }
        case r if(r.code) == 422 => {
          sender ! Error(s"Error. Insufficient funds.")
        }
        case _ => sender ! Error("Something went wrong")
      }

    }
    case Request => {
      println("got here")
      sender() ! Response("ok")
    }
    case _ => println("error");Error

  }
}
