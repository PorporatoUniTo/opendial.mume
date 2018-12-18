package opendial.modules.mume;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import opendial.modules.mume.information.LocationInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static opendial.modules.mume.config.Shared.*;

class LocationsExtractor {
    static private LocationsExtractor extractor = null;

    private LocationsExtractor() {
    }

    static LocationsExtractor getInstance() {
        if (extractor == null)
            extractor = new LocationsExtractor();
        return extractor;
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

    /**
     * Extract the city from an address
     */
    private void getCityFromAddress(List<IndexedWord> address, List<LocationInfo> addresses, List<LocationInfo> cities, List<CoreLabel> tokens, SemanticGraph dependencies) {
        for (String city : CITIES) {
            String[] cityWords = city.split(" ");
            int i = 0;
            while (i < address.size() && i < cityWords.length &&
                    address.get(address.size() - i - 1).originalText().equals(cityWords[cityWords.length - i - 1])) {
                i++;
            }
            if (i > 0)
                cities.add(new LocationInfo(address.subList(address.size() - i, address.size()), tokens, dependencies));
            addresses.add(new LocationInfo(address.subList(0, address.size() - i), tokens, dependencies));
        }
    }

    /**
     * Separates cities' NERs from addresses' NERs.
     *
     * @param locationNERs the Lis<List<IndexedWord>> of NERs found in the (corrected) user utterance
     * @param cities       the (to-be-filled) List<LocationInfo> of cities' NERs
     * @param addresses    the (to-be-filled) List<LocationInfo> of addresses' NERs
     */
    private void partitionLocation(List<List<IndexedWord>> locationNERs, List<LocationInfo> cities, List<LocationInfo> addresses, List<CoreLabel> tokens, SemanticGraph dependencies) {
        for (List<IndexedWord> location : locationNERs) {
            String locationText = location.stream().map(IndexedWord::originalText).collect(Collectors.joining(" "));
            if (CITIES.stream().map(String::toLowerCase).collect(Collectors.toList()).contains(locationText.toLowerCase()))
                cities.add(new LocationInfo(location, tokens, dependencies));
            else {
                addresses.add(new LocationInfo(location, tokens, dependencies));
            }
        }
    }

    /**
     * Extract updated information from a new user utterance.
     *
     * @param annotatedUserUtterance the Annotation conteining the (corrected) user utterance
     * @param locationNERs           the recognised locations in the (corrected) user utterance
     * @param information            Map<String, String> that will contains the updated information
     * @param oldInformation         the Map<String, String> that contains the old information
     * @param machinePrevState       the previous state of the machine, needed if the user is answering a question from the system
     */
    void extractLocations(Annotation annotatedUserUtterance,
                          List<List<IndexedWord>> locationNERs,
                          Map<String, String> information,
                          Map<String, String> oldInformation,
                          String machinePrevState) {
        try {
            LocationInfo newStartCity = null;
            LocationInfo newEndCity = null;
            LocationInfo newStartSlot = null;
            LocationInfo newEndSlot = null;

            List<CoreLabel> tokens = annotatedUserUtterance.get(CoreAnnotations.TokensAnnotation.class);
            SemanticGraph dependencies = annotatedUserUtterance.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);

            List<LocationInfo> cities = new ArrayList<>();
            List<LocationInfo> addresses = new ArrayList<>();

            partitionLocation(locationNERs, cities, addresses, tokens, dependencies);

            log.info("Locations:\t" + locationNERs.toString());
            log.info("Cities:");
            cities.forEach(d -> log.info(d.toString()));
            log.info("Addresses:");
            addresses.forEach(t -> log.info(t.toString()));

            boolean doneCase = false;
            boolean doneVerbs = false;

            do {
                do {
                    /* Old values for checking for changes */
                    LocationInfo previousStartCity = newStartCity;
                    LocationInfo previousEndCity = newEndCity;
                    LocationInfo previousStartSlot = newStartSlot;
                    LocationInfo previousEndSlot = newEndSlot;

                    /* SEARCH FOR UNAMBIGUOUS TEMPORAL EXPRESSIONS (those that are unequivocally about the start or the end of the journey) */
                    /* CITIES */
                    for (LocationInfo city : cities) {
                        if (city.getCaseType() != null) {
                            /* "... da Pinerolo..." */
                            if (newStartCity == null && STRONG_START_CITY_CASE.contains(city.getCaseType())) {
                                newStartCity = city;
                                city.isStart = true;
                            }
                            /* "... fino a Nichelino..." */
                            else if (newEndCity == null && STRONG_END_CITY_CASE.contains(city.getCaseType())) {
                                newEndCity = city;
                                city.isEnd = true;
                            }
                        }
                    }

                    /* SLOTS */
                    for (LocationInfo slot : addresses) {
                        if (slot.getCaseType() != null) {
                            /* "... da piazza Vittorio Veneto..." */
                            if (newStartSlot == null && STRONG_START_SLOT_CASE.contains(slot.getCaseType())) {
                                newStartSlot = slot;
                                slot.isStart = true;
                            }
                            /* "... a Pinerolo Olimpica..." */
                            else if (newEndSlot == null && STRONG_END_SLOT_CASE.contains(slot.getCaseType())) {
                                newEndSlot = slot;
                                slot.isEnd = true;
                            }
                        }
                    }

                    /* SEARCH FOR AMBIGUOUS TEMPORAL EXPRESSION (those which role dependes on other information in the sentence) */
                    /* CITIES */
                    for (LocationInfo city : cities) {
                        /* "... da Pinerolo... a Nichelino..." */
                        if (newEndCity == null && WEAK_END_CITY_CASE.contains(city.getCaseType()) &&
                                newStartCity != null && STRONG_START_DATE_CASE.contains(newStartCity.getCaseType())) {
                            newEndCity = city;
                            city.isEnd = true;
                        } else if (DEPENDANT_CITY_CASE.contains(city.getCaseType())) {
                            /* "... da Pinerolo Olimpica a Pinerolo..." */
                            if (newStartCity == null &&
                                    newStartSlot != null && newStartSlot.isGovernorOf(city)) {
                                newStartCity = city;
                                city.isStart = true;
                            }
                            /* "... a viale Segre a Nichelino..." */
                            if (newEndCity == null &&
                                    newEndSlot != null && newEndSlot.isGovernorOf(city)) {
                                newEndCity = city;
                                city.isEnd = true;
                            }
                        }
                    }

                    /* SLOTS */
                    for (LocationInfo slot : addresses) {
                        if (DEPENDANT_SLOT_CASE.contains(slot.getCaseType())) {
                            /* "... da Pinerolo in piazza Avis..." */
                            if (newStartSlot == null && !slot.isEnd &&
                                    newStartCity != null && newStartCity.isGovernorOf(slot)) {
                                newStartSlot = slot;
                                slot.isStart = true;
                            }
                            /* "... fino a dopodomani alle 9..." */
                            if (newEndSlot == null &&
                                    newEndCity != null && newEndCity.isGovernorOf(slot)) {
                                newEndCity = slot;
                                slot.isEnd = true;
                            }
                        }
                    }

                    /* If no change occurred in the current iteration, exit */
                    if ((previousStartCity == null && newStartCity == null || previousStartCity != null && previousStartCity.equals(newStartCity)) &&
                            (previousEndCity == null && newEndCity == null || previousEndCity != null && previousEndCity.equals(newEndCity)) &&
                            (previousStartSlot == null && newStartSlot == null || previousStartSlot != null && previousStartSlot.equals(newStartSlot)) &&
                            (previousEndSlot == null && newEndSlot == null || previousEndSlot != null && previousEndSlot.equals(newEndSlot)))
                        doneCase = true;
                } while (!doneCase);

                /* Old values for checking for changes */
                LocationInfo previousStartCity = newStartCity;
                LocationInfo previousEndDate = newEndCity;
                LocationInfo previousStartSlot = newStartSlot;
                LocationInfo previousEndTime = newEndSlot;

                /* After checking the presence of more significant indicator, check the verb for start or end clue */
                /* CITIES */
                for (LocationInfo city : cities) {
                    if (newStartCity == null && START_VERBS.contains(city.getFirstVerbGovernorLemma())) {
                        newStartCity = city;
                        city.isStart = true;
                    } else if (newEndCity == null && END_VERBS.contains(city.getFirstVerbGovernorLemma())) {
                        newEndCity = city;
                        city.isEnd = true;
                    }
                }
                /* TIMES */
                for (LocationInfo slot : addresses) {
                    if (newStartSlot == null && START_VERBS.contains(slot.getFirstVerbGovernorLemma())) {
                        newStartSlot = slot;
                        slot.isStart = true;
                    } else if (newEndSlot == null && END_VERBS.contains(slot.getFirstVerbGovernorLemma())) {
                        newEndSlot = slot;
                        slot.isEnd = true;
                    }
                }

                /* If no chnge occurred in the current iteration, exit */
                if ((previousStartCity == null && newStartCity == null || previousStartCity != null && previousStartCity.equals(newStartCity)) &&
                        (previousEndDate == null && newEndCity == null || previousEndDate != null && previousEndDate.equals(newEndCity)) &&
                        (previousStartSlot == null && newStartSlot == null || previousStartSlot != null && previousStartSlot.equals(newStartSlot)) &&
                        (previousEndTime == null && newEndSlot == null || previousEndTime != null && previousEndTime.equals(newEndSlot)))
                    doneVerbs = true;
            } while (!doneVerbs);


            /* Check if the user is answering to targetted questions */
            /* Not updating indexes, but it doesn't matter */
            if (cities.size() == 1 && newStartCity == null && machinePrevState.endsWith("START_DATE"))
                newStartCity = cities.get(0);
            else if (cities.size() == 1 && newEndCity == null && machinePrevState.endsWith("END_DATE"))
                newEndCity = cities.get(0);
            else if (addresses.size() == 1 && newStartSlot == null && machinePrevState.endsWith("START_TIME"))
                newStartSlot = addresses.get(0);
            else if (addresses.size() == 1 && newEndSlot == null && machinePrevState.endsWith("END_TIME"))
                newEndSlot = addresses.get(0);


            /* INFER slor and/or city from the information extracted */

            log.info("Final Start Date: " + newStartCity);
            log.info("Final End Date: " + newEndCity);
            log.info("Final Start Time: " + newStartSlot);
            log.info("Final End Time: " + newEndSlot);


            if (newStartCity != null)
                information.put("startCity", newStartCity.getLocation());
            if (newEndCity != null)
                information.put("endCity", newEndCity.getLocation());
            if (newStartSlot != null)
                information.put("startSlot", newStartSlot.getLocation());
            if (newEndSlot != null)
                information.put("endSlot", newEndSlot.getLocation());

            JsonParser parser = new JsonParser();
            boolean waitBetweenRequests = false;
            if (!information.get("startCity").equals(NONE) && !information.get("startSlot").equals(NONE) &&
                    (!information.getOrDefault("startSlot", NONE).equals(oldInformation.get("startSlot")) ||
                            !information.getOrDefault("startCity", NONE).equals(oldInformation.get("startCity")))) {
                String nominatimResponse = getNominatimJSON(information.get("startSlot") + " " + information.get("startCity"));

                JsonArray locations = (JsonArray) parser.parse(nominatimResponse);
                if (locations.size() > 0) {
                    information.put("startLat", locations.get(0).getAsJsonObject().get("lat").getAsString());
                    information.put("startLon", locations.get(0).getAsJsonObject().get("lon").getAsString());
                }
                waitBetweenRequests = true;
            }
            if (!information.get("endCity").equals(NONE) && !information.get("endSlot").equals(NONE) &&
                    (!information.getOrDefault("endSlot", NONE).equals(oldInformation.get("endSlot")) ||
                            !information.getOrDefault("endCity", NONE).equals(oldInformation.get("endCity")))) {
                if (waitBetweenRequests) {
                    try {
                        Thread.sleep(NOMINATIM_TIMEOUT);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                String nominatimResponse = getNominatimJSON(information.get("endSlot") + " " + information.get("endCity"));

                JsonArray locations = (JsonArray) parser.parse(nominatimResponse);
                if (locations.size() > 0) {
                    information.put("endLat", locations.get(0).getAsJsonObject().get("lat").getAsString());
                    information.put("endLon", locations.get(0).getAsJsonObject().get("lon").getAsString());
                }
            }
        } catch (NullPointerException exception) {
            exception.printStackTrace();
            log.severe("USER UTTERANCE:\t" + annotatedUserUtterance.toString());
            log.severe("LOCATIONS FOUND:\t" + locationNERs.toString());
        }
    }
}
