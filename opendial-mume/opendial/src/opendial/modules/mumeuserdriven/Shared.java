package opendial.modules.mumeuserdriven;

import java.util.*;
import java.util.logging.Logger;

public class Shared {
    /*
    public enum StateVariables {
        START_SLOT("startSlot"),
        START_SLOT_UNSPECIFIED("startSlotUnspecified"),
        START_CITY("startCity"),
        START_LAT("startLat"),
        START_LON("startLon"),
        START_DATE("startDate"),
        START_TIME("startTime"),

        END_SLOT("endSlot"),
        END_CITY("endCity"),
        END_LAT("endLat"),
        END_LON("endLon"),
        END_DATE("endDate"),
        END_TIME("endTime"),
        END_TIME_KNOWN("endTimeKnown"),

        VEHICLE_TYPE("vehicleType");

        private String name;

        StateVariables(String var) {
            name = var;
        }

        @Override
        public String toString() {
            return name;
        }
    }
    */


    public static final String NONE = "Missing";

    public static final int QUARTER = 15;
    public static final int HOUR = 60;

    // public static final double DISTANCE_THRESHOLD = 0.1;
    public static final double DISTANCE_THRESHOLD = 1;

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
    public static final String GEOCODING_LOCALITY = "administrative_area_level_3|locality";

    /**
     * Google service URL to get the address' parts out of an address
     */
    public static final String ADDRESS = "address=";
    public static final String AREA_BOUNDING = "bounds=44.50,7.25|45.50,8.00";
    public static final String REGION = "region=it";

    /**
     * Google service response fields
     */
    public static final String LATITUDE = "lat";
    public static final String LONGITUDE = "lng";
    public static final String COMPLETE_ADDRESS = "formatted_address";
    public static final String COMPONENTS = "address_components";
    public static final String LONG_ADDRESS = "long_name";
    public static final String RESULTS = "results";

    /*

    // Nominatim
    public static final String NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search.php?format=json&q=";
    public static final int NOMINATIM_TIMEOUT = 1000;

    public static final Set<String> STRONG_START_CITY_CASE = new HashSet<>(Arrays.asList("da"));   // "da Pinerolo"
    // DI -> "Mi serve una delle auto di Pinerolo" ==> "Voglio lasciare la macchina in via del Castello DI Nichelino"
    public static final Set<String> STRONG_END_CITY_CASE = new HashSet<>(Arrays.asList("fino"));    // "fino a Nichelino"
    // A -> "da Nichelino a Pinerolo" => "Voglio andare da piazza Vittorio Veneto A Pinerolo a Nichelino"
    public static final Set<String> DEPENDANT_CITY_CASE = new HashSet<>(Arrays.asList("a", "di"));  // "voglio partire da piazza Avis a Pinerolo", "voglio partire da via Morante di Nichelino"
    public static final Set<String> WEAK_END_CITY_CASE = new HashSet<>(Arrays.asList("a")); // "da Pinerolo a Nichelino"

    public static final Set<String> STRONG_START_ADDRESS_CASE = new HashSet<>(Arrays.asList("da"));  // "da piazza Vittorio Veneto"
    public static final Set<String> STRONG_END_ADDRESS_CASE = new HashSet<>(Arrays.asList("a")); // "a piazza Vittorio Veneto"
    public static final Set<String> DEPENDANT_ADDRESS_CASE = new HashSet<>(Arrays.asList("in"));   // "voglio partire da Pinerolo in piazza Avis", "Voglio arrivare A nichino in viale Segre"

    public static final Set<String> STRONG_START_DATE_CASE = new HashSet<>(Arrays.asList("da", "dal"));  // "da domani", "dal 26"
    public static final Set<String> STRONG_END_DATE_CASE = new HashSet<>(Arrays.asList("fino", "a", "al")); // "fino a dopodomani", "al 27", "a domani"
    public static final Set<String> STRONG_SINGLE_DATE_CASE = new HashSet<>(Arrays.asList("per")); // "per domani", "per il 30" (30 -case-> per -det-> il)
    // DI -> "dalle 16 alle 19 di domani" => "Mi serve un'auto dalle _ DI oggi alle _ DI domani"
    // DEL -> "dalle 16 alle 19 del 20 febbraio" => "Mi serve un'auto dalle _ di oggi alle _ DEL 20 febbraio"
    public static final Set<String> DEPENDANT_DATE_CASE = new HashSet<>(Arrays.asList("del", "di"));   // "dalle _ alle _ del 20 febbraio", "dalle _ alle _ di domani"

    public static final Set<String> STRONG_START_TIME_CASE = new HashSet<>(Arrays.asList("dalle"));   // "dalle 14"
    public static final Set<String> STRONG_END_TIME_CASE = new HashSet<>(Arrays.asList("fino"));    // "fino alle 17" (17 -case-> fino -mwe-> alle)
    // ALLE -> "dalle _ alle 18" => "Prendero' l'auto ALLE 8"
    public static final Set<String> DEPENDANT_TIME_CASE = new HashSet<>(Arrays.asList("alle", "le"));    // "fino a domani alle 17", "entro le 14 di dopodomani"

    public static final Set<String> START_VERBS = new HashSet<>(Arrays.asList("partire", "prendere", "prenotare", "usare"));
    public static final Set<String> END_VERBS = new HashSet<>(Arrays.asList("arrivare", "lasciare", "posare"));
    */

    public static final Set<String> ADDRESS_CLUE = new HashSet<>(Arrays.asList("corso", "piazza", "strada", "via", "viale", "vicolo"));


    public static final LinkedList<String> PUNCT = new LinkedList<>(Arrays.asList("", ".", " ", ",", ":", ";", "?", "!"));
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
                for (String p : PUNCT)
                    positiveAnswers.add(word + p);
            }
    }

    private static final Set<String> NEGATIVE_WORDS = new HashSet<>(Arrays.asList("no", "sbagliato", "sbagliatissimo", "falso", "scorretto", "non lo so"));
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
                for (String p : PUNCT)
                    negativeAnswers.add(word + p);
            }
    }


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

    private static final Set<String> NOW_WORDS = new HashSet<>(Arrays.asList("adesso", "ora", "subito"));
    public static Set<String> nowAnswers = new HashSet<>();

    static {
        for (String w : NOW_WORDS)
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
                    nowAnswers.add(word + p);
            }
    }

    private static final Set<String> HERE_WORDS = new HashSet<>(Arrays.asList("qui", "qua", "ora", "adesso"));
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

    /* Sample vehicle type */
    public static final Map<String, List<String>> VEHICLE_TYPES = new HashMap<>();

    /*
     * Example of vehicle type:
     *  - economy (economica)
     *  - luxury (di lusso)
     *  - transport (da trasporto, van)
     */
    static {
        // VEHICLE_TYPES.put("economy", new ArrayList<>(Arrays.asList("economy", "economica")));
        VEHICLE_TYPES.put("luxury", new ArrayList<>(Arrays.asList("luxury", "di lusso")));
        VEHICLE_TYPES.put("transport", new ArrayList<>(Arrays.asList("transport", "da trasporto", "il trasporto", "van", "trasloco")));
    }

    private static final Set<String> CANCEL_WORDS = new HashSet<>(Arrays.asList("stop", "alt", "cancella", "annulla"));
    public static Set<String> cancelAnswers = new HashSet<>();

    static {
        for (String w : CANCEL_WORDS)
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
                    cancelAnswers.add(word + p);
            }
    }

    // logger
    public static final Logger log = Logger.getLogger("MuMeLog");
}
