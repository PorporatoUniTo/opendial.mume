package opendial.modules.mumeuserdriven;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static opendial.modules.mumeuserdriven.Config.LOG4J_CONFIG;
import static opendial.modules.mumeuserdriven.Config.TINT_CONFIG;
import static opendial.modules.mumeuserdriven.Shared.*;

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
        if (updatedVars.contains("u_u") &&
                state.hasChanceNode("u_u") &&
                state.hasChanceNode("current_step") &&
                state.queryProb("current_step").getBest().toString().equals("INFORMATION_RETRIEVAL") &&
                state.hasChanceNode("a_m")) {

            String userUtterance = state.queryProb("u_u").getBest().toString();
            String machineIntent = state.queryProb("a_m").getBest().toString();
            log.info("\n");
            log.info("Machine Action:\t" + machineIntent);
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

            /*
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
            */

            // 'Vorrei prenotare l'auto in piazza Vittorio Veneto a Pinerolo per domani dalle 14 alle sette'
            log.info("User said: '" + userUtterance + "'");

            userUtterance = correctUserUtterance(userUtterance, machineIntent);
            log.info("Corrected user utterance: '" + userUtterance + "'");

            Annotation annotation;
            // try {
            // annotation = pipeline.run(stream, jsonOut, TintRunner.OutputFormat.JSON);
            // annotation = pipeline.run(stream, System.out, TintRunner.OutputFormat.JSON);
            annotation = pipeline.runRaw(userUtterance);

            // TESTS
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

            /* No test
            List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
            SemanticGraph dependencies = sentences.get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
            */

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

            List<List<IndexedWord>> cities = new ArrayList<>();
            List<List<IndexedWord>> addresses = new ArrayList<>();

            partitionLocation(locationAnnotations, cities, addresses, tokens, dependencies);

            List<String> processedVehicleTypeAnnotation = new ArrayList<>();
            List<String> processedLocationAnnotations = new ArrayList<>();
            List<String> processedTimeAnnotations = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            if (machineIntent.contains("SLOT") || machineIntent.contains("CITY")) {
                if (addresses.size() == 1) {
                    String city = "";
                    boolean inferredCity = false;
                    if (cities.size() == 1)
                        city = cities.get(0).stream().map(IndexedWord::originalText).collect(Collectors.joining(" "));
                    else
                        for (Map.Entry<String, List<String>> cityAddresses : CITIES_ADDRESSES.entrySet())
                            if (cityAddresses.getValue().contains(addresses.get(0).stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")))) {
                                city = cityAddresses.getKey();
                                inferredCity = true;
                            }

                    if (!city.isEmpty()) {
                        String address = addresses.get(0).stream().map(IndexedWord::originalText).collect(Collectors.joining(" "));
                        JsonParser parser = new JsonParser();
                        String nominatimResponse = getNominatimJSON(address + " " + city);

                        JsonArray locations = (JsonArray) parser.parse(nominatimResponse);
                        if (locations.size() > 0) {
                            processedLocationAnnotations.add("Slot(" + address + ")");
                            processedLocationAnnotations.add(((inferredCity) ? "InferredCity(" : "City(") + city + ")");
                            processedLocationAnnotations.add("Lat(" + locations.get(0).getAsJsonObject().get("lat").getAsString()
                                    /* Avoids characters problems */
                                    .replace(".", "_") + ")");
                            processedLocationAnnotations.add("Lon(" + locations.get(0).getAsJsonObject().get("lon").getAsString()
                                    /* Avoids characters problems */
                                    .replace(".", "_") + ")");
                        }
                    }
                } else if (cities.size() == 1 && addresses.isEmpty()) {
                    String city = cities.get(0).stream().map(IndexedWord::originalText).collect(Collectors.joining(" "));
                    String address = CITIES_ADDRESSES.get(city).get(
                            (machineIntent.contains("START")) ? 0 : CITIES_ADDRESSES.get(city).size() - 1
                    );
                    JsonParser parser = new JsonParser();
                    String nominatimResponse = getNominatimJSON(address + " " + city);

                    JsonArray locations = (JsonArray) parser.parse(nominatimResponse);
                    if (locations.size() > 0) {
                        processedLocationAnnotations.add("Slot(" + address + ")");
                        processedLocationAnnotations.add("City(" + city + ")");
                        processedLocationAnnotations.add("Lat(" + locations.get(0).getAsJsonObject().get("lat").getAsString()
                                /* Avoids characters problems */
                                .replace(".", "_") + ")");
                        processedLocationAnnotations.add("Lon(" + locations.get(0).getAsJsonObject().get("lon").getAsString()
                                /* Avoids characters problems */
                                .replace(".", "_") + ")");
                    }
                } /* else
                    processedLocationAnnotations.add("City(" + cities.get(0).stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")) + ")");
                    */
            } else {
                cities.forEach(c -> {
                    String city = c.stream().map(IndexedWord::originalText).collect(Collectors.joining(" "));
                    processedLocationAnnotations.add("City(" + city + ")");
                });
                addresses.forEach(a -> {
                    String sddress = a.stream().map(IndexedWord::originalText).collect(Collectors.joining(" "));
                    processedLocationAnnotations.add("Address(" + sddress + ")");
                });
            }

            ZonedDateTime now = ZonedDateTime.now();
            if (machineIntent.contains("TIME")) {
                if (timeAnnotations.size() == 1) {
                    String date = "";
                    if (dateAnnotations.size() == 1) {
                        String currentDate = dateAnnotations.get(0).get(0).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class).split("T")[0];
                        if (!currentDate.equals("XXXX-XX-XX"))
                            date = currentDate;
                    }
                    if (date.isEmpty()) {
                        if (machineIntent.contains("END") && state.hasChanceNode("StartDate")) {
                            date = state.queryProb("StartDate").getBest().toString();
                        } else {
                            date = now.getYear() + "-" +
                                    ((now.getMonthValue() < 10) ? "0" + now.getMonthValue() : now.getMonthValue()) + "-" +
                                    ((now.getDayOfMonth() < 10) ? "0" + now.getDayOfMonth() : now.getDayOfMonth());
                        }
                    }

                    // FIXME check time corrections
                    // TODO add recap to the confirmation question
                    String time;
                    // TODO check the methodology: minutes in letters are a problem?
                    boolean timeInLetters = !timeAnnotations.get(0).stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")).matches("[^\\d]*\\d+[^\\d]*");

                    log.info("Time in letters:\t" + timeInLetters);

                    String[] newTimeFields = timeAnnotations.get(0).get(0).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class).split("T")[1].split(":");
                    if (machineIntent.contains("START")) {
                        time = (Integer.parseInt(newTimeFields[0]) +
                                ((timeInLetters &&
                                        Integer.parseInt(date.split("-")[2]) == now.getDayOfMonth() &&
                                        Integer.parseInt(newTimeFields[0]) < now.getHour()) ? 12 : 0)
                        ) + ":" + newTimeFields[1];
                    } else if (machineIntent.contains("END") &&
                            state.hasChanceNode("StartTime")) {
                        String startHour = state.queryProb("StartTime").getBest().toString().split("-")[0];
                        time = (Integer.parseInt(newTimeFields[0]) +
                                ((timeInLetters && Integer.parseInt(newTimeFields[0]) <= Integer.parseInt(startHour)) ? 12 : 0)
                        ) + ":" + newTimeFields[1];
                    } // Superfluos, just for Java variable initialization's policy
                    else
                        time = newTimeFields[0] + ":" + newTimeFields[1];


                    processedTimeAnnotations.add("Time(" + time
                            /* Avoids characters problems */
                            .replace(':', '-') +
                            ")");
                    processedTimeAnnotations.add("Date(" + date + ")");
                }
            }

            if (machineIntent.contains("VEHICLE_TYPE")) {
                for (Map.Entry<String, List<String>> vehicleTypeExpressions : VEHICLE_TYPES.entrySet())
                    for (String expression : vehicleTypeExpressions.getValue())
                        if (annotation.get(CoreAnnotations.TextAnnotation.class).contains(expression))
                            processedVehicleTypeAnnotation.add("VehicleType(" + vehicleTypeExpressions.getKey() + ")");
            }


            /* ERRORS DETECTION */
            if (processedTimeAnnotations.size() > 0 &&
                    machineIntent.contains("TIME")) {
                String[] newDate = processedTimeAnnotations.stream().filter(d -> d.startsWith("Date")).findFirst().orElse("Date()")
                        .replace("Date(", "").replace(")", "").split("-");
                String[] newTime = processedTimeAnnotations.stream().filter(d -> d.startsWith("Time")).findFirst().orElse("Time()")
                        .replace("Time(", "").replace(")", "").split("-");
                if (
                    // the year is a past year, or...
                        Integer.parseInt(newDate[0]) < now.getYear() ||
                                // the month is a past month, or...
                                Integer.parseInt(newDate[0]) == now.getYear() &&
                                        Integer.parseInt(newDate[1]) < now.getMonthValue() ||
                                // the day is a past day, or...
                                Integer.parseInt(newDate[0]) == now.getYear() &&
                                        Integer.parseInt(newDate[1]) == now.getMonthValue() &&
                                        Integer.parseInt(newDate[2]) < now.getDayOfMonth() ||
                                // the time is a past time, or...
                                Integer.parseInt(newDate[0]) == now.getYear() &&
                                        Integer.parseInt(newDate[1]) == now.getMonthValue() &&
                                        Integer.parseInt(newDate[2]) == now.getDayOfMonth() &&
                                        Integer.parseInt(newTime[0]) < now.getHour() ||
                                // the hour is invalid (greater than 23 or less than 0, due to the correction taking in account the start time)
                                Integer.parseInt(newTime[0]) > 23 || Integer.parseInt(newTime[0]) < 0) {
                    errors.add("Error(" + "PastTimeError" + ")");
                }
            }


            StringJoiner j = new StringJoiner(", ", "[", "]");
            processedLocationAnnotations.forEach(j::add);
            processedTimeAnnotations.forEach(j::add);
            processedVehicleTypeAnnotation.forEach(j::add);
            if (checkForNegativeAnswer(tokens))
                j.add("Answer(false)");
            else if (checkForPositiveAnswer(tokens))
                j.add("Answer(true)");

            errors.forEach(j::add);

            /* Aknowledge the fact that the user as spoken! */
            if (errors.isEmpty())
                j.add("UU");
            else
                j.add("UE");
            String newInformation = j.toString();
            log.info(newInformation);

            system.addContent("NewInformation", newInformation);
        }
    }

    /**
     * Recognise the presence of a positive answer (e.g.: 'yes').
     *
     * @param tokens
     * @return
     */
    private boolean checkForPositiveAnswer(List<CoreLabel> tokens) {
        for (CoreLabel token : tokens)
            if (positiveAnswers.contains(token.originalText()))
                return true;
        return false;
    }

    /**
     * Recognise the presence of a negative answer (e.g.: 'no').
     *
     * @param tokens
     * @return
     */
    private boolean checkForNegativeAnswer(List<CoreLabel> tokens) {
        for (CoreLabel token : tokens)
            if (negativeAnswers.contains(token.originalText()))
                return true;
        return false;
    }


    /**
     * Separates cities' NERs from addresses' NERs.
     *
     * @param locationNERs the Lis<List<IndexedWord>> of NERs found in the (corrected) user utterance
     * @param cities       the (to-be-filled) List<LocationInfo> of cities' NERs
     * @param addresses    the (to-be-filled) List<LocationInfo> of addresses' NERs
     */
    private void partitionLocation(List<List<IndexedWord>> locationNERs, List<List<IndexedWord>> cities, List<List<IndexedWord>> addresses, List<CoreLabel> tokens, SemanticGraph dependencies) {
        for (List<IndexedWord> location : locationNERs) {
            String locationText = location.stream().map(IndexedWord::originalText).collect(Collectors.joining(" "));
            if (CITIES.stream().map(String::toLowerCase).collect(Collectors.toList()).contains(locationText.toLowerCase()))
                cities.add(location);
            else {
                addresses.add(location);
            }
        }
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
     * @param machineIntent     the previous machine state(that may contains the question the user is answering at)
     */
    private String correctUserUtterance(String originalUtterance, String machineIntent) {
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
        if (machineIntent.contains("TIME")) {
            int timeMaybe = -1;
            try {
                timeMaybe = Integer.parseInt(correctedUtterance.trim());
            } catch (NumberFormatException exception) {
            }
            if (timeMaybe > -1)
                correctedUtterance = "Alle " + correctedUtterance;
            else if (correctedUtterance.startsWith("le"))
                correctedUtterance = "Al" + correctedUtterance;
            else if (correctedUtterance.startsWith("Le"))
                correctedUtterance = "Alle " + correctedUtterance.substring(3);
        } else if (machineIntent.endsWith("START_CITY") || machineIntent.endsWith("START_SLOT")) {
            /*
             * If the user is answering to a question like "Da dove vuoi partire", the utternace should contain the
             *  preposition "da" (e.g.: Da dove vuoi partire? Da Pinerolo)
             */
            if (!correctedUtterance.toLowerCase().startsWith("da "))
                correctedUtterance = "Da " + correctedUtterance;
        } else if (machineIntent.endsWith("END_CITY") || machineIntent.endsWith("END_SLOT")) {
            /*
             * Similarly if the user is communication the city/spot in which s/he will leave the vehicle, the utterance
             *  should contain the preposition "a" (e.g.: "E dove voui posare l'auto? a Nichelino")
             */
            if (!correctedUtterance.toLowerCase().startsWith("a"))
                correctedUtterance = "A " + correctedUtterance;
        }

        /*
         * IMPORTANT: if the user utterance ends with a named entity (for example 'Voglio partire da piazza Castello')
         * the extraction process won't get this last one; a full stop (NER type "O", null) is therefore required and
         * has to be added if missing.
         */
        if (!originalUtterance.endsWith("."))
            correctedUtterance = correctedUtterance + ".";
        return correctedUtterance;
    }

    /**
     * Execute the geocoding for the found location via Nominatim.
     *
     * @param location the String with the location name
     * @return the JSON Object with the location innformation
     */
    private String getNominatimJSON(String location) {
        StringBuilder a = new StringBuilder();
        try {
            URL startAddress = new URL(NOMINATIM_SEARCH_URL + URLEncoder.encode(location, "UTF-8"));
            log.info(startAddress.toString());
            URLConnection geoConnection = startAddress.openConnection();
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(geoConnection.getInputStream(), StandardCharsets.UTF_8));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                a.append(inputLine);
            }
            in.close();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        return a.toString();
    }
}
