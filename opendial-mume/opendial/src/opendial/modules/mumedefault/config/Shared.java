package opendial.modules.mumedefault.config;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static opendial.modules.mumedefault.config.Config.EXAMPLE_LOG_FILE_PREFIX;

public class Shared {
    public static final String START = "start";
    private static final String CAPITAL_START = "Start";
    private static final String END = "end";
    private static final String CAPITAL_END = "End";
    private static final String TIME = "Time";
    private static final String DATE = "Date";
    private static final String SLOT = "Slot";
    private static final String ADDRESS = "Address";
    private static final String CITY = "City";
    public static final String START_TIME = START + TIME;
    public static final String END_TIME = END + TIME;
    public static final String END_TIME_KNOWN = END_TIME + "Known";
    public static final String START_DATE = START + DATE;
    public static final String END_DATE = END + DATE;
    public static final String START_SLOT = START + SLOT;
    public static final String END_SLOT = END + SLOT;
    public static final String START_ADDRESS = START + ADDRESS;
    public static final String END_ADDRESS = END + ADDRESS;
    public static final String START_CITY = START + CITY;
    public static final String END_CITY = END + CITY;
    public static final String VEHICLE_TYPE = "vehicleType";
    private static final String LAT = "Lat";
    private static final String LON = "Lon";
    public static final String START_LAT = START + LAT;
    public static final String START_LON = START + LON;
    public static final String END_LAT = END + LAT;
    public static final String END_LON = END + LON;
    private static final String SORTED_SLOTS = "SortedSlots";
    public static final String START_SORTED_SLOTS = START + SORTED_SLOTS;
    public static final String END_SORTED_SLOTS = END + SORTED_SLOTS;
    private static final String INFERRED = "inferred";
    public static final String INFERRED_START_CITY = INFERRED + CAPITAL_START + CITY;
    public static final String INFERRED_END_CITY = INFERRED + CAPITAL_END + CITY;
    private static final String UNSPECIFIED = "unspecified";
    public static final String UNSPECIFIED_START_SLOT = UNSPECIFIED + CAPITAL_START + SLOT;
    public static final String UNSPECIFIED_END_SLOT = UNSPECIFIED + CAPITAL_END + SLOT;

    public static final String NONE = "Missing";

    public static final String PAST_TIME_ERROR = "PastTimeError";
    // public static final String PAST_DATE_ERROR = "PastDateError";
    public static final String PAST_DATE_ERROR = PAST_TIME_ERROR;


    public static final double DISTANCE_THRESHOLD = 0.1;

    // Google Maps
    public static final String KEY = "key=";
    public static final String LANGUAGE = "language=it";
    /**
     * Google service URL to get the complete address from the (incomplete) information given by the user
     */
    public static final String MAPS_SEARCH = "https://maps.googleapis.com/maps/api/place/findplacefromtext/json?";
    public static final String INPUT = "input=";
    public static final String INPUT_TYPE = "inputtype=textquery";
    public static final String FIELDS = "fields=formatted_address,geometry/location";
    public static final String LOCATION_BIAS = "locationbias=point:";
    public static final String LOCATION_BIAS_RECTANGLE = "locationbias=rectangle:44.50,7.25|45.50,8.00";

    /**
     * Google service URL to get the current location of the user
     */
    public static final String MAPS_GEOLOCATION = "https://www.googleapis.com/geolocation/v1/geolocate?";

    /**
     * Google service URL to get the address of a coordinate point
     */
    public static final String MAPS_GEOCODING = "https://maps.googleapis.com/maps/api/geocode/json?";
    public static final String LAT_LNG = "latlng=";
    public static final String GEOCODING_RESULT_TYPES = "result_type=";
    // route = address; locality = city
    public static final String GEOCODING_LOCALITY = "route|locality";

    /**
     * Google service URL to get the address' parts out of an address
     */
    public static final String GOOGLE_API_ADDRESS = "address=";
    public static final String AREA_BOUNDING = "bounds=44.50,7.25|45.50,8.00";
    public static final String REGION = "region=it";

    /**
     * Google service response fields
     */
    public static final String LATITUDE = "lat";
    public static final String LONGITUDE = "lng";
    public static final String CANDIDATES = "candidates";
    public static final String COMPLETE_ADDRESS = "formatted_address";
    public static final String COMPONENTS = "address_components";
    public static final String LONG_NAME = "long_name";
    public static final String RESULTS = "results";
    public static final String COMPONENT_TYPES = "types";
    public static final String ROUTE = "route";
    public static final String LOCALITY = "locality";

    /*
    // Nominatim
    public static final String NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search.php?format=json&q=";
    public static final int NOMINATIM_TIMEOUT = 1000;
    */

    public static final Set<String> CASES = new HashSet<>(Arrays.asList("da", "fino", "per", "a", "di", "in", "del", "dal", "al", "dalle", "alle", "le"));

    public static final Set<String> STRONG_START_CITY_CASE = new HashSet<>(Arrays.asList("da"));   // "da Pinerolo"
    // DI -> "Mi serve una delle auto di Pinerolo" ==> "Voglio lasciare la macchina in via del Castello DI Nichelino"
    public static final Set<String> STRONG_END_CITY_CASE = new HashSet<>(Arrays.asList("fino", "per"));    // "fino a Nichelino", "Voglio aprtire subiyo per Rivoli."
    // A -> "da Nichelino a Pinerolo" => "Voglio andare da piazza Vittorio Veneto A Pinerolo a Nichelino"
    public static final Set<String> DEPENDANT_CITY_CASE = new HashSet<>(Arrays.asList("a", "di"));  // "voglio partire da BERNINI a Torino", "voglio partire da via Morante di Nichelino"
    public static final Set<String> WEAK_START_CITY_CASE = new HashSet<>(Arrays.asList("di"));  // "voglio una delle macchine di Nichelino"
    public static final Set<String> WEAK_END_CITY_CASE = new HashSet<>(Arrays.asList("a")); // "da Pinerolo a Nichelino"

    public static final Set<String> STRONG_START_ADDRESS_CASE = new HashSet<>(Arrays.asList("da"));  // "da piazza Vittorio Veneto"
    public static final Set<String> WEAK_START_ADDRESS_CASE = new HashSet<>(Arrays.asList("in"));  // "in piazza Avis"
    public static final Set<String> STRONG_END_ADDRESS_CASE = new HashSet<>(Arrays.asList("a")); // "a piazza Vittorio Veneto"
    public static final Set<String> DEPENDANT_ADDRESS_CASE = new HashSet<>(Arrays.asList("in"));   // "voglio partire da Pinerolo in piazza Avis", "Voglio la macchina di MONTANARI IN via Tripoli"

    public static final Set<String> STRONG_START_SLOT_CASE = new HashSet<>(Arrays.asList("da"));   // "da BERNINI"
    // DI -> "Mi serve una delle auto di SANTA GIULIA" ==> "Voglio lasciare la macchina a MATTEOTTI DI Grugliasco"
    public static final Set<String> STRONG_END_SLOT_CASE = new HashSet<>(Arrays.asList("fino"));    // "fino a OSPEDALE MARIA VITTORIA"
    // A -> "da BERNINI a TOFANE" => "Voglio andare da MARCONI A Vinovo a Nichelino"
    public static final Set<String> DEPENDANT_SLOT_CASE = new HashSet<>(Arrays.asList("a", "di"));  // "voglio partire da Torino a BERNINI", "voglio partire da MARCONI di Vinovo"
    public static final Set<String> WEAK_START_SLOT_CASE = new HashSet<>(Arrays.asList("di", "del"));  // "voglio una delle macchine del parcheggio BERNINI"
    public static final Set<String> WEAK_END_SLOT_CASE = new HashSet<>(Arrays.asList("a")); // "da BERNINI a BOLOGNA"

    public static final Set<String> STRONG_START_DATE_CASE = new HashSet<>(Arrays.asList("da", "dal"));  // "da domani", "dal 26"
    public static final Set<String> STRONG_END_DATE_CASE = new HashSet<>(Arrays.asList("fino", "a", "al")); // "fino a dopodomani", "al 27", "a domani"
    public static final Set<String> STRONG_SINGLE_DATE_CASE = new HashSet<>(Arrays.asList("per")); // "per domani", "per il 30" (30 -case-> per -det-> il)
    // DI -> "dalle 16 alle 19 di domani" => "Mi serve un'auto dalle _ DI oggi alle _ DI domani"
    // DEL -> "dalle 16 alle 19 del 20 febbraio" => "Mi serve un'auto dalle _ di oggi alle _ DEL 20 febbraio"
    public static final Set<String> DEPENDANT_DATE_CASE = new HashSet<>(Arrays.asList("del", "di"));   // "dalle _ alle _ del 20 febbraio", "dalle _ alle _ di domani"

    public static final Set<String> STRONG_START_TIME_CASE = new HashSet<>(Arrays.asList("dalle"));   // "dalle 14"
    public static final Set<String> STRONG_END_TIME_CASE = new HashSet<>(Arrays.asList("fino", "a"));    // "fino alle 17" (17 -case-> fino -mwe-> alle)
    // ALLE -> "dalle _ alle 18" => "Prendero' l'auto ALLE 8"
    public static final Set<String> DEPENDANT_TIME_CASE = new HashSet<>(Arrays.asList("alle", "le"));    // "fino a domani alle 17", "entro le 14 di dopodomani"

    public static final Set<String> STRONG_END_DURATION_CASE = new HashSet<>(Arrays.asList("per", "dopo"));   // "per due ore", "posarla dopo un'ora"
    public static final Set<String> DEPENDANT_DURATION_CASE = new HashSet<>(Arrays.asList("fra", "tra"));   // "fra due ore mi serve un'auto", "poserò l'auto tra due ore"

    public static final Set<String> ADDRESS_CLUE = new HashSet<>(Arrays.asList("corso", "piazza", "strada", "via", "viale", "vicolo", "ospedale", "palazzo"));

    public static final Set<String> START_VERBS = new HashSet<>(Arrays.asList("essere", "iniziare", "partire", "prendere", "prenotare", "servire", "usare", "volere",
            // FIXME
            "prendare"));
    public static final Set<String> LOCATION_END_VERBS = new HashSet<>(Arrays.asList("andare", "arrivare", "giungere", "lasciare", "posare", "raggiungere", "fermare"));
    public static final Set<String> TIME_END_VERBS = new HashSet<>(Arrays.asList("arrivare", "lasciare", "posare", "fermare"));

    public static final LinkedList<String> PUNCT = new LinkedList<>(Arrays.asList(".", " ", ",", ":", ";", "?", "!"));

    /*
    /* Sample citis /
    public static final Set<String> CITIES = new HashSet<>(Arrays.asList("Pinerolo", "Nichelino"));
    public static final Map<String, List<String>> CITIES_ADDRESSES = new HashMap<>();

    /*
     * Example of addresses by city:
     *  - the first is the close economy slot
     *  - the second is the close Transport slot
     *  - the third is the open (economy) slot
     /
    static {
        CITIES_ADDRESSES.put("Pinerolo", new ArrayList<>(Arrays.asList("piazza Vittorio Veneto", "piazza Avis", "Pinerolo Olimpica")));
        CITIES_ADDRESSES.put("Nichelino", new ArrayList<>(Arrays.asList("via del Castello", "via Elsa Morante", "viale Segre")));
    }
    */

    public static final Set<String> NOW_WORDS = new HashSet<>(Arrays.asList("adesso", "subito", "presto", "immediatamente", "immantinente", "stante"    // "seduta stante"
            // ,"ora"    FIXME 'ora' is interpreted like a duration
    ));

    public static final Set<String> HERE_WORDS = new HashSet<>(Arrays.asList("qui", "qua", "sono", "vicino"));

    static {
        HERE_WORDS.addAll(NOW_WORDS);
    }

    /*
    public static Set<String> hereAnswers = new HashSet<>();

    static {
        for (String w : HERE_WORDS)
            for (int c : Arrays.asList(0, 1, 2)) {
                String word;
                switch (c) {
                    case 0:
                        word = w;
                        break;
                    case 1:
                        word = w.substring(0, 1).toUpperCase() + w.substring(1);
                        break;
                    default:
                        word = w.toUpperCase();
                }
                for (String p : PUNCT)
                    hereAnswers.add(word + p);
            }
    }
    */

    private static final Set<String> POSITIVE_WORDS = new HashSet<>(Arrays.asList("sì", "si", "certo", "certissimo", "ok", "giusto", "giustissimo", "vero", "verissimo", "corretto"));
    public static Set<String> positiveAnswers = new HashSet<>();

    static {
        for (String w : POSITIVE_WORDS)
            for (int c : Arrays.asList(0, 1, 2)) {
                String word;
                switch (c) {
                    case 0:
                        word = w;
                        break;
                    case 1:
                        word = w.substring(0, 1).toUpperCase() + w.substring(1);
                        break;
                    default:
                        word = w.toUpperCase();
                }
                positiveAnswers.add(word);
                for (String p : PUNCT)
                    positiveAnswers.add(word + p);
            }
    }

    private static final Set<String> NEGATIVE_WORDS = new HashSet<>(Arrays.asList("no", "sbagliato", "sbagliatissimo", "falso", "scorretto" //, "non lo so"
    ));
    public static Set<String> negativeAnswers = new HashSet<>();

    static {
        for (String w : NEGATIVE_WORDS)
            for (int c : Arrays.asList(0, 1, 2)) {
                String word;
                switch (c) {
                    case 0:
                        word = w;
                        break;
                    case 1:
                        word = w.substring(0, 1).toUpperCase() + w.substring(1);
                        break;
                    default:
                        word = w.toUpperCase();
                }
                negativeAnswers.add(word);
                for (String p : PUNCT)
                    negativeAnswers.add(word + p);
            }
    }

    private static final Set<String> NEGATIVE_COMPOSITE_ANSWERS = new HashSet<>(Arrays.asList("non lo so", "non so", "non saprei", "non importa", "non mi importa"));
    public static List<String> negativeCompositeAnswers = new ArrayList<>();

    static {
        for (String w : NEGATIVE_COMPOSITE_ANSWERS)
            for (int c : Arrays.asList(0, 1, 2)) {
                String word;
                switch (c) {
                    case 0:
                        word = w;
                        break;
                    case 1:
                        word = w.substring(0, 1).toUpperCase() + w.substring(1);
                        break;
                    default:
                        word = w.toUpperCase();
                }
                negativeCompositeAnswers.add(word);
                for (String p : PUNCT)
                    negativeCompositeAnswers.add(word + p);
            }
    }

    public static final Set<String> FROM_NOW_CLUE = new HashSet<>(Arrays.asList("tra", "fra"));

    /* Sample vehicle type */
    public static final Map<String, List<String>> VEHICLE_TYPES = new HashMap<>();

    /*
     * Example of vehicle type:
     *  - economy (economica)
     *  - luxury (di lusso)
     *  - transport (da trasporto)
     */
    static {
        // VEHICLE_TYPES.put("economy", new ArrayList<>(Arrays.asList("economy", "economica", "economico")));   // DEFAULT
        VEHICLE_TYPES.put("luxury", new ArrayList<>(Arrays.asList("luxury", "di lusso", "lussuosa")));
        VEHICLE_TYPES.put("transport", new ArrayList<>(Arrays.asList("transport", "da trasporto", "il trasporto", "van", "furgone", "furgoncino", "trasloco")));
    }

    // loggers
    public static final Logger log = Logger.getLogger("MuMeLog");

    public static final Logger exampleLog = Logger.getAnonymousLogger();

    static {
        try {
            FileHandler loggerOutFile = new FileHandler(EXAMPLE_LOG_FILE_PREFIX + ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT).replaceAll(":", "_") + ".txt");
            loggerOutFile.setFormatter(new SimpleFormatter() {
                private static final String format = "%s%n";

                @Override
                public synchronized String format(LogRecord lr) {
                    return String.format(format,
                            lr.getMessage()
                    );
                }
            });
            exampleLog.setUseParentHandlers(false);
            exampleLog.addHandler(loggerOutFile);
        } catch (IOException ioex) {
            log.severe("Not able to create example logger.");
            ioex.printStackTrace();
        }

    }
}
