package opendial.modules.mume;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import eu.fbk.dh.tint.runner.TintPipeline;
import opendial.DialogueState;
import opendial.DialogueSystem;
import opendial.modules.Module;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;

import static opendial.modules.mume.config.Config.LOG4J_CONFIG;
import static opendial.modules.mume.config.Config.TINT_CONFIG;
import static opendial.modules.mume.config.Shared.NONE;
import static opendial.modules.mume.config.Shared.log;

/**
 * Extract journey information from the user utterances.
 */
public class CarPoolingInformationExtraction implements Module {

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
    public CarPoolingInformationExtraction(DialogueSystem system) {
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
     * Extract any information that could be found in the current user utterance.
     *
     * @param state       the current dialogue state
     * @param updatedVars the updated variables in the state
     */
    @Override
    public void trigger(DialogueState state, Collection<String> updatedVars) {
        if (updatedVars.contains("u_u") &&
                state.hasChanceNode("u_u") &&
                state.hasChanceNode("state") &&
                state.queryProb("state").getBest().toString().equals("INFORMATION_EXTRACTION") &&
                state.hasChanceNode("a_m") &&
                state.queryProb("a_m").getBest().toString().equals("RETRIEVE_INFORMATION")) {

            String userUtterance = state.queryProb("u_u").getBest().toString();

            // Informations
            Map<String, String> information = new HashMap<>();
            Map<String, String> previousInformation = new HashMap<>();
            String[] infoSlots = {
                    "startDate",
                    "startTime",
                    "startCity",
                    "startSlot",
                    "startLat",
                    "startLon",

                    "endDate",
                    "endCity",
                    "endSlot",
                    "endLat",
                    "endLon",
                    "endTime",
                    "vehicleType"
            };

            Arrays.stream(infoSlots).forEach(slot -> {
                if (state.hasChanceNode(slot))
                    information.put(slot, state.queryProb(slot).getBest().toString());
                else
                    information.put(slot, NONE);
                previousInformation.put(slot, information.get(slot));
            });

            previousInformation.forEach((s, v) -> log.info(s + " = " + v));

            // 'Vorrei prenotare l'auto in piazza Vittorio Veneto a Pinerolo per domani dalle 14 alle sette'
            log.info("User said: '" + userUtterance + "'");

            String machinePrevState = "";
            if (state.hasChanceNode("a_m-prev"))
                machinePrevState = state.queryProb("a_m-prev").getBest().toString();

            ZonedDateTime now = ZonedDateTime.now();
            userUtterance = correctUserUtterance(userUtterance, machinePrevState, now,
                    (!information.get("startDate").equals(NONE)) ? information.get("startDate") : "");
            log.info("Corrected user utterance: '" + userUtterance + "'");

            /* Real cedit card needed /
            try {
                results = GeocodingApi.geocode(geoContext, userUtterance).await();
            } catch (ApiException | InterruptedException | IOException exception) {
                exception.printStackTrace();
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            for (GeocodingResult result : results)
                log.info(gson.toJson(result.addressComponents));
            */

            /*
             * The TimexesAnnotation needed to access the TIMEX3 tags in the TintPipeline is not public:
             * the result of the annotation process is written on a JSON file and read from there
             /
            InputStream stream = new ByteArrayInputStream(userUtterance.getBytes(StandardCharsets.UTF_8));

            OutputStream jsonOut = null;
            try {
                jsonOut = new FileOutputStream(JSON_OUT);
            } catch (FileNotFoundException exception) {
                exception.printStackTrace();
            }
            */

            Annotation annotation;
            // try {
            // annotation = pipeline.run(stream, jsonOut, TintRunner.OutputFormat.JSON);
            // annotation = pipeline.run(stream, System.out, TintRunner.OutputFormat.JSON);
            annotation = pipeline.runRaw(userUtterance);

            /* See previous comment /
            log.info("TIMEX3: " + String.valueOf(annotation.get(HeidelTimeAnnotations.TimexesAnnotation.class).size()));

            BufferedReader bufferedReader = new BufferedReader(new FileReader(JSON_OUT));
            Gson gsonOut = new Gson();
            LinkedTreeMap json = (LinkedTreeMap) gsonOut.fromJson(bufferedReader, Object.class);
            */

            /*
             * LogOutputStream
             * https://web.archive.org/web/20130527080241/http://www.java2s.com/Open-Source/Java/Testing/jacareto/jacareto/toolkit/log4j/LogOutputStream.java.htm
             */

            // TESTS
            // log.info(json.toString());
            // ((ArrayList) json.get("timexes")).forEach(t -> log.info(((LinkedTreeMap) t).get("timexType").toString()));
            log.info("Results:");
            log.info("Text: " + annotation.get(CoreAnnotations.TextAnnotation.class));
            log.info("Token's POS-tags:");
            List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
            tokens.forEach(token -> log.info(token.get(CoreAnnotations.PartOfSpeechAnnotation.class)));
            log.info("Sentences:");
            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
            sentences.forEach(sentence -> log.info(sentence.toShorterString()));
            log.info("Dependencies:");
            // There is only one sentence: property 'ita_toksent.ssplitOnlyOnNewLine=true' in Tint's default-config.properties
            SemanticGraph dependencies = sentences.get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
            dependencies.prettyPrint();
            log.info("Root:");
            dependencies.getRoots().forEach(root -> log.info(root.toString()));
            log.info("NERs:");
            tokens.forEach(t -> {
                String ner = t.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                if (!ner.equals("O"))
                    log.info(ner);
            });
            List<CoreLabel> dateTokens = new ArrayList<>();
            List<CoreLabel> timeTokens = new ArrayList<>();
            List<CoreLabel> locTokens = new ArrayList<>();
            List<CoreLabel> durTokens = new ArrayList<>();
            tokens.forEach(t -> {
                switch (t.get(CoreAnnotations.NamedEntityTagAnnotation.class)) {
                    case "DATE":
                        dateTokens.add(t);
                        break;
                    case "TIME":
                        timeTokens.add(t);
                        break;
                    case "LOC":
                        locTokens.add(t);
                        break;
                    case "DURATION":
                        durTokens.add(t);
                        break;
                    default:
                }
            });
            log.info("Dates:");
            dateTokens.forEach(t -> log.info(t.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class)));
            log.info("Times:");
            timeTokens.forEach(t -> log.info(t.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class)));
            log.info("Durations:");
            durTokens.forEach(t -> log.info(t.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class)));
            log.info("Locations:");
            locTokens.forEach(t -> log.info(t.get(CoreAnnotations.TextAnnotation.class)));

            /* OLD
            List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
            // There is only one sentence: property 'ita_toksent.ssplitOnlyOnNewLine=true' in Tint's default-config.properties
            for (CoreMap sentence : sentences) {
                SemanticGraph dependencies = sentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);

                // dependencies.prettyPrint();

                List<IndexedWord> conjiunctionIndices = new ArrayList<>();
                dependencies.childPairs(dependencies.getFirstRoot()).forEach((p) -> {
                    if ((p.first.getShortName() + ":" + p.first.getSpecific()).equals("conj:e"))
                        conjiunctionIndices.add(p.second);
                });

                int split = -1;
                if (conjiunctionIndices.size() > 0)
                    split = conjiunctionIndices.get(0).beginPosition() - 1;
                boolean inNER = false;
                CoreLabel currentNER = null;
                int currentNERStart = -1;
                String currentValue = "";
                String currentNERText = "";
                String currentNERType = "O";
                String nerType;
                for (CoreLabel token : tokens) {
                    nerType = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                    if (inNER && !nerType.equals(currentNERType)) {
                        updateInfo(annotation, dependencies, currentNER, currentNERStart, currentNERType, currentValue, split, information, machinePrevState);

                        // Leaving the found NER: reset parameters
                        inNER = false;
                        currentNER = null;
                        currentNERStart = -1;
                        currentValue = "";
                        currentNERType = "O";
                    }
                    if (!nerType.equals("O") && !inNER) {
                        // Another NER encountered
                        inNER = true;
                        currentNER = token;
                        currentNERStart = token.beginPosition();
                        currentNERType = nerType;
                        switch (nerType) {
                            case "DATE":
                                currentValue = token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
                                currentNERText = token.get(CoreAnnotations.TextAnnotation.class);
                                break;
                            case "TIME":
                                currentValue = token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
                                currentNERText = token.get(CoreAnnotations.TextAnnotation.class);
                                break;
                            case "LOC":
                                currentValue = token.get(CoreAnnotations.TextAnnotation.class);
                                currentNERText = token.get(CoreAnnotations.TextAnnotation.class);
                                break;
                        }
                    } else if (inNER && nerType.equals("LOC")) {
                        currentValue = currentValue + " " + token.get(CoreAnnotations.TextAnnotation.class);
                        currentNERText = currentNERText + " " + token.get(CoreAnnotations.TextAnnotation.class);
                    } else if (inNER)
                        currentNERText = currentNERText + " " + token.get(CoreAnnotations.TextAnnotation.class);
                }
                // If the user utterance terminate with a NER, collect this information
                nerType = "O";
                if (inNER && !nerType.equals(currentNERType)) {
                    updateInfo(annotation, dependencies, currentNER, currentNERStart, currentNERType, currentValue, split, information, machinePrevState);
                }
                JsonParser parser = new JsonParser();
                boolean waitBetweenRequests = false;
                if (!information.getOrDefault("startSlot", NONE).equals(previousInformation.get("startSlot"))) {
                    String nominatimResponse = getNominatimJSON(information.get("startSlot"));

                    JsonArray locations = (JsonArray) parser.parse(nominatimResponse);
                    if (locations.size() > 0) {
                        information.put("startLat", locations.get(0).getAsJsonObject().get("lat").getAsString());
                        information.put("startLon", locations.get(0).getAsJsonObject().get("lon").getAsString());
                    }
                    waitBetweenRequests = true;
                }
                if (!information.getOrDefault("endSlot", NONE).equals(previousInformation.get("endSlot"))) {
                    if (waitBetweenRequests) {
                        try {
                            Thread.sleep(NOMINATIM_TIMEOUT);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    String nominatimResponse = getNominatimJSON(information.get("endSlot"));

                    JsonArray locations = (JsonArray) parser.parse(nominatimResponse);
                    if (locations.size() > 0) {
                        information.put("endLat", locations.get(0).getAsJsonObject().get("lat").getAsString());
                        information.put("endLon", locations.get(0).getAsJsonObject().get("lon").getAsString());
                    }
                }
            }
            */

            // NO TEST
            //List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
            //List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
            //SemanticGraph dependencies = sentences.get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);

            List<List<IndexedWord>> locationAnnotations = new ArrayList<>();
            List<List<IndexedWord>> dateAnnotations = new ArrayList<>();
            List<List<IndexedWord>> timeAnnotations = new ArrayList<>();
            List<List<IndexedWord>> durationAnnotations = new ArrayList<>();
            /* True if the token in exam parteins to the current (multi-word) NER */
            boolean inNER = false;
            /* Tokens and IndexedWords of the current (multi-word) NER */
            List<IndexedWord> currentNERIndexedWords = new ArrayList<>();
            /* The NER type of the next token */
            String nerType;
            String currentNERType = NO_NER;

            for (CoreLabel token : tokens) {
                nerType = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);

                if (inNER && !nerType.equals(currentNERType)) {
                    // The current NER is complete
                    switch (currentNERType) {
                        case "LOC":
                            locationAnnotations.add(currentNERIndexedWords);
                            break;
                        case "DATE":
                            dateAnnotations.add(new ArrayList<>(currentNERIndexedWords));
                            break;
                        case "TIME":
                            timeAnnotations.add(new ArrayList<>(currentNERIndexedWords));
                            break;
                        case "DURATION":
                            durationAnnotations.add(new ArrayList<>(currentNERIndexedWords));
                            break;
                        default:
                    }

                    // Leaving the found NER: reset parameters
                    inNER = false;
                    currentNERIndexedWords = new ArrayList<>();
                } else if (!nerType.equals("O") && !inNER) {
                    // Another NER encountered
                    inNER = true;

                    currentNERType = nerType;
                    currentNERIndexedWords.add(dependencies.getNodeByIndex(token.index()));
                } else if (inNER && nerType.equals(currentNERType))
                    // Previous NER continuation
                    currentNERIndexedWords.add(dependencies.getNodeByIndex(token.index()));
            }

            LocationsExtractor.getInstance().extractLocations(annotation, locationAnnotations, information, previousInformation, machinePrevState);

            DatesTimesExtractor.getInstance().extractTimeAndDate(annotation, dateAnnotations, timeAnnotations, durationAnnotations, information, machinePrevState);

            VehicleTypeExtractor.getInstance().extractVehicleType(userUtterance, information, previousInformation);

            boolean hasBeenUpdated = false;
            for (Map.Entry<String, String> info : information.entrySet())
                if (information.get(info.getKey()).equals(previousInformation.get(info.getKey())))
                    hasBeenUpdated = true;

            information.forEach((s, v) -> {
                log.info(s + " = " + v);
                system.addContent(s, v);
            });
            system.addContent("update", String.valueOf(hasBeenUpdated));
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

    /**
     * Returns the italian name of a month.
     *
     * @param monthNumber the Integer month number
     * @return the Italian (lowercase) String name of the month
     */
    private String getMonthName(int monthNumber) {
        String name = "";
        switch (monthNumber) {
            case 1:
                name = "gennaio";
                break;
            case 2:
                name = "febbraio";
                break;
            case 3:
                name = "marzo";
                break;
            case 4:
                name = "aprile";
                break;
            case 5:
                name = "maggio";
                break;
            case 6:
                name = "giugno";
                break;
            case 7:
                name = "luglio";
                break;
            case 8:
                name = "agosto";
                break;
            case 9:
                name = "settembre";
                break;
            case 10:
                name = "ottobre";
                break;
            case 11:
                name = "novembre";
                break;
            case 12:
                name = "dicembre";
                break;
        }
        return name;
    }

    /**
     * Correct the user utterance for enable the recognition of the Named Entities in it.
     *
     * @param originalUtterance the original utterance from the user
     * @param machinePrevState  the previous machine state(that may contains the question the user is answering at)
     * @param now               the current complete date
     * @param startDate         the String with the modified user utterance ready for parsing
     */
    private String correctUserUtterance(String originalUtterance, String machinePrevState, ZonedDateTime now, String startDate) {
        String correctedUtterance = originalUtterance;
        // NO correctedUtterance = correctedUtterance.toLowerCase();

        /*
         * In one cases the hour starts with a vovel, "all'una":
         *  Delete the apostrophe and add "-le" ("all'una -> alle una") to reduce the possible utterance forms' number.
         */
        correctedUtterance = correctedUtterance.replace("l'una", "le una");

        /*
         * If the user is answering a question by the system, s\he could have possibly omitted a preposition
         * (e.g.: "A che ora vuoi posare l'auto? [alle ]14"): if this is the case, add the preposition.
         */
        if (machinePrevState.endsWith("TIME")) {
            /*
             * If the system asked for a time information, the answer shuold (?) contain a preposition such as "alle"
             *   (e.g., "A che ora vorresti partire? Alle sette")
             */
            if (!(correctedUtterance.contains("Alle") || correctedUtterance.contains("alle") ||
                    correctedUtterance.contains("Dalle") || correctedUtterance.contains("dalle") ||
                    correctedUtterance.contains("Le") || correctedUtterance.contains("le")))    // The others are explicitated for clarity, this is more general
                correctedUtterance = "Alle " + correctedUtterance;
            else if (correctedUtterance.startsWith("le"))
                correctedUtterance = "Al" + correctedUtterance;
            else if (correctedUtterance.startsWith("Le"))
                correctedUtterance = "Alle " + correctedUtterance.substring(3);
        } else if (machinePrevState.endsWith("DATE")) {
            /*
             * The user maybe has omitted the month from a date. If it is so, it should be that the user would imply the
             *  CURRENT MONTH or the month that s/he has ALREADY COMMUNICATED.
             */
            int dayMaybe = -1;
            try {
                if (correctedUtterance.startsWith("il "))
                    correctedUtterance = correctedUtterance.substring(3);
                dayMaybe = Integer.parseInt(originalUtterance);
            } catch (NumberFormatException exception) {
            }
            /*
             * If the answer is just a number or something in the form "il _NUM_" (where _NUM_ is a number), the system
             *  has to infer the month (and possibly the year)
             */
            if (dayMaybe > 0)
                correctedUtterance = correctedUtterance + " " +
                        // if the user has communicated a "start month"...
                        ((!startDate.equals(NONE)) ?
                                // ... then s/he probably imply the same month...
                                getMonthName(Integer.parseInt(startDate.split("-")[1])) :
                                // ... otherwise, the current month
                                getMonthName(now.getMonthValue()));
        } else if (machinePrevState.endsWith("START_CITY") || machinePrevState.endsWith("START_SLOT")) {
            /*
             * If the user is answering to a question like "Da dove vuoi partire", the utternace should contain the
             *  preposition "da" (e.g.: Da dove vuoi partire? Da Pinerolo)
             */
            if (!correctedUtterance.startsWith("da "))
                correctedUtterance = "da " + correctedUtterance;
        } /* THE END CITY IS NOT AN ESSENTIAL INFORMATION: if the user doesn't communicate it, the system doesn't ask.
            else if (machinePrevState.endsWith("END_CITY") || machinePrevState.endsWith("END_SLOT")) {
            /*
             * Similarly if the user is communication the city/spot in which s/he will leave the vehicle, the utterance
             *  should contain the preposition "a" (e.g.: "E dove voui posare l'auto? a Nichelino")
             /
            if (!correctedUtterance.contains("a"))
                correctedUtterance = "a " + correctedUtterance;
        }
        */

        /*
         * IMPORTANT: if the user utterance ends with a named entity (for example 'Voglio partire da piazza Castello')
         * the extraction process won't get this last one; a full stop (NER type "O", null) is therefore required and
         * has to be added if missing.
         */
        if (!originalUtterance.endsWith("."))
            correctedUtterance = correctedUtterance + ".";
        return correctedUtterance;
    }
}
