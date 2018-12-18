package opendial.modules.mumeuserdriven;

import opendial.DialogueState;
import opendial.DialogueSystem;
import opendial.modules.Module;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * Example of simple external module used for the flight-booking dialogue domain. The
 * module monitors for two particular values for the system action:
 * <ol>
 * <li>"FindOffer" checks the (faked) price of the user order and returns
 * MakeOffer(price)
 * <li>"Book" simulates the booking of the user order.
 * </ol>
 *
 * @author Pierre Lison (plison@ifi.uio.no)
 */
public class CarPoolingRequest implements Module {

    // logger
    public final static Logger log = Logger.getLogger("OpenDial");

    // the dialogue system
    DialogueSystem system;

    // whether the module is paused or active
    boolean paused = true;

    /**
     * Creates a new instance of the flight-booking module
     *
     * @param system the dialogue system to which the module should be attached
     */
    public CarPoolingRequest(DialogueSystem system) {
        this.system = system;
    }

    /**
     * Starts the module.
     */
    @Override
    public void start() {
        paused = false;
    }

    /**
     * Checks whether the updated variables contains the system action and (if yes)
     * whether the system action value is "FindOffer" or "Book". If the value is
     * "FindOffer", checks the price of the order (faked here to 179 or 299 EUR) and
     * adds the new action "MakeOffer(price)" to the dialogue state. If the value is
     * "Book", simply write down the order on the system output.
     *
     * @param state       the current dialogue state
     * @param updatedVars the updated variables in the state
     */
    @Override
    public void trigger(DialogueState state, Collection<String> updatedVars) {
        if (updatedVars.contains("current_step") &&
                state.hasChanceNode("current_step") &&
                // If the new state of the machine is the "make request" state
                state.queryProb("current_step").getBest().toString().equals("SEND_REQUEST")) {
            String result;
            double rnd = Math.random();
            log.info("Request result: " + rnd);
            if (rnd < 0.7) {
                result = "Success";
                log.info("\t ==>\t Success");
            } else {
                result = "Failure";
                log.info("\t ==>\t Failure");
            }
            system.addContent("RequestResult", result);

            system.addContent("current_step", "OUTCOME_COMMUNICATION");
        }

    }

    /**
     * Pauses the module.
     *
     * @param toPause whether to pause the module or not
     */
    @Override
    public void pause(boolean toPause) {
        paused = toPause;
    }

    /**
     * Returns whether the module is currently running or not.
     *
     * @return whether the module is running or not.
     */
    @Override
    public boolean isRunning() {
        return !paused;
    }

}
