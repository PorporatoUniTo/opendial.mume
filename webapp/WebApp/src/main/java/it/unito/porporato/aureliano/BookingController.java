package it.unito.porporato.aureliano;

import opendial.DialogueSystem;
import opendial.Settings;
import opendial.domains.Domain;
import opendial.readers.XMLDomainReader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.logging.Logger;

@RestController
public class BookingController {
    private static final String s = File.separator;
    static private final String DOMAIN_PATH = "D:" + s + "University" + s + "Borsa" + s + "opendial-mume-core" + s + "opendial-mume" + s + "opendial" + s + "domains" + s + "mumedefault" + s + "car-pooling.xml";

    static private DialogueSystem dialogueSystem;
    static private Logger log = Logger.getLogger("MuMeLog");

    static {
        Domain defaultDomain;
        dialogueSystem = new DialogueSystem();
        dialogueSystem.getSettings().showGUI = false;
        dialogueSystem.enableSpeech(false);
        Settings settings = dialogueSystem.getSettings();
        dialogueSystem.changeSettings(settings);
        try {
            defaultDomain = XMLDomainReader.extractDomain(DOMAIN_PATH);
        } catch (RuntimeException e) {
            dialogueSystem.displayComment("Cannot load domain: " + e);
            e.printStackTrace();
            defaultDomain = XMLDomainReader.extractEmptyDomain(DOMAIN_PATH);
        }
        dialogueSystem.changeDomain(defaultDomain);
        dialogueSystem.startSystem();
    }

    static String processSentence(String newSentence) {
        dialogueSystem.refreshDomain();
        // dialogueSystem.startSystem();
        String oldMachineUtterance;
        if (dialogueSystem.getState().hasChanceNode("u_m"))
            oldMachineUtterance = dialogueSystem.getState().queryProb("u_m").getBest().toString();
        else oldMachineUtterance = "";

        log.info("Old u_m:\t'" + oldMachineUtterance + "'");

        dialogueSystem.addUserInput(newSentence);
        boolean doLog = true;
        while (true) {
            String newMachineUtterance;
            if (dialogueSystem.getState().hasChanceNode("u_m"))
                newMachineUtterance = dialogueSystem.getState().queryProb("u_m").getBest().toString();
            else
                newMachineUtterance = oldMachineUtterance;

            if (doLog) {
                log.info("New u_m:\t'" + newMachineUtterance + "'");
                doLog = false;
            } else doLog = true;

            if (!oldMachineUtterance.equals(newMachineUtterance)) {
                log.info("New u_m:\t'" + newMachineUtterance + "'");
                return newMachineUtterance;
            } else
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException iE) {
                    iE.printStackTrace();
                }
        }
    }

    @RequestMapping("/booking")
    public String reserveCar() {
        String nS = "Buongiorno, vorrei prenotare un'auto di lusso dal parcheggio EINAUDI per domani alle 16 fino alle 19:30 se fosse possibile, grazie";
        return processSentence(nS);
    }
}
