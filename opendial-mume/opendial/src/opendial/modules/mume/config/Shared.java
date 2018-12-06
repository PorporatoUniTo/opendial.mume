package opendial.modules.mume.config;

import java.util.*;
import java.util.logging.Logger;

public class Shared {
    public static final String NONE = "Missing";

    // Nominatim
    public static final String NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search.php?format=json&q=";
    public static final int NOMINATIM_TIMEOUT = 1000;

    public static final Set<String> STRONG_START_CITY_CASE = new HashSet<>(Arrays.asList("da"));   // "da Pinerolo"
    // DI -> "Mi serve una delle auto di Pinerolo" ==> "Voglio lasciare la macchina in via del Castello DI Nichelino"
    public static final Set<String> STRONG_END_CITY_CASE = new HashSet<>(Arrays.asList("fino"));    // "fino a Nichelino"
    // A -> "da Nichelino a Pinerolo" => "Voglio andare da piazza Vittorio Veneto A Pinerolo a Nichelino"
    public static final Set<String> DEPENDANT_CITY_CASE = new HashSet<>(Arrays.asList("a", "di"));  // "voglio partire da piazza Avis a Pinerolo", "voglio partire da via Morante di Nichelino"
    public static final Set<String> WEAK_END_CITY_CASE = new HashSet<>(Arrays.asList("a")); // "da Pinerolo a Nichelino"

    public static final Set<String> STRONG_START_SLOT_CASE = new HashSet<>(Arrays.asList("da"));  // "da piazza Vittorio Veneto"
    public static final Set<String> STRONG_END_SLOT_CASE = new HashSet<>(Arrays.asList("a")); // "a piazza Vittorio Veneto"
    public static final Set<String> DEPENDANT_SLOT_CASE = new HashSet<>(Arrays.asList("in"));   // "voglio partire da Pinerolo in piazza Avis", "Voglio arrivare A nichino in viale Segre"

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

    /* Sample citis */
    public static final Set<String> CITIES = new HashSet<>(Arrays.asList("Pinerolo", "Nichelino"));
    public static final Map<String, List<String>> CITIES_ADDRESSES = new HashMap<>();

    /*
     * Example of addresses by city:
     *  - the first is the close economy slot
     *  - the second is the close Transport slot
     *  - the third is the open (economy) slot
     */
    static {
        CITIES_ADDRESSES.put("Pinerolo", new ArrayList<>(Arrays.asList("piazza Vittorio Veneto", "piazza Avis", "Pinerolo Olimpica")));
        CITIES_ADDRESSES.put("Nichelino", new ArrayList<>(Arrays.asList("via del Castello", "via Elsa Morante", "viale Segre")));
    }

    /* Sample vehicle type */
    public static final Map<String, List<String>> VEHICLE_TYPES = new HashMap<>();

    /*
     * Example of vehicle type:
     *  - economy (economica)
     *  - luxury (di lusso)
     *  - transport (da trasporto)
     */
    static {
        // VEHICLE_TYPES.put("economy", new ArrayList<>(Arrays.asList("economy", "economica")));
        VEHICLE_TYPES.put("luxury", new ArrayList<>(Arrays.asList("luxury", "di lusso")));
        VEHICLE_TYPES.put("transport", new ArrayList<>(Arrays.asList("transport", "da trasporto", "il trasporto")));
    }

    // logger
    public static final Logger log = Logger.getLogger("MuMeLog");
}
