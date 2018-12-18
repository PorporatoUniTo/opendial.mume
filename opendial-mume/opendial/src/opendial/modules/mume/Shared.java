package opendial.modules.mume;

import java.util.*;
import java.util.logging.Logger;

class Shared {
    static final String NONE = "Missing";

    // Nominatim
    static final String NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search.php?format=json&q=";
    static final int NOMINATIM_TIMEOUT = 1000;

    static final Set<String> STRONG_START_CITY_CASE = new HashSet<>(Arrays.asList("da"));   // "da Pinerolo"
    // DI -> "Mi serve una delle auto di Pinerolo" ==> "Voglio lasciare la macchina in via del Castello DI Nichelino"
    static final Set<String> STRONG_END_CITY_CASE = new HashSet<>(Arrays.asList());    // A -> "da Nichelino a Pinerolo" => "Voglio andare da piazza Vittorio Veneto A Pinerolo a Nichelino"

    static final Set<String> STRONG_START_SLOT_CASE = new HashSet<>(Arrays.asList("da"));  // "da piazza Vittorio Veneto"
    static final Set<String> STRONG_END_SLOT_CASE = new HashSet<>(Arrays.asList("a")); // "a piazza Vittorio Veneto"

    static final Set<String> STRONG_START_DATE_CASE = new HashSet<>(Arrays.asList("da", "dal"));  // "da domani", "dal 26"
    static final Set<String> STRONG_END_DATE_CASE = new HashSet<>(Arrays.asList("fino", "a", "al")); // "fino a dopodomani", "al 27", "a domani"
    static final Set<String> STRONG_SINGLE_DATE_CASE = new HashSet<>(Arrays.asList("per")); // "per domani", "per il 30" (30 -case-> per -det-> il)
    // DI -> "dalle 16 alle 19 di domani" => "Mi serve un'auto dalle _ DI oggi alle _ DI domani"
    // DEL -> "dalle 16 alle 19 del 20 febbraio" => "Mi serve un'auto dalle _ di oggi alle _ DEL 20 febbraio"
    static final Set<String> DEPENDANT_DATE_CASE = new HashSet<>(Arrays.asList("del", "di"));   // "dalle _ alle _ del 20 febbraio", "dalle _ alle _ di domani"

    static final Set<String> STRONG_START_TIME_CASE = new HashSet<>(Arrays.asList("dalle"));   // "dalle 14"
    static final Set<String> STRONG_END_TIME_CASE = new HashSet<>(Arrays.asList("fino"));    // "fino alle 17" (17 -case-> fino -mwe-> alle)
    // ALLE -> "dalle _ alle 18" => "Prendero' l'auto ALLE 8"
    static final Set<String> DEPENDANT_TIME_CASE = new HashSet<>(Arrays.asList("alle", "le"));    // "fino a domani alle 17", "entro le 14 di dopodomani"

    static final Set<String> START_VERBS = new HashSet<>(Arrays.asList("partire", "prendere", "prenotare", "usare"));
    static final Set<String> END_VERBS = new HashSet<>(Arrays.asList("arrivare", "lasciare", "posare"));

    /* Sample citis */
    static final Set<String> CITIES = new HashSet<>(Arrays.asList("Pinerolo", "Nichelino"));
    static final Map<String, List<String>> CITIES_ADDRESSES = new HashMap<>();

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
    static final Map<String, List<String>> VEHICLE_TYPES = new HashMap<>();

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
    final static Logger log = Logger.getLogger("MuMeLog");
}
