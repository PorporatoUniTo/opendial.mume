package opendial.modules.mumedefault;

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
import opendial.modules.mumedefault.information.LocationInfo;

import java.io.*;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static opendial.modules.mumedefault.config.Config.*;
import static opendial.modules.mumedefault.config.Shared.*;
import static opendial.modules.mumedefault.information.TimeInfo.QUARTER;

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
    */

    // Tint
    TintPipeline pipeline;
    TintPipeline correctionPipeline;

    // Google Goecoding
    static Properties localGoogleMapsAPIPropeties;
    static String[] macAddresses;
    static String[] channels;

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
        // The charset encoding has to be settet at starttime
        // System.setProperty("file.encoding", "UTF-8");
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
        */


        /*===== CAUTION =====*/
        localGoogleMapsAPIPropeties = new Properties();
        try (BufferedReader googlePropertiesReader = new BufferedReader(new InputStreamReader(new FileInputStream(GOOGLE_MAPS_API_CONFIG)))) {
            localGoogleMapsAPIPropeties.load(googlePropertiesReader);
        } catch (FileNotFoundException fNFE) {
            log.severe("The attemp to use non-local Google API properties has failed.");
            fNFE.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        String[] macPortsPairs = localGoogleMapsAPIPropeties.getProperty("mac.address").split(";");
        if (macPortsPairs.length > 0) {
            macAddresses = new String[macPortsPairs.length];
            channels = new String[macPortsPairs.length];
            for (int i = 0; i < macPortsPairs.length; i++) {
                String[] temp = macPortsPairs[i].split(",");
                macAddresses[i] = temp[0];
                channels[i] = temp[1];
            }
        } else
            log.severe("Failed to load 'mac.address' property.");
        /*===================*/

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
            // String userUtterance = new String(userUtteranceBytes, StandardCharsets.UTF_16);

            // Informations
            SortedMap<String, String> information = new TreeMap<>();
            Map<String, String> previousInformation = new HashMap<>();
            String[] infoSlots = {
                    START_DATE,
                    START_TIME,
                    START_CITY,
                    START_SLOT,
                    START_LAT,
                    START_LON,

                    END_DATE,
                    END_TIME,
                    END_TIME_KNOWN,
                    END_CITY,
                    END_SLOT,
                    END_LAT,
                    END_LON,

                    VEHICLE_TYPE
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
            exampleLog.info("User's new message:\t'" + userUtterance + "'");

            if (userUtterance != null && !userUtterance.isEmpty()) {
                String machinePrevState = "";
                if (state.hasChanceNode("a_m-prev")) {
                    machinePrevState = state.queryProb("a_m-prev").getBest().toString();
                    log.info("Prev State:\t" + machinePrevState);
                }

                ZonedDateTime now = ZonedDateTime.now();
                userUtterance = correctUserUtterance(userUtterance, machinePrevState, now, information.getOrDefault(START_DATE, NONE), information.getOrDefault(END_DATE, NONE));
                log.info("Corrected user utterance: '" + userUtterance + "'");
                exampleLog.info("Corrected message:\t'" + userUtterance + "'");

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

                /* TESTS
                // log.info(json.toString());
                // ((ArrayList) json.get("timexes")).forEach(t -> log.info(((LinkedTreeMap) t).get("timexType").toString()));
                log.info("Results:");
                log.info("Text: " + annotation.get(CoreAnnotations.TextAnnotation.class));
                log.info("Token's POS-tags:");
                List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
                tokens.forEach(token -> log.info(token.get(CoreAnnotations.PartOfSpeechAnnotation.class)));
                tokens.forEach(token -> log.info(token.get(CoreAnnotations.LemmaAnnotation.class)));
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
                */

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
                List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
                List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
                // There is only one sentence: property 'ita_toksent.ssplitOnlyOnNewLine=true' in Tint's default-config.properties
                SemanticGraph dependencies = sentences.get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);

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


                List<LocationInfo> noDateAnnotations = LocationsExtractor.getInstance().extractLocations(annotation, locationAnnotations, information, previousInformation, machinePrevState);

                // Set of te indeces of the IndexedWords corresponding to location
                Set<Integer> noDateIndexedWordIndeces = noDateAnnotations.stream().flatMap(i -> i.getWordList().stream().map(IndexedWord::index)).collect(Collectors.toSet());
                dateAnnotations = dateAnnotations.stream().filter(l ->  // Retains only those annotations...
                        // ... whose NERs are not among those in an address
                        l.stream().noneMatch(n -> noDateIndexedWordIndeces.contains(n.index()))
                ).collect(Collectors.toList());

                // Filter out unspecific times (e.g., "sera")
                timeAnnotations = timeAnnotations.stream().filter(l ->
                        tokens.get(l.get(0).index() - 1).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class).split("T")[1].matches("\\d{1,2}(:\\d\\d)?")
                ).collect(Collectors.toList());

                DatesTimesExtractor.getInstance().extractTimeAndDate(annotation, dateAnnotations, timeAnnotations, durationAnnotations, information, previousInformation, machinePrevState);

                VehicleTypeExtractor.getInstance().extractVehicleType(userUtterance, information, previousInformation);


                // If the user gave all the required information
                if (!information.getOrDefault(START_SLOT, NONE).equals(NONE) &&
                        !information.getOrDefault(START_TIME, NONE).equals(NONE) &&
                        !information.getOrDefault(START_DATE, NONE).equals(NONE)) {
                    // Propagate endSlot and endCity information, but not end address:
                    //  end address can differ from the slot address
                    if (information.getOrDefault(END_SLOT, NONE).equals(NONE))
                        information.put(END_SLOT, information.getOrDefault(START_SLOT, NONE));
                    if (information.getOrDefault(END_CITY, NONE).equals(NONE))
                        information.put(END_CITY, information.getOrDefault(START_CITY, NONE));
                    if (information.getOrDefault(END_LAT, NONE).equals(NONE))
                        information.put(END_LAT, information.getOrDefault(START_LAT, NONE));
                    if (information.getOrDefault(END_LON, NONE).equals(NONE))
                        information.put(END_LON, information.getOrDefault(START_LON, NONE));
                }

                log.info("Updated Information:");
                exampleLog.info("Updated Information:");
                information.forEach((s, v) -> {
                    log.info(s + " = " + v);
                    exampleLog.info(((v.equals(previousInformation.getOrDefault(s, NONE))) ? "" : "\t") + s + " = " + v);
                    system.addContent(s, v);
                });


                List<String> errors = new LinkedList<>();
                if (!information.get(START_DATE).equals(previousInformation.get(START_DATE)))
                    checkForStartDateErrors(information, now, errors);
                if (!information.get(START_TIME).equals(previousInformation.get(START_TIME)))
                    checkForStartTimeErrors(information, now, errors);
                if (!information.get(END_DATE).equals(previousInformation.get(END_DATE)))
                    checkForEndDateErrors(information, errors);
                if (!information.get(END_TIME).equals(previousInformation.get(END_TIME)))
                    checkForEndTimeErrors(information, errors);

                exampleLog.info("\n");
                log.info("Errors found:\t" + errors.stream().collect(Collectors.joining(",", "[", "]")));
                if (!errors.isEmpty())
                    exampleLog.info("Errors found:\t" + errors.stream().collect(Collectors.joining("\n\t", "\t", "\n")));


                boolean hasBeenUpdated = false;
                for (Map.Entry<String, String> info : information.entrySet())
                    if (!info.getValue().equals(previousInformation.get(info.getKey())))
                        hasBeenUpdated = true;
                if (!hasBeenUpdated && !errors.isEmpty())
                    hasBeenUpdated = true;
                // To continue the dialog with an empty utternace, symulate the update
                if (!hasBeenUpdated && userUtterance.isEmpty())
                    hasBeenUpdated = true;


                if (hasBeenUpdated)
                    if (errors.isEmpty())
                        system.addContent("update", String.valueOf(true));
                    else {
                        system.addContent("errors", errors.stream().collect(Collectors.joining(",", "[", "]")));
                        system.addContent("update", String.valueOf(false));
                    }
            } else
                system.addContent("update", String.valueOf(true));
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
        switch (monthNumber) {
            case 1:
                return "gennaio";
            case 2:
                return "febbraio";
            case 3:
                return "marzo";
            case 4:
                return "aprile";
            case 5:
                return "maggio";
            case 6:
                return "giugno";
            case 7:
                return "luglio";
            case 8:
                return "agosto";
            case 9:
                return "settembre";
            case 10:
                return "ottobre";
            case 11:
                return "novembre";
            case 12:
                return "dicembre";
            default:
                return "";
        }
    }

    /**
     * Correct the user utterance for enable the recognition of the Named Entities in it.
     *
     * @param originalUtterance the original utterance from the user
     * @param machinePrevState  the previous machine state(that may contains the question the user is answering at)
     * @param now               the current complete date
     * @param startDate         the String with the modified user utterance ready for parsing
     */
    private String correctUserUtterance(String originalUtterance, String machinePrevState, ZonedDateTime now, String startDate, String endDate) {
        if (originalUtterance.isEmpty())
            return originalUtterance;

        String correctedUtterance = originalUtterance;
        // NO correctedUtterance = correctedUtterance.toLowerCase();

        // Normalize whitespaces sequences with a single whitespace
        correctedUtterance = correctedUtterance.replaceAll("\\s+", " ");

        /*
         * In one cases the hour starts with a vovel, "all'una":
         *  Delete the apostrophe and add "-le" ("all'una -> alle una") to reduce the possible utterance forms' number.
         */
        correctedUtterance = correctedUtterance.replace("l'una", "le una");

        /*
         * Sometimes the word "parcheggio" interfer with the resolution of the related LocationInfo, so delete it
         */
        correctedUtterance = correctedUtterance.replaceAll("pi.? vicino a", "")
                .replaceAll("pi.? vicino", "qui")
                .replaceAll("[Dd]al [Pp]archeggio", "da")
                .replaceAll("[Aa]l [Pp]archeggio", "a")
                .replaceAll("[Nn]el [Pp]archeggio", "a");

        /*
         * "Mezzogiorno" is recognised as a duration instrad of a time, so replace its occurrences with numbers
         */
        correctedUtterance = correctedUtterance.replaceAll("fino a mezzogiorno", "alle 12")
                .replaceAll("a mezzogiorno", "alle 12")
                .replaceAll("mezzogiorno", "le 12");

        /*
         * The user maybe has omitted the month from a date. If it is so, it should be that the user would imply the
         *  CURRENT MONTH or the month that s/he has ALREADY COMMUNICATED (or was inferred by the system).
         */
        if (machinePrevState.endsWith("DATE")) {
            /*
             * If the answer is just a number or something in the form "[...] il _NUM_(.?)" (where _NUM_ is a number), the system
             *  has to infer the month (and possibly the year)
             /
            if (correctedUtterance.matches("(Il|il|Dal|dal|Al|al|) (\\d{1,2})")) {
                boolean addPunctuation = false;
                if (correctedUtterance.endsWith(".") || correctedUtterance.endsWith(";")) {
                    correctedUtterance = correctedUtterance.substring(0, correctedUtterance.length() - 1);
                    addPunctuation = true;
                }
                correctedUtterance = correctedUtterance + " " +
                        // if the user has communicated a "start month"...
                        ((startDate != null && !startDate.equals(NONE)) ?
                                // ... then s/he probably imply the same month...
                                getMonthName(Integer.parseInt(startDate.split("-")[1])) :
                                // ... otherwise, the current month
                                getMonthName(now.getMonthValue())) +
                        ((addPunctuation) ? "." : "");
            }
             */
            Pattern monthDayPattern = Pattern.compile(
                    "([Ii]l|[Dd]al|[Aa]l|[Dd]el) (\\d{1,2})(?!\\d? ([Gg]ennaio|[Ff]ebbraio|[Mm]arzo|[Aa]prile|[Mm]aggio|[Gg]iugno|[Ll]uglio|[Aa]gosto|[Ss]ettembre|[Oo]ttobre|[Nn]ovembre|[Dd]icembre))"
            );
            Matcher monthDayMatcher = monthDayPattern.matcher(correctedUtterance);
            String monthName = "";
            for (String month : new String[]{"gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"})
                if (correctedUtterance.toLowerCase().contains(month))
                    monthName = month;
            if (monthName.isEmpty())
                // If the user has given a month already...
                monthName = ((startDate != null && !startDate.equals(NONE)) ?
                        // ... then s/he probably imply the same month...
                        getMonthName(Integer.parseInt(startDate.split("-")[1])) :
                        ((endDate != null && !endDate.equals(NONE)) ?
                                // ... then s/he probably imply the same month...
                                getMonthName(Integer.parseInt(endDate.split("-")[1])) :
                                // ... otherwise, the current month
                                getMonthName(now.getMonthValue())));
            while (monthDayMatcher.find()) {
                correctedUtterance = monthDayMatcher.replaceAll("$1 $2 " + monthName);
            }
        }
        /*
         * If the user is answering a question by the system, s\he could have possibly omitted a preposition
         * (e.g.: "A che ora vuoi posare l'auto? [alle ]14"): if this is the case, add the preposition.
         */
        else if (machinePrevState.endsWith("TIME")) {
            /*
             * If the system asked for a time information, the answer shuold contain a preposition such as "alle"
             *   (e.g., "A che ora vorresti partire? Alle sette")
             */
            if (!(correctedUtterance.startsWith("Alle") || correctedUtterance.startsWith("alle") ||
                    correctedUtterance.startsWith("Dalle") || correctedUtterance.startsWith("dalle") ||
                    correctedUtterance.startsWith("Le") || correctedUtterance.startsWith("le")) &&   // The others are explicitated for clarity, this is more general
                    correctedUtterance.matches("\\d{1,2}(:\\d\\d)?"))
                correctedUtterance = "Alle " + correctedUtterance.trim();
            else if (correctedUtterance.startsWith("le"))
                correctedUtterance = "Al" + correctedUtterance;
            else if (correctedUtterance.startsWith("Le"))
                correctedUtterance = "Alle " + correctedUtterance.substring(3);
        } else if (machinePrevState.endsWith("START_CITY") || machinePrevState.endsWith("START_SLOT")) {
            /*
             * If the user is answering to a question like "Da dove vuoi partire", the utternace should contain the
             *  preposition "da" (e.g.: Da dove vuoi partire? Da Pinerolo)
             */
            if (!correctedUtterance.startsWith("da ") && !correctedUtterance.startsWith("Da ") &&
                    !correctedUtterance.startsWith("dall'") && !correctedUtterance.startsWith("Dall'"))
                correctedUtterance = "Da " + correctedUtterance;


            /* If the system asked for a slot and the user says 'adesso' (e.g. 'da dove sono adesso'),
             * it should not be taken as 'now', so delete it to avoid misinterpretations */
            if (machinePrevState.endsWith("SLOT"))
                correctedUtterance = correctedUtterance.replace(" adesso", "")
                        .replace(" ora", "");
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
        /* If the system asked for a slot and the user says 'adesso' (e.g. 'da dove sono adesso'),
         * it should not be taken as 'now', so delete it to avoid misinterpretations */
        else if (machinePrevState.endsWith("SLOT")) {
            correctedUtterance = correctedUtterance.replace(" adesso", "")
                    .replace(" ora", "");
        }

        /* Tint has some problem with accented letters in the weekdays names: replace with the non-accented letter (it works fine) */
        correctedUtterance = correctedUtterance.replaceAll("luned.", "lunedi");
        correctedUtterance = correctedUtterance.replaceAll("marted.", "martedi");
        correctedUtterance = correctedUtterance.replaceAll("mercoled.", "mercoledi");
        correctedUtterance = correctedUtterance.replaceAll("gioved.", "giovedi");
        correctedUtterance = correctedUtterance.replaceAll("venerd.", "venerdi");

        /* The systma can handler just one sentence at one time */
        correctedUtterance = correctedUtterance.replace("...", ".");
        String subCorrectedUtterance = correctedUtterance.substring(0, correctedUtterance.length() - 1);
        correctedUtterance = subCorrectedUtterance.replace(".", " e ").replace(";", " e ") + correctedUtterance.charAt(correctedUtterance.length() - 1);

        /* Tint has some problem with the hour format \d\d:\d\d */
        /* Some user use '.' or even ',' instead */
        correctedUtterance = correctedUtterance.replaceAll("(\\d{1,2})[:,.](\\d\\d)", "$1 e $2");

        /*
         * IMPORTANT: if the user utterance ends with a named entity (for example 'Voglio partire da piazza Castello')
         * the extraction process won't get this last one; a full stop (NER type "O", null) is therefore required and
         * has to be added if missing.
         */
        boolean endWithPunct = false;
        for (int i = 0; i < PUNCT.size() && !endWithPunct; i++)
            if (originalUtterance.endsWith(PUNCT.get(i)))
                endWithPunct = true;
        if (!endWithPunct)
            correctedUtterance = correctedUtterance + ".";
        return correctedUtterance;
    }

    /**
     * TODO
     * Check if the user requested a vehicle for a past time (hour).
     *
     * @param information the Map<String, String> of all information collected til this point
     * @param now         the ZoneDateTime of the moment of the request
     * @param errors      the (to be filled) List<String> of the errors found in the request in exam
     */
    private void checkForStartDateErrors(Map<String, String> information, ZonedDateTime now, List<String> errors) {
        String[] sDate = information.getOrDefault(START_DATE, NONE).split("-");
        if (sDate.length > 2 &&
                // the year is a past year, or...
                ((Integer.parseInt(sDate[0]) < now.getYear() ||
                        // the month is a past month, or...
                        Integer.parseInt(sDate[0]) == now.getYear() &&
                                Integer.parseInt(sDate[1]) < now.getMonthValue() ||
                        // the day is a past day, or...
                        Integer.parseInt(sDate[0]) == now.getYear() &&
                                Integer.parseInt(sDate[1]) == now.getMonthValue() &&
                                Integer.parseInt(sDate[2]) < now.getDayOfMonth()) ||
                        // the hour is invalid (greater than 23 or less than 0, due to the correction taking in account the start time)
                        //  (this should not happen)
                        (Integer.parseInt(sDate[1]) > 12 || Integer.parseInt(sDate[2]) > 31))) {
            information.put(START_DATE, NONE);
            errors.add(PAST_TIME_ERROR);
        }
    }

    /**
     * TODO
     * Check if the user requested a vehicle for a past time (hour).
     *
     * @param information the Map<String, String> of all information collected til this point
     * @param now         the ZoneDateTime of the moment of the request
     * @param errors      the (to be filled) List<String> of the errors found in the request in exam
     */
    private void checkForStartTimeErrors(Map<String, String> information, ZonedDateTime now, List<String> errors) {
        String[] sDate = information.getOrDefault(START_DATE, NONE).split("-");
        String[] sTime = information.getOrDefault(START_TIME, NONE).split("-");
        if (sTime.length > 1 && sDate.length > 2 &&
                // the time is a past time, or...
                (((Integer.parseInt(sDate[0]) == now.getYear() &&
                        Integer.parseInt(sDate[1]) == now.getMonthValue() &&
                        Integer.parseInt(sDate[2]) == now.getDayOfMonth() &&
                        (Integer.parseInt(sTime[0]) < now.getHour() ||
                                Integer.parseInt(sTime[0]) == now.getHour() &&
                                        Integer.parseInt(sTime[1]) < (now.getMinute() / QUARTER) * QUARTER)) ||
                        // the hour is invalid (greater than 23 or less than 0, due to the correction taking in account the start time)
                        //  (this should not happen)
                        (Integer.parseInt(sTime[0]) > 23 || Integer.parseInt(sTime[0]) < 0)))) {
            information.put(START_TIME, NONE);
            errors.add(PAST_TIME_ERROR);
        }
    }

    /**
     * TODO
     * Check if the user requested a vehicle for a past time (hour).
     *
     * @param information the Map<String, String> of all information collected til this point
     * @param errors      the (to be filled) List<String> of the errors found in the request in exam
     */
    private void checkForEndDateErrors(Map<String, String> information, List<String> errors) {
        String[] sDate = information.getOrDefault(START_DATE, NONE).split("-");
        String[] eDate = information.getOrDefault(END_DATE, NONE).split("-");
        if (eDate.length > 2 && sDate.length > 2 &&
                // the year is a past year, or...
                ((Integer.parseInt(eDate[0]) < Integer.parseInt(sDate[0]) ||
                        // the month is a past month, or...
                        Integer.parseInt(eDate[0]) == Integer.parseInt(sDate[0]) &&
                                Integer.parseInt(eDate[1]) < Integer.parseInt(sDate[1]) ||
                        // the day is a past day, or...
                        Integer.parseInt(eDate[0]) == Integer.parseInt(sDate[0]) &&
                                Integer.parseInt(eDate[1]) == Integer.parseInt(sDate[1]) &&
                                Integer.parseInt(eDate[2]) < Integer.parseInt(sDate[2])) ||
                        // the hour is invalid (greater than 23 or less than 0, due to the correction taking in account the start time)
                        //  (this should not happen)
                        (Integer.parseInt(eDate[1]) > 12 || Integer.parseInt(eDate[2]) > 31))) {
            information.put(END_DATE, NONE);
            errors.add(PAST_TIME_ERROR);
        }
    }

    /**
     * TODO
     * Check if the user requested a vehicle for a past time (hour).
     *
     * @param information the Map<String, String> of all information collected til this point
     * @param errors      the (to be filled) List<String> of the errors found in the request in exam
     */
    private void checkForEndTimeErrors(Map<String, String> information, List<String> errors) {
        String[] sDate = information.getOrDefault(START_DATE, NONE).split("-");
        String[] sTime = information.getOrDefault(START_TIME, NONE).split("-");
        String[] eDate = information.getOrDefault(END_DATE, NONE).split("-");
        String[] eTime = information.getOrDefault(END_TIME, NONE).split("-");
        if (sTime.length > 1 && sDate.length > 2 &&
                eTime.length > 1 && eDate.length > 2 &&
                (// the time is a past time, or...
                        Integer.parseInt(eDate[0]) == Integer.parseInt(sDate[0]) &&
                                Integer.parseInt(eDate[1]) == Integer.parseInt(sDate[1]) &&
                                Integer.parseInt(eDate[2]) == Integer.parseInt(sDate[2]) &&
                                (Integer.parseInt(eTime[0]) <= Integer.parseInt(sTime[0]) ||
                                        Integer.parseInt(eTime[0]) == Integer.parseInt(sTime[0]) + 1 &&
                                                Integer.parseInt(eTime[1]) < Integer.parseInt(sTime[1]))) ||
                // the hour is invalid (greater than 23 or less than 0, due to the correction taking in account the start time)
                //  (this should not happen)
                eTime.length > 1 && (Integer.parseInt(eTime[0]) > 23 || Integer.parseInt(eTime[0]) < 0)) {
            information.put(END_TIME, NONE);
            errors.add(PAST_TIME_ERROR);
        }
    }
}
