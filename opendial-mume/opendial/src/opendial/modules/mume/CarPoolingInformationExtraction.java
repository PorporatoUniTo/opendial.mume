package opendial.modules.mume;

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
import eu.fbk.dh.tint.runner.TintRunner;
import opendial.DialogueState;
import opendial.DialogueSystem;
import opendial.modules.Module;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
                (state.queryProb("a_m").getBest().toString().equals("RETRIEVE_INFORMATION") ||
                        state.queryProb("a_m").getBest().toString().equals("ASK_START_DATE"))) {
            String userUtterance = state.queryProb("u_u").getBest().toString();

            // Informations
            String startDate = NONE;
            String endDate = NONE;
            String startTime = NONE;
            String endTime = NONE;
            String startSlot = NONE;
            String endSlot = NONE;
            //String vehicleType = NONE;
            double startLat = Double.MIN_VALUE;
            double startLon = Double.MIN_VALUE;
            double endLat = Double.MIN_VALUE;
            double endLon = Double.MIN_VALUE;

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
            InputStream stream = new ByteArrayInputStream(userUtterance.getBytes(StandardCharsets.UTF_8));
//            OutputStream jsonOut = null;
//            try {
//                jsonOut = new FileOutputStream(JSON_OUT);
//            } catch (FileNotFoundException exception) {
//                exception.printStackTrace();
//            }

            /*
             * LogOutputStream
             * https://web.archive.org/web/20130527080241/http://www.java2s.com/Open-Source/Java/Testing/jacareto/jacareto/toolkit/log4j/LogOutputStream.java.htm
             */

            Annotation annotation;
            try {
                annotation = pipeline.run(stream, System.out, TintRunner.OutputFormat.JSON);

                /* See previous comment */
                // log.info("TIMEX3: " + String.valueOf(annotation.get(HeidelTimeAnnotations.TimexesAnnotation.class).size()));

                //BufferedReader bufferedReader = new BufferedReader(new FileReader(JSON_OUT));
                //Gson gsonOut = new Gson();
                //LinkedTreeMap json = (LinkedTreeMap) gsonOut.fromJson(bufferedReader, Object.class);

//                // TESTS
//                //log.info(json.toString());
//                //((ArrayList) json.get("timexes")).forEach(t -> log.info(((LinkedTreeMap) t).get("timexType").toString()));
//                log.info("Results:");
//                log.info("Text: " + annotation.get(CoreAnnotations.TextAnnotation.class));
//                log.info("Token's POS-tags:");
//                List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
//                tokens.forEach(token -> log.info(token.get(CoreAnnotations.PartOfSpeechAnnotation.class)));
//                log.info("Sentences:");
//                List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
//                sentences.forEach(sentence -> log.info(sentence.toShorterString()));
//                log.info("Dependencies:");
//                // There is only one sentence: property 'ita_toksent.ssplitOnlyOnNewLine=true' in Tint's default-config.properties
//                SemanticGraph dependencies = sentences.get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
//                dependencies.prettyPrint();
//                log.info("Root:");
//                dependencies.getRoots().forEach(root -> log.info(root.toString()));
//                log.info("Root's Children:");
//                dependencies.childPairs(dependencies.getFirstRoot()).forEach((p) -> {
//                    log.info(p.first.getShortName() +
//                            ((p.first.getSpecific() != null) ? "#" + p.first.getSpecific() : ""));
//                    log.info(String.valueOf(p.second.beginPosition()));
//                });
//                log.info("Root Split (just befor the root conjunction):");
//                List<IndexedWord> conjiunctionIndices = new ArrayList<>();
//                dependencies.childPairs(dependencies.getFirstRoot()).forEach((p) -> {
//                    if ((p.first.getShortName() + ":" + p.first.getSpecific()).equals("conj:e"))
//                        conjiunctionIndices.add(p.second);
//                });
//                conjiunctionIndices.forEach(c -> log.info(String.valueOf(c.beginPosition() - 1)));
//                List<CoreLabel> dateTokens = new ArrayList<>();
//                List<CoreLabel> timeTokens = new ArrayList<>();
//                List<CoreLabel> locTokens = new ArrayList<>();
//                tokens.forEach(t -> {
//                    switch (t.get(CoreAnnotations.NamedEntityTagAnnotation.class)) {
//                        case "DATE":
//                            dateTokens.add(t);
//                            break;
//                        case "TIME":
//                            timeTokens.add(t);
//                            break;
//                        case "LOC":
//                            locTokens.add(t);
//                            break;
//                        default:
//                    }
//                });
//                log.info("Dates:");
//                dateTokens.forEach(t -> log.info(t.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class)));
//                log.info("Times:");
//                timeTokens.forEach(t -> log.info(t.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class)));
//                log.info("Locations:");
//                locTokens.forEach(t -> log.info(t.get(CoreAnnotations.TextAnnotation.class)));


                List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
                List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
                // There is only one sentence: property 'ita_toksent.ssplitOnlyOnNewLine=true' in Tint's default-config.properties
                SemanticGraph dependencies = sentences.get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
                List<IndexedWord> conjiunctionIndices = new ArrayList<>();
                dependencies.childPairs(dependencies.getFirstRoot()).forEach((p) -> {
                    if ((p.first.getShortName() + ":" + p.first.getSpecific()).equals("conj:e"))
                        conjiunctionIndices.add(p.second);
                });
                int split = conjiunctionIndices.get(0).beginPosition() - 1;
                boolean inNER = false;
                int currentNERStart = -1;
                String currentValue = "";
                String currentNERType = "O";
                String nerType = "";
                for (CoreLabel token : tokens) {
                    nerType = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                    if (inNER && !nerType.equals(currentNERType)) {
                        if (currentNERStart < split) {
                            switch (currentNERType) {
                                case "DATE":
                                    startDate = currentValue;
                                    break;
                                case "TIME":
                                    startTime = currentValue.split("T")[1];
                                    break;
                                case "LOC":
                                    startSlot = currentValue;
                                    break;
                                default:
                            }
                        } else {
                            switch (currentNERType) {
                                case "DATE":
                                    endDate = currentValue;
                                    break;
                                case "TIME":
                                    endTime = currentValue.split("T")[1];
                                    break;
                                case "LOC":
                                    endSlot = currentValue;
                                    break;
                                default:
                            }
                        }

                        // Leaving the found NER: reset parameters
                        inNER = false;
                        currentNERStart = -1;
                        currentValue = "";
                        currentNERType = "O";
                    }
                    if (!nerType.equals("O") && !inNER) {
                        // Another NER encountered
                        inNER = true;
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
                    if (currentNERStart < split) {
                        switch (currentNERType) {
                            case "DATE":
                                startDate = currentValue;
                                break;
                            case "TIME":
                                startTime = currentValue.split("T")[1];
                                break;
                            case "LOC":
                                startSlot = currentValue;
                                break;
                            default:
                        }
                    } else {
                        switch (currentNERType) {
                            case "DATE":
                                endDate = currentValue;
                                break;
                            case "TIME":
                                endTime = currentValue.split("T")[1];
                                break;
                            case "LOC":
                                endSlot = currentValue;
                                break;
                            default:
                        }
                    }
                }
                if (!startSlot.equals(NONE)) {
                    try {
                        URL startAddress = new URL(NOMINATIM_SEARCH_URL + URLEncoder.encode(startSlot, "UTF-8"));
                        log.info(startAddress.toString());
                        URLConnection geoConnection = startAddress.openConnection();
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(geoConnection.getInputStream(), StandardCharsets.UTF_8));
                        String inputLine;
                        StringBuilder a = new StringBuilder();
                        while ((inputLine = in.readLine()) != null) {
                            a.append(inputLine);
                        }
                        in.close();

                        JsonParser parser = new JsonParser();
                        JsonArray locations = (JsonArray) parser.parse(a.toString());
                        if (locations.size() > 0) {
                            startLat = Double.parseDouble(locations.get(0).getAsJsonObject().get("lat").getAsString());
                            startLon = Double.parseDouble(locations.get(0).getAsJsonObject().get("lon").getAsString());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                try {
                    Thread.sleep(NOMINATIM_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (!endSlot.equals(NONE)) {
                    try {
                        URL endAddress = new URL(NOMINATIM_SEARCH_URL + URLEncoder.encode(endSlot, "UTF-8"));
                        log.info(endAddress.toString());
                        URLConnection geoConnection = endAddress.openConnection();
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(geoConnection.getInputStream(), StandardCharsets.UTF_8));
                        String inputLine;
                        StringBuilder a = new StringBuilder();
                        while ((inputLine = in.readLine()) != null) {
                            a.append(inputLine);
                        }
                        in.close();

                        JsonParser parser = new JsonParser();
                        JsonArray locations = (JsonArray) parser.parse(a.toString());
                        if (locations.size() > 0) {
                            endLat = Double.parseDouble(locations.get(0).getAsJsonObject().get("lat").getAsString());
                            endLon = Double.parseDouble(locations.get(0).getAsJsonObject().get("lon").getAsString());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException exception) {
                exception.printStackTrace();
            }

            log.info("startDate = " + startDate);
            log.info("startTime = " + startTime);
            log.info("startSlot = " + startSlot);
            log.info("\tstartLat = " + startLat);
            log.info("\tstartLon = " + startLon);
            log.info("endDate = " + endDate);
            log.info("endTime = " + endTime);
            log.info("endSlot = " + endSlot);
            log.info("\tendLat = " + endLat);
            log.info("\tendLon = " + endLon);

            system.addContent("startDate", startDate);
            system.addContent("startTime", startTime);
            system.addContent("startSlot", startSlot);
            system.addContent("endDate", endDate);
            system.addContent("endTime", endTime);
            system.addContent("endSlot", endSlot);
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
