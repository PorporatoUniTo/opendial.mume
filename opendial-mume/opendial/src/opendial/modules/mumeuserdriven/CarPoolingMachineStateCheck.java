package opendial.modules.mumeuserdriven;

import eu.fbk.dh.tint.runner.TintPipeline;
import opendial.DialogueState;
import opendial.DialogueSystem;
import opendial.modules.Module;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static opendial.modules.mumeuserdriven.Config.LOG4J_CONFIG;
import static opendial.modules.mumeuserdriven.Config.TINT_CONFIG;
import static opendial.modules.mumeuserdriven.Shared.log;

/**
 * Extract journey information from the user utterances.
 */
public class CarPoolingMachineStateCheck implements Module {

    static {
        System.setProperty("log4j.configurationFile", LOG4J_CONFIG);
    }

    //* private static final String JSON_OUT = "." + File.separator + "out.json"; *//

    private static final String NO_NER = "O";

    // the dialogue system
    DialogueSystem system;

    // whether the module is paused or active
    boolean paused = true;

    /*
    // HeidelTime
    HeidelTimeStandalone haidelTime;

    // Google Goecoding
    GeoApiContext geoContext;
    */

    // Tint
    TintPipeline pipeline;

    /**
     * Creates a new instance of the flight-booking module
     *
     * @param system the dialogue system to which the module should be attached
     */
    public CarPoolingMachineStateCheck(DialogueSystem system) {
        this.system = system;
    }

    /**
     * Starts the module.
     */
    @Override
    public void start() {
        pipeline = new TintPipeline();
        try {
            File configFile = new File(TINT_CONFIG);
            pipeline.loadPropertiesFromFile(configFile);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        pipeline.load();

        /*
        haidelTime = new HeidelTimeStandalone(
                Language.ITALIAN,
                DocumentType.COLLOQUIAL,
                OutputType.TIMEML,
                HEIDELTIME_CONFIG,
                POSTagger.TREETAGGER
                //true
        );

        geoContext = new GeoApiContext.Builder()
                .apiKey("xxx")
                .maxRetries(3)
                .retryTimeout(3, TimeUnit.SECONDS)
                .build();
        */

        paused = false;
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

    /**
     * Extract any information that could be found in the current user utterance.
     *
     * @param state       the current dialogue state
     * @param updatedVars the updated variables in the state
     */
    @Override
    public void trigger(DialogueState state, Collection<String> updatedVars) {
        if (updatedVars.contains("a_m") &&
                state.hasChanceNode("a_m")) {

            log.info("\n");
            log.info("Machine Action:\t" + state.queryProb("a_m").getBest().toString());
            log.info("NewInformation:\t" + state.queryProb("NewInformation").getBest().toString());
            log.info("StartSlot:\t" + state.queryProb("StartSlot").getBest().toString());
            log.info("StartCity:\t" + state.queryProb("StartCity").getBest().toString());
            log.info("StartLat:\t" + state.queryProb("StartLat").getBest().toString());
            log.info("StartLon:\t" + state.queryProb("StartLon").getBest().toString());
            log.info("StartDate:\t" + state.queryProb("StartDate").getBest().toString());
            log.info("StartTime:\t" + state.queryProb("StartTime").getBest().toString());
            log.info("EndSlot:\t" + state.queryProb("EndSlot").getBest().toString());
            log.info("EndCity:\t" + state.queryProb("EndCity").getBest().toString());
            log.info("EndTimeKnown:\t" + state.queryProb("EndTimeKnown").getBest().toString());
            log.info("EndDate:\t" + state.queryProb("EndDate").getBest().toString());
            log.info("EndTime:\t" + state.queryProb("EndTime").getBest().toString());
            log.info("StopsKnown:\t" + state.queryProb("StopsKnown").getBest().toString());
            log.info("Stops:\t" + state.queryProb("Stops").getBest().toString());

        }
    }
}
