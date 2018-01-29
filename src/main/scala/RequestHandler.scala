
package bb.mixer

import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.HttpTransactions._
import bb.mixer.MixerMain.{primaryToMixerOut, primaryToLastActivity}

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
    case moa: MixerOutAddresses => {
      primaryToMixerOut.get(moa.fromAddress) match {
        case Some(_) => {
          sender ! Error("You already have alternate addresses associated with this address.  Try again.") //limit one set of alternate addresses per primary address
        }
        case None => {
          context.actorOf(MixerService.props()) ! MixerOutAddresses(moa.fromAddress, moa.addresses)
          primaryToLastActivity.update(moa.fromAddress, java.util.Calendar.getInstance.getTime) //init first time user has used the mixer
          sender ! Response(s"Sent your info to the Mixer.  Your mixer address is 'dummyMixer1'.  Please send funds to be mixed to that address. Thanks")
        }
       }

    }
    case mt: MixThis => {
      primaryToMixerOut.get(mt.fromAddress) match { //check that the user has notified mixer of out addresses
        case Some(_) => {
          sendJobCoinTransaction(mt.fromAddress, mt.mixerAddress, mt.amount) match { //send to Gemini API @ mixer address
            case r if(r.code) == 200 => {
              context.actorOf(MixerService.props()) ! MixThis(mt.fromAddress, "dummyMixer1", mt.amount) //TODO send a MixThis class which includes the incInt as part of the mixer in address
              sender ! Response(s"Sent your info to the Mixer. Your mix will be done momentarily. Thanks")
            }
            case r if(r.code) == 422 => {
              sender ! Error(s"Error. Insufficient funds.")
            }
            case _ => sender ! Error("Something went wrong.")
          }
        }
        case None => {
          sender ! Error(s"You have not notified the mixer of the alternate addresses to use in the mix.")
        }
      }


    }
    case Request => {
      println("got here")
      sender() ! Response("ok")
    }
    case _ => println("error");Error

  }
}
