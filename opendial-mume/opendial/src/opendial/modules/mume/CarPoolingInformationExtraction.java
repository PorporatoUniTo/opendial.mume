package opendial.modules.mume;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
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
import java.util.*;
import java.util.logging.Logger;

import static opendial.modules.mume.config.Config.LOG4J_CONFIG;
import static opendial.modules.mume.config.Config.TINT_CONFIG;

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
public class CarPoolingInformationExtraction implements Module {
    //private static final String JSON_OUT = "." File.separator + "out.json";
    private static final String NONE = "None";
    private static final String NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search.php?format=json&q=";
    private static final int NOMINATIM_TIMEOUT = 1000;

    private static final Set<String> START_VERBS = new HashSet<>(Collections.singletonList("prendere"));
    private static final Set<String> END_VERBS = new HashSet<>(Collections.singletonList("posare"));
    private static final Set<String> START_CASES = new HashSet<>(Arrays.asList("da", "dalle", "dal"));

    static {
        System.setProperty("log4j.configurationFile", LOG4J_CONFIG);
    }

    // logger
    public final static Logger log = Logger.getLogger("OpenDial");

    // the dialogue system
    DialogueSystem system;

    // whether the module is paused or active
    boolean paused = true;

    /*
    // HeidelTime
    HeidelTimeStandalone haidelTime;

    // Numinatim


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
                .apiKey("")
                .maxRetries(3)
                .retryTimeout(3, TimeUnit.SECONDS)
                .build();
        */

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
        if (updatedVars.contains("u_u") &&
                state.hasChanceNode("u_u") &&
                state.hasChanceNode("state") &&
                state.queryProb("state").getBest().toString().equals("INFORMATION_EXTRACTION") &&
                state.hasChanceNode("a_m") &&
                state.queryProb("a_m").getBest().toString().equals("RETRIEVE_INFORMATION")) {
            String userUtterance = state.queryProb("u_u").getBest().toString();

            // Informations
            Map<String, String> information = new HashMap<>();
            Map<String, String> backup = new HashMap<>();
            String[] infoSlots = {
                    "startDate",
                    "endDate",
                    "startTime",
                    "endTime",
                    "startSlot",
                    "endSlot",
                    "vehicleType",
                    "startLat",
                    "startLon",
                    "endLat",
                    "endLon"
            };

            Arrays.stream(infoSlots).forEach(slot -> {
                if (state.hasChanceNode(slot))
                    information.put(slot, state.queryProb(slot).getBest().toString());
                else
                    information.put(slot, "None");
                backup.put(slot, information.get(slot));
            });

            // 'Voglio prendere una macchina il 26 ottobre alle 14 da piazza Castello e voglio posarla alle sette del 29 ottobre a Volvera'
            log.info("User said: '" + userUtterance + "'");

            /*
            String timeAnnotatedUserUtterance = "error";
            GeocodingResult[] results = new GeocodingResult[0];

            try {
                timeAnnotatedUserUtterance = haidelTime.process(userUtterance, Date
                        .from(LocalDateTime.ofInstant(new Date().toInstant(), ZoneId.systemDefault())
                                .atZone(ZoneId.systemDefault()).toInstant()));
            } catch (DocumentCreationTimeMissingException exception) {
                exception.printStackTrace();
            }

            log.info(timeAnnotatedUserUtterance);

            /* JUST FOR TESTING: assmption: the only date given is the startDate /
            String testDate = timeAnnotatedUserUtterance.substring(1, timeAnnotatedUserUtterance.length() - 1);
            Document taggedUtterance = Jsoup.parse(timeAnnotatedUserUtterance, "", Parser.xmlParser());
            Elements utteranceTags = taggedUtterance.getElementsByTag("TIMEX3");
            for (Element elem : utteranceTags)
                log.info(elem.toString());

            system.addContent("startDate", utteranceTags.get(0).text());

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
             */
            // InputStream stream = new ByteArrayInputStream(userUtterance.getBytes(StandardCharsets.UTF_8));

            /*
            OutputStream jsonOut = null;
            try {
                jsonOut = new FileOutputStream(JSON_OUT);
            } catch (FileNotFoundException exception) {
                exception.printStackTrace();
            }
            */

            /*
             * LogOutputStream
             * https://web.archive.org/web/20130527080241/http://www.java2s.com/Open-Source/Java/Testing/jacareto/jacareto/toolkit/log4j/LogOutputStream.java.htm
             */

            Annotation annotation;
            // try {
            // annotation = pipeline.run(stream, jsonOut, TintRunner.OutputFormat.JSON);
            // annotation = pipeline.run(stream, System.out, TintRunner.OutputFormat.JSON);
            annotation = pipeline.runRaw(userUtterance);

            String machinePrevState = "";
            if (state.hasChanceNode("a_m-prev"))
                machinePrevState = state.queryProb("a_m-prev").getBest().toString();

            /* See previous comment */
            // log.info("TIMEX3: " + String.valueOf(annotation.get(HeidelTimeAnnotations.TimexesAnnotation.class).size()));

            //BufferedReader bufferedReader = new BufferedReader(new FileReader(JSON_OUT));
            //Gson gsonOut = new Gson();
            //LinkedTreeMap json = (LinkedTreeMap) gsonOut.fromJson(bufferedReader, Object.class);

            /*
            // TESTS
            //log.info(json.toString());
            //((ArrayList) json.get("timexes")).forEach(t -> log.info(((LinkedTreeMap) t).get("timexType").toString()));
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
            log.info("Roots' Children:");
            dependencies.childPairs(dependencies.getFirstRoot()).forEach((p) -> {
                log.info(p.first.getShortName() +
                        ((p.first.getSpecific() != null) ? "#" + p.first.getSpecific() : ""));
                log.info(String.valueOf(p.second.beginPosition()));
            });
            log.info("Root Split (just befor the root conjunction):");
            List<IndexedWord> conjiunctionIndices = new ArrayList<>();
            dependencies.childPairs(dependencies.getFirstRoot()).forEach((p) -> {
                if ((p.first.getShortName() + ":" + p.first.getSpecific()).equals("conj:e"))
                    conjiunctionIndices.add(p.second);
            });
            conjiunctionIndices.forEach(c -> log.info(String.valueOf(c.beginPosition() - 1)));
            List<CoreLabel> dateTokens = new ArrayList<>();
            List<CoreLabel> timeTokens = new ArrayList<>();
            List<CoreLabel> locTokens = new ArrayList<>();
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
                    default:
                }
            });
            log.info("Dates:");
            dateTokens.forEach(t -> log.info(t.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class)));
            log.info("Times:");
            timeTokens.forEach(t -> log.info(t.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class)));
            log.info("Locations:");
            locTokens.forEach(t -> log.info(t.get(CoreAnnotations.TextAnnotation.class)));
            */

            List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
            // There is only one sentence: property 'ita_toksent.ssplitOnlyOnNewLine=true' in Tint's default-config.properties
            SemanticGraph dependencies = sentences.get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);

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
                            break;
                        case "TIME":
                            currentValue = token.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
                            break;
                        case "LOC":
                            currentValue = token.get(CoreAnnotations.TextAnnotation.class);
                            break;
                    }
                } else if (inNER && nerType.equals("LOC"))
                    currentValue = currentValue + " " + token.get(CoreAnnotations.TextAnnotation.class);
            }
            // If the user utterance terminate with a NER, collect this information
            nerType = "O";
            if (inNER && !nerType.equals(currentNERType)) {
                updateInfo(annotation, dependencies, currentNER, currentNERStart, currentNERType, currentValue, split, information, machinePrevState);
            }
            JsonParser parser = new JsonParser();
            boolean waitBetweenRequests = false;
            if (!information.getOrDefault("startSlot", NONE).equals(NONE)) {
                String nominatimResponse = getNominatimJSON(information.get("startSlot"));

                JsonArray locations = (JsonArray) parser.parse(nominatimResponse);
                if (locations.size() > 0) {
                    information.put("startLat", locations.get(0).getAsJsonObject().get("lat").getAsString());
                    information.put("startLon", locations.get(0).getAsJsonObject().get("lon").getAsString());
                }
                waitBetweenRequests = true;
            }
            if (!information.getOrDefault("endSlot", NONE).equals(NONE)) {
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
            // } catch (IOException exception) {
            //     exception.printStackTrace();
            // }

            boolean hasBeenUpdated = false;
            for (Map.Entry<String, String> info : information.entrySet())
                if (information.get(info.getKey()).equals(backup.get(info.getKey())))
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
     * Execute the geocoding for the found location via -Nominatim.
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

    /**
     * @param annotation
     * @param dependencies
     * @param currentNERToken
     * @param currentNERStart
     * @param currentNERType
     * @param currentValue
     * @param split
     * @param information
     */
    private void updateInfo(Annotation annotation,
                            SemanticGraph dependencies,
                            CoreLabel currentNERToken,
                            int currentNERStart,
                            String currentNERType,
                            String currentValue,
                            int split,
                            Map<String, String> information,
                            String machinePrevState) {
        IndexedWord tokenIndexedWord = dependencies.getNodeByIndex(currentNERToken.index());

        String verbGovernorLemma = "";
        List<IndexedWord> currentParents = dependencies.getPathToRoot(tokenIndexedWord);
        Iterator<IndexedWord> it = currentParents.iterator();
        while (verbGovernorLemma.isEmpty() && it.hasNext()) {
            CoreLabel currentParentToken = annotation.get(CoreAnnotations.TokensAnnotation.class).get(it.next().index() - 1);
            if (currentParentToken.get(CoreAnnotations.PartOfSpeechAnnotation.class)
                    .startsWith("V"))
                verbGovernorLemma = currentParentToken.lemma();
        }

        String tokenCase = "";
        for (Pair<GrammaticalRelation, IndexedWord> p : dependencies.childPairs(tokenIndexedWord)) {
            if (p.first.getShortName().equals("case"))
                tokenCase = p.second.lemma();
        }

        if (split >= 0 && currentNERStart < split ||
                !verbGovernorLemma.isEmpty() && START_VERBS.contains(verbGovernorLemma) ||
                !tokenCase.isEmpty() && START_CASES.contains(tokenCase)) {
            switch (currentNERType) {
                case "DATE":
                    information.put("startDate", currentValue);
                    break;
                case "TIME":
                    information.put("startTime", currentValue.split("T")[1]);
                    break;
                case "LOC":
                    information.put("startSlot", currentValue);
                    break;
                default:
            }
        } else if (split >= 0 && currentNERStart >= split ||
                !verbGovernorLemma.isEmpty() && END_VERBS.contains(verbGovernorLemma)) {
            switch (currentNERType) {
                case "DATE":
                    information.put("endDate", currentValue);
                    break;
                case "TIME":
                    information.put("endTime", currentValue.split("T")[1]);
                    break;
                case "LOC":
                    information.put("endSlot", currentValue);
                    break;
                default:
            }
        } else if (machinePrevState.startsWith("ASK_INFO")) {
            switch (machinePrevState) {
                case "ASK_INFO_START_SLOT":
                    if (currentNERType.equals("LOC"))
                        information.put("startSlot", currentValue);
                    break;
                case "ASK_INFO_END_SLOT":
                    if (currentNERType.equals("LOC"))
                        information.put("endSlot", currentValue);
                    break;
                case "ASK_INFO_START_DATE":
                    if (currentNERType.equals("DATE"))
                        information.put("startDate", currentValue);
                    break;
                case "ASK_INFO_END_DATE":
                    if (currentNERType.equals("DATE"))
                        information.put("endDate", currentValue);
                    break;
                case "ASK_INFO_START_TIME":
                    if (currentNERType.equals("TIME"))
                        information.put("startTime", currentValue.split("T")[1]);
                    break;
                case "ASK_INFO_END_TIME":
                    if (currentNERType.equals("TIME"))
                        information.put("endTime", currentValue.split("T")[1]);
                    break;
                default:
            }
        }
    }
}
