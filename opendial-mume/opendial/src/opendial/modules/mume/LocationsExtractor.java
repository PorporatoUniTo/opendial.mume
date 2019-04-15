package opendial.modules.mume;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import opendial.modules.mume.information.City;
import opendial.modules.mume.information.LocationInfo;
import opendial.modules.mume.information.Slot;

import java.util.*;
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
     * Extract updated information from a new user utterance.
     *
     * @param annotatedUserUtterance the Annotation conteining the (corrected) user utterance
     * @param locationNERs           the recognised locations in the (corrected) user utterance
     * @param information            Map<String, String> that will contains the updated information
     * @param oldInformation         the Map<String, String> that contains the old information
     * @param machinePrevState       the previous state of the machine, needed if the user is answering a question from the system
     * @return A List<LocationInfo> of the address found, to filter out from the dates in the original sentence and
     * avoid misunderstandings between date and place name (such as 'XVIII dicembre')
     */
    List<LocationInfo> extractLocations(Annotation annotatedUserUtterance,
                                        List<List<IndexedWord>> locationNERs,
                                        Map<String, String> information,
                                        Map<String, String> oldInformation,
                                        String machinePrevState) {
        List<LocationInfo> addresses = new ArrayList<>();
        try {
            LocationInfo newStartCity = null;
            LocationInfo newEndCity = null;
            LocationInfo newStartAddress = null;
            LocationInfo newEndAddress = null;
            LocationInfo newStartSlot = null;
            LocationInfo newEndSlot = null;

            List<CoreLabel> tokens = annotatedUserUtterance.get(CoreAnnotations.TokensAnnotation.class);
            SemanticGraph dependencies = annotatedUserUtterance.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);

            List<LocationInfo> cities = new ArrayList<>();
            List<LocationInfo> slots = new ArrayList<>();
            boolean hereClue = tokens.stream().anyMatch(t -> HERE_WORDS.contains(t.originalText()));

            findLocationInfo(cities, addresses, slots, tokens, dependencies, locationNERs);


            //log.info("Locations:\t" + locationNERs.toString());
            log.info("Cities:");
            cities.forEach(d -> log.info(d.toString()));
            log.info("Addresses:");
            addresses.forEach(t -> log.info(t.toString()));
            log.info("Slots:");
            slots.forEach(t -> log.info(t.toString()));
            log.info("Here:\t" + hereClue);

            boolean doneCase = false;
            boolean doneVerbs = false;

            do {
                do {
                    /* Old values for checking for changes */
                    LocationInfo previousStartCity = newStartCity;
                    LocationInfo previousEndCity = newEndCity;
                    LocationInfo previousStartAddress = newStartAddress;
                    LocationInfo previousEndAddress = newEndAddress;
                    LocationInfo previousStartSlot = newStartSlot;
                    LocationInfo previousEndSlot = newEndSlot;

                    /* SEARCH FOR UNAMBIGUOUS LOCATION EXPRESSIONS (those that are unequivocally about the start or the end of the journey) */
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

                    /* GOOGLE_API_ADDRESS */
                    for (LocationInfo address : addresses) {
                        if (address.getCaseType() != null) {
                            /* "... da piazza Vittorio Veneto..." */
                            if (newStartAddress == null && STRONG_START_ADDRESS_CASE.contains(address.getCaseType())) {
                                newStartAddress = address;
                                address.isStart = true;
                            }
                            /* "... a Pinerolo Olimpica..." */
                            else if (newEndAddress == null && STRONG_END_ADDRESS_CASE.contains(address.getCaseType())) {
                                newEndAddress = address;
                                address.isEnd = true;
                            }
                        }
                    }

                    /* SLOTS */
                    for (LocationInfo slot : slots) {
                        if (slot.getCaseType() != null) {
                            /* "... da BERNINI..." */
                            if (newStartSlot == null && STRONG_START_SLOT_CASE.contains(slot.getCaseType())) {
                                newStartSlot = slot;
                                slot.isStart = true;
                            }
                            /* "... a BOLOGNA ..." */
                            else if (newEndSlot == null && STRONG_END_SLOT_CASE.contains(slot.getCaseType())) {
                                newEndSlot = slot;
                                slot.isEnd = true;
                            }
                        }
                    }

                    /* SEARCH FOR AMBIGUOUS SPATIAL EXPRESSION (those which role dependes on other information in the sentence) */
                    /* CITIES */
                    for (LocationInfo city : cities) {
                        if (city.getCaseType() != null && DEPENDANT_CITY_CASE.contains(city.getCaseType())) {
                            /* "... da Pinerolo Olimpica a Pinerolo..." */
                            if (newStartCity == null &&
                                    (newStartAddress != null && newStartAddress.isGovernorOf(city)) ||
                                    (newStartSlot != null && newStartSlot.isGovernorOf(city))) {
                                newStartCity = city;
                                city.isStart = true;
                            }
                            /* "... a viale Segre a Nichelino..." */
                            if (newEndCity == null &&
                                    (newEndAddress != null && newEndAddress.isGovernorOf(city)) ||
                                    (newEndSlot != null && newEndSlot.isGovernorOf(city))) {
                                newEndCity = city;
                                city.isEnd = true;
                            }
                        }
                        /* "... da Pinerolo... a Nichelino..." */
                        else if (newEndCity == null && WEAK_END_CITY_CASE.contains(city.getCaseType()) &&
                                newStartCity != null && STRONG_START_CITY_CASE.contains(newStartCity.getCaseType())) {
                            newEndCity = city;
                            city.isEnd = true;
                        }
                        /* "Voglio andare a Nichelino [domani]." */
                        else if (newEndCity == null && WEAK_END_CITY_CASE.contains(city.getCaseType()) && cities.size() == 1) {
                            newEndCity = city;
                            city.isEnd = true;
                        }
                    }

                    /* ADDRESSES */
                    for (LocationInfo address : addresses) {
                        if (address.getCaseType() != null && DEPENDANT_ADDRESS_CASE.contains(address.getCaseType())) {
                            /* "... da Pinerolo in piazza Avis..." */
                            if (newStartAddress == null && !address.isEnd &&
                                    newStartCity != null && newStartCity.isGovernorOf(address)) {
                                newStartAddress = address;
                                address.isStart = true;
                            }
                            /* "... fino a Nichelino in via del Castello..." */
                            if (newEndAddress == null &&
                                    newEndCity != null && newEndCity.isGovernorOf(address)) {
                                newEndAddress = address;
                                address.isEnd = true;
                            }
                        }
                    }

                    /* SLOTS */
                    for (LocationInfo slot : slots) {
                        /* "... da BERNINI... a SAVOIA..." */
                        if (newEndSlot == null && WEAK_END_SLOT_CASE.contains(slot.getCaseType()) &&
                                newStartSlot != null && STRONG_START_SLOT_CASE.contains(newStartSlot.getCaseType())) {
                            newEndSlot = slot;
                            slot.isEnd = true;
                        }
                        /* "Voglio andare a TORTONA [domani]." */
                        else if (newEndSlot == null && WEAK_END_SLOT_CASE.contains(slot.getCaseType()) && slots.size() == 1) {
                            newEndSlot = slot;
                            slot.isEnd = true;
                        } else if (slot.getCaseType() != null && DEPENDANT_SLOT_CASE.contains(slot.getCaseType())) {
                            /* "... da Torino a BERNINI..." */
                            if (newStartSlot == null &&
                                    (newStartAddress != null && newStartAddress.isGovernorOf(slot)) ||
                                    (newStartCity != null && newStartCity.isGovernorOf(slot))) {
                                newStartSlot = slot;
                                slot.isStart = true;
                            }
                            /* "... a Torino a TORTONA..." */
                            if (newEndSlot == null &&
                                    (newEndAddress != null && newEndAddress.isGovernorOf(slot)) ||
                                    (newEndCity != null && newEndSlot.isGovernorOf(slot))) {
                                newEndSlot = slot;
                                slot.isEnd = true;
                            }
                        }
                    }

                    /* If no change occurred in the current iteration, exit */
                    if ((previousStartCity == null && newStartCity == null || previousStartCity != null && previousStartCity.equals(newStartCity)) &&
                            (previousEndCity == null && newEndCity == null || previousEndCity != null && previousEndCity.equals(newEndCity)) &&
                            (previousStartAddress == null && newStartAddress == null || previousStartAddress != null && previousStartAddress.equals(newStartAddress)) &&
                            (previousEndAddress == null && newEndAddress == null || previousEndAddress != null && previousEndAddress.equals(newEndAddress)) &&
                            (previousStartSlot == null && newStartSlot == null || previousStartSlot != null && previousStartSlot.equals(newStartSlot)) &&
                            (previousEndSlot == null && newEndSlot == null || previousEndSlot != null && previousEndSlot.equals(newEndSlot)))
                        doneCase = true;
                } while (!doneCase);

                /* Old values for checking for changes */
                LocationInfo previousStartCity = newStartCity;
                LocationInfo previousEndDate = newEndCity;
                LocationInfo previousStartAddress = newStartAddress;
                LocationInfo previousEndAddress = newEndAddress;
                LocationInfo previousStartSlot = newStartSlot;
                LocationInfo previousEndTime = newEndSlot;

                /* After checking the presence of more significant indicator, check the verb for start or end clue */
                /* CITIES */
                for (LocationInfo city : cities) {
                    if (newStartCity == null && START_VERBS.contains(city.getFirstVerbGovernorLemma()) && !city.isEnd) {
                        newStartCity = city;
                        city.isStart = true;
                    } else if (newEndCity == null && END_VERBS.contains(city.getFirstVerbGovernorLemma()) && !city.isStart) {
                        newEndCity = city;
                        city.isEnd = true;
                    }
                    /* Ho bisogno di una macchina di Nichelino */
                    if (newStartCity == null &&
                            WEAK_START_CITY_CASE.contains(city.getCaseType()) &&
                            // !START_VERBS.contains(city.getFirstVerbGovernorLemma()) &&
                            !END_VERBS.contains(city.getFirstVerbGovernorLemma()) &&
                            !city.isEnd) {
                        newStartCity = city;
                        city.isStart = true;
                    }
                }

                /* GOOGLE_API_ADDRESS */
                for (LocationInfo address : addresses) {
                    if (newStartAddress == null && START_VERBS.contains(address.getFirstVerbGovernorLemma()) && !address.isEnd) {
                        newStartAddress = address;
                        address.isStart = true;
                    } else if (newEndAddress == null && END_VERBS.contains(address.getFirstVerbGovernorLemma()) && !address.isStart) {
                        newEndAddress = address;
                        address.isEnd = true;
                    }
                    /* Voglio la macchina in piazza Avis */
                    if (newStartAddress == null &&
                            WEAK_START_ADDRESS_CASE.contains(address.getCaseType()) &&
                            // START_VERBS.contains(address.getFirstVerbGovernorLemma()) &&
                            !END_VERBS.contains(address.getFirstVerbGovernorLemma()) &&
                            !address.isEnd) {
                        newStartAddress = address;
                        address.isStart = true;
                    }
                }

                /* SLOTS */
                for (LocationInfo slot : slots) {
                    if (newStartSlot == null && START_VERBS.contains(slot.getFirstVerbGovernorLemma()) && !slot.isEnd) {
                        newStartSlot = slot;
                        slot.isStart = true;
                    } else if (newEndSlot == null && END_VERBS.contains(slot.getFirstVerbGovernorLemma()) && !slot.isStart) {
                        newEndSlot = slot;
                        slot.isEnd = true;
                    }
                    /* Ho bisogno di una macchina di Nichelino */
                    if (newStartSlot == null &&
                            WEAK_START_SLOT_CASE.contains(slot.getCaseType()) &&
                            // START_VERBS.contains(slot.getFirstVerbGovernorLemma()) &&
                            !END_VERBS.contains(slot.getFirstVerbGovernorLemma()) &&
                            !slot.isEnd) {
                        newStartSlot = slot;
                        slot.isStart = true;
                    }
                }

                /* If no chnge occurred in the current iteration, exit */
                if ((previousStartCity == null && newStartCity == null || previousStartCity != null && previousStartCity.equals(newStartCity)) &&
                        (previousEndDate == null && newEndCity == null || previousEndDate != null && previousEndDate.equals(newEndCity)) &&
                        (previousStartAddress == null && newStartAddress == null || previousStartAddress != null && previousStartAddress.equals(newStartAddress)) &&
                        (previousEndAddress == null && newEndAddress == null || previousEndAddress != null && previousEndAddress.equals(newEndAddress)) &&
                        (previousEndTime == null && newEndSlot == null || previousEndTime != null && previousEndTime.equals(newEndSlot)) &&
                        (previousStartSlot == null && newStartSlot == null || previousStartSlot != null && previousStartSlot.equals(newStartSlot)))
                    doneVerbs = true;
            } while (!doneVerbs);


            /* Check if the user is answering to targetted questions */
            /* Not updating SemanticGraph indexes, but it doesn't matter */
            if (slots.size() == 1 && newStartSlot == null && machinePrevState.endsWith("START_SLOT") && !slots.get(0).isEnd) {
                newStartSlot = slots.get(0);
                slots.get(0).isStart = true;
            } else if (addresses.size() == 1 && newStartAddress == null && machinePrevState.endsWith("START_SLOT") && !addresses.get(0).isEnd) {
                newStartAddress = addresses.get(0);
                addresses.get(0).isStart = true;
            } else if (cities.size() == 1 && newStartCity == null && machinePrevState.endsWith("START_SLOT") && !cities.get(0).isEnd) {
                newStartCity = cities.get(0);
                cities.get(0).isStart = true;
            }


            if (!machinePrevState.endsWith("TIME") && !machinePrevState.endsWith("DATE"))
                for (CoreLabel token : tokens)
                    if (HERE_WORDS.contains(token.originalText()))
                        hereClue = true;


            log.info("Extracted Start City: " + newStartCity);
            log.info("Extracted End City: " + newEndCity);
            log.info("Extracted Start Address: " + newStartAddress);
            log.info("Extracted End Address: " + newEndAddress);
            log.info("Extracted Start Slot: " + newStartSlot);
            log.info("Extracted End Slot: " + newEndSlot);

            // Infer missing information
            InferLocationInformation.inferMissingInfo(oldInformation, newStartCity, newStartAddress, newStartSlot, hereClue, true, information, machinePrevState);
            InferLocationInformation.inferMissingInfo(oldInformation, newEndCity, newEndAddress, newEndSlot, hereClue, false, information, machinePrevState);
        } catch (NullPointerException exception) {
            exception.printStackTrace();
            log.severe("USER UTTERANCE:\t" + annotatedUserUtterance.toString());
            log.severe("LOCATIONS FOUND:\t" + locationNERs.toString());
        }


        return addresses;
    }

    /*
    /**
     * Execute the geocoding for the found location via Nominatim.
     *
     * @param location the String with the location name
     * @return the JSON Object with the location innformation
     /
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
    */

    /*
    /**
     * Extract the city from an address
     /
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
    */

    /**
     * Finds and partition diverse kind of spatial information.
     * <p>
     * This method seek and partition information about slots, addresses and cities in the user utterance.
     *
     * @param cities       the (to be filled) List<LocationInfo> of cities found in the user utterance
     * @param addresses    the (to be filled) List<LocationInfo> of addresses found in the user utterance
     * @param slots        the (to be filled) List<LocationInfo> of slots found in the user utterance
     * @param tokens       the List<CoreLabel> of the tokens in the user utterance
     * @param dependencies the SemanticGraph of the token's dependencies of the user utterance
     * @param locationNERs the List<List<IndexedWord>> of the location recognised by Tint (if any)
     */
    private void findLocationInfo(List<LocationInfo> cities, List<LocationInfo> addresses, List<LocationInfo> slots, List<CoreLabel> tokens, SemanticGraph dependencies, List<List<IndexedWord>> locationNERs) {
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
                    addresses.add(new LocationInfo(address, tokens, dependencies));

                    List<IndexedWord> maybeCity = City.extractFromAddress(address);
                    if (!maybeCity.isEmpty())
                        cities.add(new LocationInfo(maybeCity, tokens, dependencies));
                } else {
                    for (City city : City.values())
                        /* IMPORTANT: IndexedWord's indeces and TokensAnnotation's indeces start from 1 */
                        if (token.index() + city.getNumberOfWords() <= tokens.size()) {
                            List<CoreLabel> cityTokens = tokens.subList(token.index() - 1, token.index() + city.getNumberOfWords() - 1);
                            List<IndexedWord> cityIndexed = cityTokens.stream().map(t -> dependencies.getNodeByIndex(t.index())).collect(Collectors.toList());
                            if (cityTokens.stream().map(CoreLabel::originalText).collect(Collectors.joining(" ")).equalsIgnoreCase(city.getName())) {
                                cities.add(new LocationInfo(cityIndexed, tokens, dependencies));
                                indexToCheck.removeAll(cityTokens.stream().map(CoreLabel::index).collect(Collectors.toSet()));
                            }
                        }

                    if (indexToCheck.contains(token.index())) {
                        for (Map.Entry<String, Integer> slot : slotNames.entrySet())
                            /* IMPORTANT: IndexedWord's indeces and TokensAnnotation's indeces start from 1 */
                            if (token.index() + slot.getValue() <= tokens.size()) {
                                List<CoreLabel> slotTokens = tokens.subList(token.index() - 1, token.index() + slot.getValue() - 1);
                                List<IndexedWord> slotIndexed = slotTokens.stream().map(t -> dependencies.getNodeByIndex(t.index())).collect(Collectors.toList());
                                if (slotTokens.stream().map(CoreLabel::originalText).collect(Collectors.joining(" ")).equalsIgnoreCase(slot.getKey())) {
                                    slots.add(new LocationInfo(slotIndexed, tokens, dependencies));
                                    indexToCheck.removeAll(slotTokens.stream().map(CoreLabel::index).collect(Collectors.toSet()));
                                }
                            }
                    }
                }
            }
        }

        for (List<IndexedWord> loc : locationNERs) {
            if (indexToCheck.containsAll(loc.stream().map(IndexedWord::index).collect(Collectors.toSet())))
                addresses.add(new LocationInfo(loc, tokens, dependencies));
        }
    }
}
