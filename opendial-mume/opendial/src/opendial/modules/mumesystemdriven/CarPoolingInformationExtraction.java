package opendial.modules.mumesystemdriven;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static opendial.modules.mumesystemdriven.Config.*;
import static opendial.modules.mumesystemdriven.Shared.*;

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

    // Google Goecoding
    private static Properties localGoogleMapsAPIPropeties;
    private static String[] macAddresses;
    private static String[] channels;
    // private GeoApiContext geoContext;    // Client-side library

    // Tint
    private TintPipeline pipeline;

    private enum TemporalModifier {
        MORNING("mattina", "mattino"),
        EVENING("pomeriggio", "sera", "notte");

        Set<String> texts;

        TemporalModifier(String... t) {
            this.texts = new HashSet<>(Arrays.asList(t));
        }
    }

    /**
     * Creates a new instance of the flight-booking module
     *
     * @param system the dialogue system to which the module should be attached
     */
    public CarPoolingInformationExtraction(DialogueSystem system) {
        this.system = system;
    }

    /**
     * starts the module.
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
                TREETAGGER
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

        /*
        // Client-side library
        geoContext = new GeoApiContext.Builder()
                .apiKey(localGoogleMapsAPIPropeties.getProperty("google.api.key"))
                .maxRetries(3)
                .retryTimeout(2, TimeUnit.SECONDS)
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
            //log.info("\n" + system.getState().getChanceNodes().toString());
            log.info("Machine Action:\t" + machineIntent);
            log.info("newInformation:\t" + state.queryProb("newInformation").getBest().toString());
            log.info("startSlot:\t" + state.queryProb("startSlot").getBest().toString());
            log.info("sortedStartSlots:\t" + state.queryProb("sortedStartSlots").getBest().toString());
            log.info("startCity:\t" + state.queryProb("startCity").getBest().toString());
            log.info("startSlotUnspecified:\t" + state.queryProb("startSlotUnspecified").getBest().toString());
            log.info("startLat:\t" + state.queryProb("startLat").getBest().toString());
            log.info("startLon:\t" + state.queryProb("startLon").getBest().toString());
            log.info("startDate:\t" + state.queryProb("startDate").getBest().toString());
            log.info("startTime:\t" + state.queryProb("startTime").getBest().toString());
            log.info("endSlot:\t" + state.queryProb("endSlot").getBest().toString());
            log.info("sortedEndSlots:\t" + state.queryProb("sortedEndSlots").getBest().toString());
            log.info("endCity:\t" + state.queryProb("endCity").getBest().toString());
            log.info("endTimeKnown:\t" + state.queryProb("endTimeKnown").getBest().toString());
            log.info("endDate:\t" + state.queryProb("endDate").getBest().toString());
            log.info("endTime:\t" + state.queryProb("endTime").getBest().toString());

            // Informations
            SortedMap<String, String> previousInformation = new TreeMap<>();
            String[] infoSlots = {
                    "startDate",
                    "startTime",
                    "startCity",
                    "startAddress",
                    "startSlot",
                    "sortedStartSlots",
                    "startLat",
                    "startLon",

                    "endDate",
                    "endCity",
                    "endAddress",
                    "endSlot",
                    "sortedEndSlots",
                    "endLat",
                    "endLon",
                    "endTime",
                    "vehicleType"
            };

            Arrays.stream(infoSlots).forEach(slot -> {
                if (state.hasChanceNode(slot))
                    previousInformation.put(slot, state.queryProb(slot).getBest().toString());
                else
                    previousInformation.put(slot, NONE);
            });

            log.info("\n");
            previousInformation.forEach((s, v) -> log.info(s + " = " + v));

            // 'Vorrei prenotare l'auto in piazza Vittorio Veneto a Pinerolo per domani dalle 14 alle sette'
            log.info("User said: '" + userUtterance + "'");

            userUtterance = correctUserUtterance(userUtterance, machineIntent, ZonedDateTime.now(), previousInformation.getOrDefault("startDate", NONE));
            log.info("Corrected user utterance: '" + userUtterance + "'");

            Annotation annotation;
            // try {
            // annotation = pipeline.run(stream, jsonOut, TintRunner.OutputFormat.JSON);
            // annotation = pipeline.run(stream, System.out, TintRunner.OutputFormat.JSON);
            annotation = pipeline.runRaw(userUtterance);

            /* TESTS
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
            */

            // No test
            List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
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

            List<Slot> slots = new ArrayList<>();
            List<City> cities = new ArrayList<>();
            List<List<IndexedWord>> addresses = new ArrayList<>();

            partitionLocation(tokens, dependencies, locationAnnotations, addresses, slots, cities);

            log.info("Slots:\t" + slots.stream().map(Slot::toString).collect(Collectors.joining(",\n", "[", "]")));
            log.info("Cities:\t" + cities.stream().map(City::toString).collect(Collectors.joining(",\n", "[", "]")));
            log.info("Addresses:\t" + addresses.stream().map(a -> a.stream().map(IndexedWord::originalText).collect(Collectors.joining(" "))).collect(Collectors.joining(",\n", "[", "]")));

            List<String> processedVehicleTypeAnnotation = new ArrayList<>();
            List<String> processedLocationAnnotations = new ArrayList<>();
            List<String> processedTimeAnnotations = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            /* TIME AND DATE */
            ZonedDateTime now = ZonedDateTime.now();
            if (machineIntent.contains("TIME")) {
                String date = "";
                String time = "";
                if (timeAnnotations.size() == 1) {
                    if (dateAnnotations.size() == 1) {
                        String currentDate = dateAnnotations.get(0).get(0).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class).split("T")[0];
                        if (!currentDate.equals("XXXX-XX-XX"))
                            date = currentDate;
                    }
                    if (date.isEmpty()) {
                        if (machineIntent.contains("END")) {
                            // start date should already has been setted (?)
                            date = state.queryProb("startDate").getBest().toString();
                        } else {
                            date = String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
                        }
                    }


                    // TODO check time corrections
                    // FIXME minutes in letters are a problem
                    boolean timeInLetters = !timeAnnotations.get(0).stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")).matches("[^\\d]*\\d+[^\\d]*");

                    log.info("Time in letters:\t" + timeInLetters);

                    IndexedWord timeWord = timeAnnotations.get(0).get(0);
                    String[] newTimeFields = timeWord.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class).split("T")[1].split(":");
                    if (machineIntent.contains("START")) {
                        time = String.format("%02d:%02d",
                                (Integer.parseInt(newTimeFields[0]) +
                                        ((timeInLetters &&
                                                (Integer.parseInt(date.split("-")[2]) == now.getDayOfMonth() &&
                                                        Integer.parseInt(newTimeFields[0]) < now.getHour()) ||
                                                (hasTemporalSpecification(timeWord, dependencies, TemporalModifier.EVENING))) ? 12 : 0)
                                ),
                                roundToPreviousQuarter(Integer.parseInt(newTimeFields[1])));
                    } else if (machineIntent.contains("END")) {
                        int roundedMinutes = roundToPreviousQuarter(Integer.parseInt(newTimeFields[1]));
                        if (roundedMinutes != Integer.parseInt(newTimeFields[1]))
                            roundedMinutes += QUARTER;
                        // start time should already has been setted
                        String startHour = state.queryProb("startTime").getBest().toString().split("-")[0];
                        time = String.format("%02d:%02d",
                                (Integer.parseInt(newTimeFields[0]) +
                                        ((timeInLetters && Integer.parseInt(newTimeFields[0]) <= Integer.parseInt(startHour)) ? 12 : 0) +
                                        // If the minutes has been rounded for excess, it may be necessary increment the hour too
                                        roundedMinutes / HOUR
                                ),
                                (roundedMinutes % HOUR));

                        if (time.isEmpty() && !durationAnnotations.isEmpty()) {
                            // Separate the duration of dateTtime
                            String[] durString = durationAnnotations.get(0).get(0).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class).split("T");
                            // Delete initial 'P'
                            durString[0] = durString[0].substring(1);

                        }
                    }
                } else if (checkForNowAnswer(tokens)) {
                    int roundedMinutes = roundToPreviousQuarter(now.getMinute());
                    boolean incHour = false;
                    if (machineIntent.contains("END")) {
                        roundedMinutes += QUARTER;
                        // Correct order
                        incHour = roundedMinutes / HOUR > 0;
                        roundedMinutes = roundedMinutes % HOUR;
                    }
                    time = String.format("%02d:%02d", (incHour) ? now.getHour() + 1 : now.getHour(), roundedMinutes);
                    date = String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
                }


                if (!time.isEmpty())
                    processedTimeAnnotations.add("Time(" + time
                            /* Avoids characters problems */
                            .replace(':', '-') +
                            ")");
                if (!date.isEmpty())
                    processedTimeAnnotations.add("Date(" + date + ")");
            }   /* time and date END */


            /* SLOT, GOOGLE_API_ADDRESS AND CITY */
            boolean floatingStartSlot = (state.hasChanceNode("startSlotUnspecified") &&
                    !state.queryProb("startSlotUnspecified").getBest().toString().equals(NONE)) ?
                    Boolean.valueOf(state.queryProb("startSlotUnspecified").getBest().toString()) :
                    true;
            if (machineIntent.contains("SLOT")) {
                /* to check if not empty */
                String slot = "";
                List<Slot> sortedSlots = new ArrayList<>();
                String address = "";
                String city = "";
                double lat = -100.0;
                double lon = -200.0;
                /* The user did not specified a city, so the 'city' variable's value has been inffered by the system */
                boolean inferredCity = false;

                JsonParser parser = new JsonParser();

                // String userCurrentPosition = getCurrentUserPosition(parser);
                // TESTING: Dipartimento di Informatica
                String userCurrentPosition = "45.08914,7.6560533";
                log.info("Current User Position:\t" + userCurrentPosition);
                String[] userPositions = userCurrentPosition.split(",");

                /* If the user knows the name of the parking slot and give it, no need of Google */
                if (!slots.isEmpty()) {
                    /*
                    // Sort the slots given by the user (hopefully just one)...
                    sortedSlots = slots.stream()
                            // ... by distance from the user's current position
                            .sorted((s1, s2) -> {
                                double latDiff1 = Double.parseDouble(userPositions[0]) - s1.getLatitude();
                                double lonDiff1 = Double.parseDouble(userPositions[1]) - s1.getLongitude();
                                double latDiff2 = Double.parseDouble(userPositions[0]) - s2.getLatitude();
                                double lonDiff2 = Double.parseDouble(userPositions[1]) - s2.getLongitude();
                                return Double.compare(Math.sqrt(latDiff1 * latDiff1 + lonDiff1 * lonDiff1), Math.sqrt(latDiff2 * latDiff2 + lonDiff2 * lonDiff2));
                            }).collect(Collectors.toList());
                    */

                    sortedSlots = sortSlots(slots, false, "", userPositions[0], userPositions[1]);

                    // Filter the slots based on the city explicitly communicated by the user
                    // If the user comminicated a city,...
                    if (cities.size() == 1) {
                        city = cities.get(0).getName();
                        inferredCity = false;

                        // ... remove the slot that are not in that city
                        String effectivelyFinalCity = city; // For stream purpuses
                        sortedSlots = sortedSlots.stream().filter(s -> s.getCity().getName().equalsIgnoreCase(effectivelyFinalCity)).collect(Collectors.toList());
                    }
                    // ... else, just retrieve the (first) slot's one
                    else if (!sortedSlots.isEmpty()) {
                        city = sortedSlots.get(0).getCity().getName();
                        // if there is some ambiguity in the city, signal it
                        inferredCity = sortedSlots.size() > 1;
                    }

                    // If there is at least one slot in the city given by the user (or in general)...
                    if (!sortedSlots.isEmpty()) {
                        // Retrive all the informaiton!
                        // Slot (1)
                        slot = sortedSlots.get(0).getName();
                        // Address (2)
                        address = sortedSlots.get(0).getAddress();
                        // City (3)
                        // 'city' already set
                        // InferredCity (4)
                        // 'inferredCity' already set

                        // Latitude (5)
                        lat = sortedSlots.get(0).getLatitude();
                        // Longitude (6)
                        lon = sortedSlots.get(0).getLongitude();

                        // SortedSlots (7)
                        if (sortedSlots.size() > 1)
                            sortedSlots = sortedSlots.subList(1, sortedSlots.size());
                        else
                            sortedSlots = new LinkedList<>();

                        // UnspecifiedSlot (8)
                        floatingStartSlot = false;
                    } /*else
                    errors.add("Error(" + "NoSlotFoundError" + ")");
                    */
                }
                /* Else, if the user give an address, we can retrieve the nearest slot (or slots) */
                else if (addresses.size() == 1) {
                    // The user did not gave the precise slot
                    floatingStartSlot = true;

                    // Construct the partial address for Google to complete
                    String toGoogleMaps = addresses.get(0).stream().map(IndexedWord::originalText).collect(Collectors.joining(" "));
                    // If the user gave the city,...
                    if (cities.size() == 1) {
                        // ... retrieve it ...
                        city = cities.get(0).getName();
                        inferredCity = false;

                        // ... and append to the address
                        toGoogleMaps += " " + city;
                    } else
                        inferredCity = true;

                    // JSON object for the complete address
                    JsonObject queryCompletionResult = null;
                    try {
                        queryCompletionResult = parser.parse(getGoogleMapsResponseJSON(getMapsSearchURL(toGoogleMaps, userCurrentPosition), false, "")).getAsJsonObject();
                        log.info("Response:\t" + queryCompletionResult.toString());
                    } catch (MalformedURLException | NullPointerException exception) {
                        exception.printStackTrace();
                    }
                    if (isOK(queryCompletionResult)) {
                        JsonObject location = queryCompletionResult.get(CANDIDATES).getAsJsonArray().get(0).getAsJsonObject().get("geometry")
                                .getAsJsonObject().get("location").getAsJsonObject();
                        JsonObject geoQueryResult = null;
                        try {
                            geoQueryResult = parser.parse(getGoogleMapsResponseJSON(getMapsReverseGeocodingURL(location.get(LATITUDE) + "," + location.get(LONGITUDE), GEOCODING_LOCALITY), false, "")).getAsJsonObject();
                        } catch (MalformedURLException excpetion) {
                            excpetion.printStackTrace();
                        }
                        if (isOK(geoQueryResult) && geoQueryResult.get(RESULTS).getAsJsonArray().size() > 0) {
                            JsonArray components = geoQueryResult.get(RESULTS).getAsJsonArray().get(0).getAsJsonObject().get(COMPONENTS).getAsJsonArray();
                            // for (JsonElement component : components) {
                            for (int i = 0; i < components.size() && (address.isEmpty() || city.isEmpty()); i++) {
                                JsonObject component = components.get(i).getAsJsonObject();
                                JsonArray types = component.get(COMPONENT_TYPES).getAsJsonArray();
                                for (JsonElement type : types) {
                                    if (type.getAsString().equals(ROUTE))
                                        address = component.getAsJsonObject().get(LONG_NAME).getAsString();
                                    else if (type.getAsString().equals(LOCALITY) && city.isEmpty()) {
                                        City cityMaybe = City.getByName(component.getAsJsonObject().get(LONG_NAME).getAsString());
                                        if (cityMaybe != null)
                                            city = cityMaybe.getName();
                                    }
                                }
                            }

                        /*
                        Stream<Slot> sortedSlotsStream = Arrays.stream(Slot.values()).filter(s -> {
                            double latDiff = location.get("lat").getAsDouble() - s.getLatitude();
                            double lonDiff = location.get("lng").getAsDouble() - s.getLongitude();
                            return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff) < DISTANCE_THRESHOLD;
                        });
                        // If the user gave the city, retain only the slot of that city
                        if (!inferredCity) {
                            String effectivelyFinalCity = city;
                            sortedSlotsStream = sortedSlotsStream.filter(s -> s.getCity().name().equals(effectivelyFinalCity));
                        }
                        sortedSlots = sortedSlotsStream.sorted((s1, s2) -> {
                            double latDiff1 = location.get("lat").getAsDouble() - s1.getLatitude();
                            double lonDiff1 = location.get("lng").getAsDouble() - s1.getLongitude();
                            double latDiff2 = location.get("lat").getAsDouble() - s2.getLatitude();
                            double lonDiff2 = location.get("lng").getAsDouble() - s2.getLongitude();
                            return Double.compare(Math.sqrt(latDiff1 * latDiff1 + lonDiff1 * lonDiff1), Math.sqrt(latDiff2 * latDiff2 + lonDiff2 * lonDiff2));
                        }).collect(Collectors.toList());
                        */

                            sortedSlots = sortSlots(Arrays.asList(Slot.values()), true, ((!inferredCity) ? city : ""), location.get(LATITUDE).getAsString(), location.get(LONGITUDE).getAsString());

                            if (!sortedSlots.isEmpty()) {
                                // Slot (1)
                                slot = sortedSlots.get(0).getName();
                                // Address (2)
                                // 'address' already set
                                // City (3)
                                // 'city' alteady set
                                // InferredCity (4)
                                // 'inferredCity' already set

                                // Latitude (5)
                                lat = location.get("lat").getAsDouble();
                                // Lobgitude (6)
                                lon = location.get("lng").getAsDouble();

                                // SortedSlots (7)
                                sortedSlots = sortedSlots.subList(1, sortedSlots.size());

                                // UnspecifiedSlot (8)
                                floatingStartSlot = true;
                            }
                        }
                    }/*else
                    errors.add("Error(" + "NoSlotFound" + ")");
                    */
                }
                /* Else, if the user communicate just the city, select the slot nearer to s/he position */
                else if (cities.size() == 1 && addresses.isEmpty()) {
                    // Retrieve the city gave by the user
                    city = cities.get(0).getName();

                    /*
                    sortedSlots = Arrays.stream(Slot.values()).filter(s -> {
                        double latDiff = Double.parseDouble(userPositions[0]) - s.getLatitude();
                        double lonDiff = Double.parseDouble(userPositions[1]) - s.getLongitude();
                        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff) < DISTANCE_THRESHOLD;
                    }).filter(s -> s.getCity().name().equals(effectivelyFinalCity)).sorted((s1, s2) -> {
                        double latDiff1 = Double.parseDouble(userPositions[0]) - s1.getLatitude();
                        double lonDiff1 = Double.parseDouble(userPositions[1]) - s1.getLongitude();
                        double latDiff2 = Double.parseDouble(userPositions[0]) - s2.getLatitude();
                        double lonDiff2 = Double.parseDouble(userPositions[1]) - s2.getLongitude();
                        return Double.compare(Math.sqrt(latDiff1 * latDiff1 + lonDiff1 * lonDiff1), Math.sqrt(latDiff2 * latDiff2 + lonDiff2 * lonDiff2));
                    }).collect(Collectors.toList());
                    */

                    // Retains only the slot in the city specified by the user and sort them by the distance from the current position if the user
                    sortedSlots = sortSlots(Arrays.asList(Slot.values()), false, city, userPositions[0], userPositions[1]);

                    if (!sortedSlots.isEmpty()) {
                        // Slot (1)
                        slot = sortedSlots.get(0).getName();
                        // Address (2)
                        address = sortedSlots.get(0).getAddress();
                        // City (3)
                        // 'city' already set
                        // InferredCity (4)
                        inferredCity = false;
                        // Latitude (5)
                        lat = Double.parseDouble(userPositions[0]);
                        // Longitude (6)
                        lon = Double.parseDouble(userPositions[1]);
                        // SortedSlots (7)
                        sortedSlots = sortedSlots.subList(1, sortedSlots.size());
                        // UnspecifiedSlot (8)
                        floatingStartSlot = true;
                    } /*else
                    errors.add("Error(" + "NoSlotFound" + ")");
                    */
                }
                /* Otherwise, select the nearest slot to the current position of th user if s/he indicate that wants to start from there */
                else if (hereAnswer(tokens)) {
//                    // Retrive the address and the city of the current user postion
//                    try {
//                        JsonObject currentUserAddress = parser.parse(getGoogleMapsResponseJSON(getMapsReverseGeocodingURL(userCurrentPosition, GEOCODING_LOCALITY), false, "")).getAsJsonObject();
//                        if (currentUserAddress != null && currentUserAddress.get("status").getAsString().equals("OK") && currentUserAddress.get("results").getAsJsonArray().size() > 0) {
//                            JsonObject bestResult = currentUserAddress.get(RESULTS).getAsJsonArray().get(0).getAsJsonObject();
//                            address = bestResult.get(COMPLETE_ADDRESS).getAsString();
//                            city = bestResult.get(COMPONENTS).getAsJsonArray().get(0).getAsJsonObject().get(LONG_NAME).getAsString();
//                            // Do not ask confirmation about the city, the user does not care
//                            inferredCity = false;
//                        }
//                    } catch (MalformedURLException excpetion) {
//                        excpetion.printStackTrace();
//                    }
                    // Retrive the address and the city of the current user position
                    JsonObject currentUserAddress = null;
                    try {
                        currentUserAddress = parser.parse(getGoogleMapsResponseJSON(getMapsReverseGeocodingURL(userCurrentPosition, GEOCODING_LOCALITY), false, "")).getAsJsonObject();
                    } catch (MalformedURLException excpetion) {
                        excpetion.printStackTrace();
                    }
                    if (isOK(currentUserAddress) && currentUserAddress.get(RESULTS).getAsJsonArray().size() > 0) {
                        inferredCity = true;
                        JsonArray components = currentUserAddress.get(RESULTS).getAsJsonArray().get(0).getAsJsonObject().get(COMPONENTS).getAsJsonArray();
                        // for (JsonElement component : components) {
                        for (int i = 0; i < components.size() && (address.isEmpty() || city.isEmpty()); i++) {
                            JsonObject component = components.get(i).getAsJsonObject();
                            JsonArray types = component.get(COMPONENT_TYPES).getAsJsonArray();
                            for (JsonElement type : types) {
                                if (type.getAsString().equals(ROUTE))
                                    address = component.getAsJsonObject().get(LONG_NAME).getAsString();
                                else if (type.getAsString().equals(LOCALITY) && city.isEmpty()) {
                                    City cityMaybe = City.getByName(component.getAsJsonObject().get(LONG_NAME).getAsString());
                                    if (cityMaybe != null) {
                                        city = cityMaybe.getName();
                                        // Do not ask confirmation about the city, the user does not care
                                        inferredCity = false;
                                    }
                                }
                            }
                        }
                    }
                    // Do not filter by city, the user did not give that
                    /*
                    sortedSlots = Arrays.stream(Slot.values()).filter(s -> {
                        double latDiff = Double.parseDouble(userPositions[0]) - s.getLatitude();
                        double lonDiff = Double.parseDouble(userPositions[1]) - s.getLongitude();
                        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff) < DISTANCE_THRESHOLD;
                    }).sorted((s1, s2) -> {
                        double latDiff1 = Double.parseDouble(userPositions[0]) - s1.getLatitude();
                        double lonDiff1 = Double.parseDouble(userPositions[1]) - s1.getLongitude();
                        double latDiff2 = Double.parseDouble(userPositions[0]) - s2.getLatitude();
                        double lonDiff2 = Double.parseDouble(userPositions[1]) - s2.getLongitude();
                        return Double.compare(Math.sqrt(latDiff1 * latDiff1 + lonDiff1 * lonDiff1), Math.sqrt(latDiff2 * latDiff2 + lonDiff2 * lonDiff2));
                    }).collect(Collectors.toList());
                    */

                    sortedSlots = sortSlots(Arrays.asList(Slot.values()), true, "", userPositions[0], userPositions[1]);

                    if (!sortedSlots.isEmpty()) {
                        // Slot (1)
                        slot = sortedSlots.get(0).getName();
                        // Address (2)
                        // 'address' already set
                        // City (3)
                        // 'city' already set
                        // InferredCity (4)
                        // 'inferredCity' already set
                        // Latitude (5)
                        lat = Double.parseDouble(userPositions[0]);
                        // Latitude (6)
                        lon = Double.parseDouble(userPositions[1]);
                        // SortedSlots (7)
                        sortedSlots = sortedSlots.subList(0, sortedSlots.size());
                        // UnspecifiedSlot(8)
                        floatingStartSlot = true;
                    }
                }

                if (!slot.isEmpty()) {
                    processedLocationAnnotations.add("Slot(" + slot + ")"); // 1
                    if (machineIntent.contains("START_SLOT") && !sortedSlots.isEmpty())
                        system.addContent("sortedStartSlots", sortedSlots.stream().map(Slot::toString).collect(Collectors.joining(",")));  // 7
                    else if (machineIntent.contains("END_SLOT") && !sortedSlots.isEmpty())
                        system.addContent("sortedEndSlots", sortedSlots.stream().map(Slot::toString).collect(Collectors.joining(",")));  // 7
                    processedLocationAnnotations.add("Address(" + address + ")");   // 2
                    processedLocationAnnotations.add(((inferredCity) ? "InferredCity(" : "City(") + city + ")");    // 3 - 4
                    processedLocationAnnotations.add("Lat(" + String.valueOf(lat)   // 5
                            /* Avoids characters problems */
                            .replace(".", "_") + ")");
                    processedLocationAnnotations.add("Lon(" + String.valueOf(lon)   // 6
                            /* Avoids characters problems */
                            .replace(".", "_") + ")");
                } /*else
                    errors.add("Error(" + "NoSlotFound" + ")");
                    */
            } else if (machineIntent.contains("CITY") && cities.size() == 1)
                processedLocationAnnotations.add("City(" + cities.get(0).getName() + ")");
            /* slot, address and city END */


            /* VEHICLE */
            if (machineIntent.contains("VEHICLE_TYPE")) {
                for (Map.Entry<String, List<String>> vehicleTypeExpressions : VEHICLE_TYPES.entrySet())
                    for (String expression : vehicleTypeExpressions.getValue())
                        if (annotation.get(CoreAnnotations.TextAnnotation.class).contains(expression))
                            processedVehicleTypeAnnotation.add("VehicleType(" + vehicleTypeExpressions.getKey() + ")");
            }   /* vehicle END */


            log.info("New Location Info:\t" + processedLocationAnnotations.toString());
            log.info("New Time Info:\t" + processedTimeAnnotations.toString());
            log.info("New Vehicle Info:\t" + processedVehicleTypeAnnotation.toString());


            /* ERRORS DETECTION */
            if (!processedTimeAnnotations.isEmpty() &&
                    machineIntent.contains("TIME")) {
                String[] newDate = processedTimeAnnotations.stream().filter(d -> d.startsWith("Date")).findFirst().orElse("Date()")
                        .replace("Date(", "").replace(")", "").split("-");
                String[] newTime = processedTimeAnnotations.stream().filter(d -> d.startsWith("Time")).findFirst().orElse("Time()")
                        .replace("Time(", "").replace(")", "").split("-");
                if (newDate.length > 1 &&
                        // the year is a past year, or...
                        (Integer.parseInt(newDate[0]) < now.getYear() ||
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
                                        (newTime.length > 1 && Integer.parseInt(newTime[0]) < now.getHour())) ||
                        // the hour is invalid (greater than 23 or less than 0, due to the correction taking in account the start time)
                        //  (this should not happen)
                        newTime.length > 1 && (Integer.parseInt(newTime[0]) > 23 || Integer.parseInt(newTime[0]) < 0)) {
                    errors.add("Error(" + "PastTimeError" + ")");
                }

                /*
                if (machineIntent.contains("END")) {
                    String[] startDate = state.queryProb("startDate").getBest().toString()
                            .replace("Date(", "").replace(")", "").split("-");
                    String[] startTime = state.queryProb("startTime").getBest().toString()
                            .replace("Time(", "").replace(")", "").split("-");
                    // TODO check trip length error

                    errors.add("Error(" + "TooShort" + ")");
                }
                */
            }


            StringJoiner j = new StringJoiner(", ", "[", "]");
            processedLocationAnnotations.forEach(j::add);
            processedTimeAnnotations.forEach(j::add);
            processedVehicleTypeAnnotation.forEach(j::add);
            if (checkForNegativeAnswer(tokens))
                j.add("Answer(false)");
            else if (checkForPositiveAnswer(tokens))
                j.add("Answer(true)");

            if (machineIntent.contains("START_SLOT"))
                j.add("StartSlotUnspecified(" + ((floatingStartSlot) ? String.valueOf(true) : String.valueOf(false)) + ")"); // 8

            errors.forEach(j::add);

            /* Aknowledge the fact that the user as spoken! */
            if (errors.isEmpty())
                j.add("UU");
            else
                j.add("UE");
            String newInformation = j.toString();
            log.info(newInformation);

            system.addContent("newInformation", newInformation);
        }

    }

    private List<Slot> sortSlots(List<Slot> slots, boolean filterDistance, String filterCity, String biasLatitude, String biasLongitude) {
        Stream<Slot> sortedSlotsStream = slots.stream();

        // Filter by distance from the specified start point (if the slot was not given)
        if (filterDistance)
            sortedSlotsStream = sortedSlotsStream.filter(s -> {
                double latDiff = Double.parseDouble(biasLatitude) - s.getLatitude();
                double lonDiff = Double.parseDouble(biasLongitude) - s.getLongitude();
                return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff) < DISTANCE_THRESHOLD;
            });

        // ... and by city, if any was given;
        if (!filterCity.isEmpty())
            sortedSlotsStream = sortedSlotsStream.filter(s -> s.getCity().name().equalsIgnoreCase(filterCity));

        // then sort by distance from the specified start point and return
        return sortedSlotsStream.sorted((s1, s2) -> {
            double latDiff1 = Double.parseDouble(biasLatitude) - s1.getLatitude();
            double lonDiff1 = Double.parseDouble(biasLongitude) - s1.getLongitude();
            double latDiff2 = Double.parseDouble(biasLatitude) - s2.getLatitude();
            double lonDiff2 = Double.parseDouble(biasLongitude) - s2.getLongitude();
            return Double.compare(Math.sqrt(latDiff1 * latDiff1 + lonDiff1 * lonDiff1), Math.sqrt(latDiff2 * latDiff2 + lonDiff2 * lonDiff2));
        }).collect(Collectors.toList());
    }

    /**
     * Recognise the presence of a positive answer (e.g.: 'yes').
     *
     * @param tokens the List<IndexedWord> of the user utterance
     * @return true if the user uttarance contains a clue of a positive answer, false otherwise
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
     * @param tokens the List<IndexedWord> of the user utterance
     * @return true if the user uttarance contains a clue of a negative answer, false otherwise
     */
    private boolean checkForNegativeAnswer(List<CoreLabel> tokens) {
        for (CoreLabel token : tokens)
            if (negativeAnswers.contains(token.originalText()))
                return true;
        return false;
    }

    /**
     * Recognise the time indication corresponding to the 'now' time (e.g.: 'adesso').
     *
     * @param tokens the List<IndexedWord> of the user utterance
     * @return true if the user uttarance contains a clue of the 'now' time, false otherwise
     */
    private boolean checkForNowAnswer(List<CoreLabel> tokens) {
        for (CoreLabel token : tokens)
            if (nowAnswers.contains(token.originalText()))
                return true;
        return false;
    }

    /**
     * Recognise the time indication corresponding to the 'here' location (e.g.: 'qui', 'dove mi trovo adesso').
     *
     * @param tokens the List<IndexedWord> of the user utterance
     * @return true if the user uttarance contains a clue of the 'here' place, false otherwise
     */
    private boolean hereAnswer(List<CoreLabel> tokens) {
        for (CoreLabel token : tokens)
            if (hereAnswers.contains(token.originalText()))
                return true;
        return false;
    }

    /**
     * Return the set of slots whose address match at least partilly with the one given by the user.
     * <p>
     * This methods check if the given address corresponds to one of the address of same slot.
     * Each slot may have other addresses besides that the platform default, like the original address or those retrieved by Google.
     *
     * @param address the String of the address communicated by the user
     * @return the Set<Slot> of the Slot for which at least one address (partially) matches with the user given one
     */
    private Set<Slot> partialMatchWithSlotAddress(List<IndexedWord> address) {
        // TODO implement
        Set<Slot> toReturn = new HashSet<>();
        Set<String> addressSet = address.stream().map(t -> t.originalText().toLowerCase()).collect(Collectors.toSet());
        for (Slot slot : Slot.values())
            for (String slotAddress : slot.getAddresses()) {
                Set<String> addressParts = Arrays.stream(slotAddress.split(" ")).map(s -> s.toLowerCase().replace(",", "")).collect(Collectors.toSet());
                if (addressParts.containsAll(addressSet))
                    toReturn.add(slot);
            }
        return toReturn;
    }

    /**
     * Separates cities' NERs from addresses' NERs and slots' NERs.
     *
     * @param tokens              the List<IndexedWord> of tokens found in the (corrected) user utterance
     * @param dependencies        the SemanticGraph of the user utterance's dependencies
     * @param locationAnnotations the List<List<IndexedWord>> of the location recognised by Tint (if any)
     * @param addresses           the (to-be-filled) List<LocationInfo> of addresses' NERs
     * @param slots               the (to-be-filled) List<LocationInfo> of slots' NERs
     * @param cities              the (to-be-filled) List<LocationInfo> of cities' NERs
     */
    private void partitionLocation(List<CoreLabel> tokens, SemanticGraph dependencies, List<List<IndexedWord>> locationAnnotations, List<List<IndexedWord>> addresses, List<Slot> slots, List<City> cities) {
        Map<String, Integer> slotNames = new HashMap<>();
        for (Slot slot : Slot.values())
            slotNames.put(slot.getName(), slot.getNumberOfWords());

        Set<Integer> indexToCheck = tokens.stream().map(CoreLabel::index).collect(Collectors.toSet());

        // ADDRESSES
        for (CoreLabel token : tokens) {
            // if the token is a address clue (e.g. 'piazza'),...
            if (indexToCheck.contains(token.index())) {
                if (ADDRESS_CLUE.contains(token.originalText().toLowerCase())) {
                    IndexedWord tokenIndexedWord = dependencies.getNodeByIndexSafe(token.index());
                    List<IndexedWord> subGraph = new ArrayList<>(Collections.singletonList(tokenIndexedWord));
                    // Retrieve parent, siblings and children of the clue token, if any, ...
                    IndexedWord parent = dependencies.getParent(tokenIndexedWord);
                    if (parent != null)
                        subGraph.add(parent);
                    Collection<IndexedWord> siblings = dependencies.getSiblings(tokenIndexedWord);
                    if (siblings != null && !siblings.isEmpty())
                        subGraph.addAll(siblings);
                    Collection<IndexedWord> children = dependencies.getChildren(tokenIndexedWord);
                    if (children != null && !children.isEmpty())
                        subGraph.addAll(children);
                    // ... and sort them by index
                    List<IndexedWord> sortedSubGraph = subGraph.stream().sorted(Comparator.comparing(IndexedWord::index)).collect(Collectors.toList());
                    log.info("Sorted subgraph:\t" + sortedSubGraph);
                    // sortedSubGraph is not empty: there is al least the clue token in exam

                    // Filter: retain only the tokens in a contunuus sequence
                    List<IndexedWord> address = new LinkedList<>();
                    // drop th tokens before the clue token
                    boolean stop = false;
                    Iterator<IndexedWord> tokensIterator = sortedSubGraph.iterator();
                    IndexedWord currentToken = null;
                    // Skip tokens before the clue token
                    int currentIndex = token.index();
                    do {
                        if (currentToken != null)
                            log.info("Skipping: " + currentToken.originalText());
                        currentToken = tokensIterator.next();
                        if (currentToken != null && currentToken.index() >= currentIndex)
                            stop = true;
                    } while (tokensIterator.hasNext() && !stop);
                    // Add to the final address only the chain of token without interruption
                    address.add(currentToken);
                    currentIndex = currentToken.index();
                    stop = false;
                    while (tokensIterator.hasNext() && !stop) {
                        currentToken = tokensIterator.next();
                        if (currentToken.index() == currentIndex + 1) {
                            address.add(currentToken);
                            currentIndex++;
                        } else
                            stop = true;
                    }

                    // Remove from the checklist the extracted token
                    indexToCheck.removeAll(address.stream().map(IndexedWord::index).collect(Collectors.toSet()));

                    log.info("Address:\t" + address.stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")));
                    addresses.add(address);

                    City maybeCity = City.extractFromAddress(address);
                    if (maybeCity != null)
                        cities.add(maybeCity);
                } else {
                    // if (indexToCheck.contains(token.index()))   // Superfluous
                    for (Map.Entry<String, Integer> slot : slotNames.entrySet())
                        /* IMPORTANT: IndexedWord's indeces and TokensAnnotation's indeces start from 1 */
                        if (token.index() + slot.getValue() <= tokens.size()) {
                            List<CoreLabel> slotTokens = tokens.subList(token.index() - 1, token.index() + slot.getValue() - 1);
                            List<Slot> slotIndexed = Slot.getByName(slotTokens.stream().map(t -> dependencies.getNodeByIndex(t.index()).originalText()).collect(Collectors.joining(" ")));
                            if (slotTokens.stream().map(CoreLabel::originalText).collect(Collectors.joining(" ")).equalsIgnoreCase(slot.getKey())) {
                                slots.addAll(slotIndexed);
                                indexToCheck.removeAll(slotTokens.stream().map(CoreLabel::index).collect(Collectors.toSet()));
                            }
                        }

                    if (indexToCheck.contains(token.index()))
                        for (City city : City.values())
                            /* IMPORTANT: IndexedWord's indeces and TokensAnnotation's indeces start from 1 */
                            if (token.index() + city.getNumberOfWords() <= tokens.size()) {
                                List<CoreLabel> cityTokens = tokens.subList(token.index() - 1, token.index() + city.getNumberOfWords() - 1);
                                City cityIndexed = City.getByName(cityTokens.stream().map(t -> dependencies.getNodeByIndex(t.index()).originalText()).collect(Collectors.joining(" ")));
                                if (cityTokens.stream().map(CoreLabel::originalText).collect(Collectors.joining(" ")).equalsIgnoreCase(city.getName())) {
                                    cities.add(cityIndexed);
                                    indexToCheck.removeAll(cityTokens.stream().map(CoreLabel::index).collect(Collectors.toSet()));
                                }
                            }
                }
            }
        }

        for (List<IndexedWord> loc : locationAnnotations) {
            if (indexToCheck.containsAll(loc.stream().map(IndexedWord::index).collect(Collectors.toSet())))
                addresses.add(loc);
        }
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
    private String correctUserUtterance(String originalUtterance, String machinePrevState, ZonedDateTime now, String startDate) {
        String correctedUtterance = originalUtterance;
        // NO correctedUtterance = correctedUtterance.toLowerCase();

        correctedUtterance = correctedUtterance.replaceAll("\\s+", " ");

        /*
         * In one cases the hour starts with a vovel, "all'una":
         *  Delete the apostrophe and add "-le" ("all'una -> alle una") to reduce the possible utterance forms' number.
         */
        correctedUtterance = correctedUtterance.replace("l'una", "le una");

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
                    "([Ii]l|[Dd]al|[Aa]l) (\\d{1,2})(?! \\d? ([Gg]ennaio|[Ff]ebbraio|[Mm]arzo|[Aa]prile|[Mm]aggio|[Gg]iugno|[Ll]uglio|[Aa]gosto|[Ss]ettembre|[Oo]ttobre|[Nn]ovembre|[Dd]icembre))"
            );
            Matcher monthDayMatcher = monthDayPattern.matcher(correctedUtterance);
            String monthName = "";
            log.info("Month Name:\t" + monthName);
            for (String month : new String[]{"gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"})
                if (correctedUtterance.contains(month))
                    monthName = month;
            log.info("Month Name:\t" + monthName);
            if (monthName.isEmpty())
                monthName = ((startDate != null && !startDate.equals(NONE)) ?
                        // ... then s/he probably imply the same month...
                        getMonthName(Integer.parseInt(startDate.split("-")[1])) :
                        // ... otherwise, the current month
                        getMonthName(now.getMonthValue()));
            while (monthDayMatcher.find()) {
                log.info("Matcher Match");
                log.info("Month Name:\t" + monthName);
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
            if (!correctedUtterance.startsWith("da ") && !correctedUtterance.startsWith("dall'"))
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

        /* Tint has some problem with accented letters in the weekdays names: replace with the non-accented letter (it works fine) */
        correctedUtterance = correctedUtterance.replaceAll("luned.", "lunedi");
        correctedUtterance = correctedUtterance.replaceAll("marted.", "martedi");
        correctedUtterance = correctedUtterance.replaceAll("mercoled.", "mercoledi");
        correctedUtterance = correctedUtterance.replaceAll("gioved.", "giovedi");
        correctedUtterance = correctedUtterance.replaceAll("venerd.", "venerdi");

        /* The systma can handler just one sentence at one time */
        String subCorrectedUtterance = correctedUtterance.substring(0, correctedUtterance.length() - 1);
        correctedUtterance = subCorrectedUtterance.replace(".", " e ").replace(";", " e ") + correctedUtterance.charAt(correctedUtterance.length() - 1);

        /* Tint has some problem with the hou format \d\d:\d\d */
        correctedUtterance = correctedUtterance.replaceAll("(\\d{1,2}):(\\d\\d)", "$1 e $2");

        /*
         * IMPORTANT: if the user utterance ends with a named entity (for example 'Voglio partire da piazza Castello')
         * the extraction process won't get this last one; a full stop (NER type "O", null) is therefore required and
         * has to be added if missing.
         */
        boolean endWithPunct = false;
        for (int i = 0; i < PUNCT.size() && !endWithPunct; i++) {
            String punct = PUNCT.get(i);
            if (!punct.isEmpty() && originalUtterance.endsWith(PUNCT.get(i)))
                endWithPunct = true;
        }
        if (!endWithPunct)
            correctedUtterance = correctedUtterance + ".";
        return correctedUtterance;
    }

    /*
    /**
     * Execute the geocoding for the found location via Nominatim.
     *
     * @param location the String with the location name
     * @return the String of the JSONObject with the location information
     /
    private String getNominatimJSON(String location) {
        StringBuilder a = new StringBuilder();
        try {
            URL startAddress = new URL(NOMINATIM_SEARCH_URL + URLEncoder.encode(location, StandardCharsets.UTF_8));
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
    */

    private static boolean isOK(JsonObject queryReult) {
        return queryReult != null && queryReult.get("status").getAsString().equals("OK");
    }

    /**
     * Returns the response send by the asked Google Maps service.
     *
     * @param service     the URL of the needed Google Maps service
     * @param postRequest must be true if the request is a POST request (e.g. Geolocation)
     * @param query       the JSON query for a POST request (ignore if postRequest is not true)
     * @return the String of the JSONObject of the service response
     */
    private static String getGoogleMapsResponseJSON(URL service, boolean postRequest, String query) {
        StringBuilder a = new StringBuilder();
        try {
            log.info("Request:\t" + service.toString());
            URLConnection geoConnection = service.openConnection();
            geoConnection.setDoOutput(postRequest);
            geoConnection.setRequestProperty("Content-Type", "application/json;charset=" + StandardCharsets.UTF_8);
            if (postRequest) {
                try (OutputStream output = geoConnection.getOutputStream()) {
                    output.write(query.getBytes(StandardCharsets.UTF_8));
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(geoConnection.getInputStream(), StandardCharsets.UTF_8));
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

    /**
     * Retrive the current user position's coordinates.
     *
     * @param parser the JsonParser for reading the response of the Google Geolocation service (just memory economy)
     * @return the String with the coordinates in the LAT,LON format, or the emprty String in case of an error
     */
    private static String getCurrentUserPosition(JsonParser parser) {
        try {
            JsonArray wifiAccessPoints = new JsonArray();
            for (int i = 0; i < macAddresses.length; i++) {
                JsonObject wifiAccessPoint = new JsonObject();
                wifiAccessPoint.addProperty("macAddress", macAddresses[i]);
                wifiAccessPoint.addProperty("channel", channels[i]);
                wifiAccessPoints.add(wifiAccessPoint);
            }
            JsonObject jsonQuery = new JsonObject();
            jsonQuery.add("wifiAccessPoints", wifiAccessPoints);
            log.info("User Current Position Request:\t" + jsonQuery.toString());
            JsonObject userCurrentPositionObject = parser.parse(getGoogleMapsResponseJSON(getMapsGeolocationURL(), true, jsonQuery.toString())).getAsJsonObject().get("location").getAsJsonObject();
            if (isOK(userCurrentPositionObject))
                return userCurrentPositionObject.get(LATITUDE) + "," + userCurrentPositionObject.get(LONGITUDE);
            else
                return "";
        } catch (MalformedURLException exception) {
            exception.printStackTrace();
        }
        return "";
    }

    /**
     * Construct the URL for the Google Maps Place Search API eequest.
     *
     * @param input the String with the location (partial) address
     * @return the URL for the Google Maps Place Search kind of request
     * @throws MalformedURLException if input or bias are not encodable or are malformed
     */
    private static URL getMapsSearchURL(String input, String bias) throws MalformedURLException {
        return new URL(MAPS_SEARCH +
                KEY + localGoogleMapsAPIPropeties.getProperty("google.api.key") + "&" +
                INPUT + URLEncoder.encode(input, StandardCharsets.UTF_8) + "&" +
                INPUT_TYPE + "&" +
                LANGUAGE + "&" +
                FIELDS + "&" +
                ((bias.isEmpty()) ? LOCATION_BIAS_RECTANGLE : LOCATION_BIAS + bias));
    }


    /**
     * Construct the URL for the Google Maps Geolocation API request.
     *
     * @return the URL for the Google Maps Geolocation kind of request
     */
    private static URL getMapsGeolocationURL() throws MalformedURLException {
        return new URL(MAPS_GEOLOCATION +
                KEY + localGoogleMapsAPIPropeties.getProperty("google.api.key"));
    }

    /**
     * Construct the URL for the Google Maps Geocoding API request.
     *
     * @param address the String with the address whise coordinates are needed
     * @return the URL for the Google Maps Geocoding kind of request
     * @throws MalformedURLException if is not encodable or malformed
     */
    private static URL getMapsGeocodingURL(String address) throws MalformedURLException {
        return new URL(MAPS_GEOCODING +
                KEY + localGoogleMapsAPIPropeties.getProperty("google.api.key") + "&" +
                GOOGLE_API_ADDRESS + URLEncoder.encode(address, StandardCharsets.UTF_8) + "&" +
                AREA_BOUNDING + "&" +
                LANGUAGE + "&" +
                REGION);
    }

    /**
     * Construct the URL for the Google Maps Reverse Geocoding API request.
     *
     * @param latLng the String of the latitude and longitued of the poinr whose address is needed, in the LAT,LON format
     * @param types  the optional information types needed
     * @return the URL for the Google Maps Reverse Geocoding kind of request
     * @throws MalformedURLException if latLng or types are malformed or invalid for this type of request
     */
    private static URL getMapsReverseGeocodingURL(String latLng, String types) throws MalformedURLException {
        return new URL(MAPS_GEOCODING +
                KEY + localGoogleMapsAPIPropeties.getProperty("google.api.key") + "&" +
                LAT_LNG + latLng + "&" +
                LANGUAGE +
                ((types.isEmpty()) ? "" : "&" + GEOCODING_RESULT_TYPES + types));
    }

    /**
     * Check if the time haa a temporal specification ("sera" or "mattina").
     *
     * @param token    the hour to check
     * @param sentence the sentence in wich to check
     * @param mod      the seeked modifier
     * @return true if any temporal specification of the given type was found, false otherwise
     */
    private boolean hasTemporalSpecification(IndexedWord token, SemanticGraph sentence, TemporalModifier mod) {
        boolean toReturn = false;
        for (IndexedWord child : sentence.getChildren(token)) {
            if (mod.texts.contains(child.originalText()))
                toReturn = true;
        }
        return toReturn;
    }

    /**
     * Round the current minutes value to the previuos hour quarter (so by floor).
     *
     * @param minutes the current minute value
     * @return the int rounded minute value
     */
    private int roundToPreviousQuarter(int minutes) {
        int i = 0;
        int m = minutes;
        while (m >= QUARTER) {
            m -= QUARTER;
            i++;
        }
        return i * QUARTER;
    }
}
