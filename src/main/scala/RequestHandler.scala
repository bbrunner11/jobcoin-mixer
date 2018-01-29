
package bb.mixer

import akka.actor.{Actor, ActorLogging, Props}
import bb.mixer.HttpTransactions._
import bb.mixer.MixerMain.{primaryToMixerIn, primaryToMixerOut, primaryToLastActivity}

object RequestHandler {
  def props(): Props = {
    Props(classOf[RequestHandler])
  }
}

case class Request(request: String)
case class Response(payload: String)
case class Error(error: String)

class RequestHandler extends Actor with ActorLogging {

  var incMixer = 0 //increment int for mixer addresses

  def receive: Receive = {

    case moa: MixerOutAddresses => {
      primaryToMixerOut.get(moa.fromAddress) match {
        case Some(_) => {
          sender ! Error("You already have alternate addresses associated with this address.  Try again.") //limit one set of alternate addresses per primary address
        }
        case None => {
          context.actorOf(MixerService.props()) ! MixerOutAddresses(moa.fromAddress, moa.addresses)
          primaryToLastActivity.update(moa.fromAddress, java.util.Calendar.getInstance.getTime) //init first time user has used the mixer
          incMixer += 1
          primaryToMixerIn.update(moa.fromAddress, s"mixerIn$incMixer") //increment mixer address by 1 per valid mix request
          sender ! Response(s"Sent your info to the Mixer.  Your mixer address is 'mixerIn$incMixer'.  Please send funds to be mixed to that address. Thanks")
        }
       }
    }
    case mt: MixThis => {
      primaryToMixerOut.get(mt.fromAddress) match { //check that the user has notified mixer of out addresses
        case Some(_) => {
          sendJobCoinTransaction(mt.fromAddress, mt.mixerAddress, mt.amount) match { //send to Gemini API @ mixer address
            case r if(r.code) == 200 => {
              context.actorOf(MixerService.props()) ! MixThis(mt.fromAddress, mt.mixerAddress, mt.amount)
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
