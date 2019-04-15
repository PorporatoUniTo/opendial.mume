package opendial.modules.mume;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import opendial.modules.mume.information.City;
import opendial.modules.mume.information.LocationInfo;
import opendial.modules.mume.information.Slot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static opendial.modules.mume.CarPoolingInformationExtraction.*;
import static opendial.modules.mume.config.Shared.*;
import static opendial.modules.mume.information.Slot.SLOT_ID_SEPARATOR;

class InferLocationInformation {
    static void inferMissingInfo(Map<String, String> oldInformation,
                                 LocationInfo newCity,
                                 LocationInfo newAddress,
                                 LocationInfo newSlot,
                                 boolean hereAnswer,
                                 boolean isStart,
                                 Map<String, String> newInformation,
                                 String machinePrevState) {
        // Map<String, String> newInformation = new HashMap<>();

        JsonParser parser = new JsonParser();

        City city = null;
        String address = null;
        Slot slot = null;
        double lat = -100.0;
        double lon = -200.0;
        /* The user did not specified a city, so the 'city' variable's value has been inffered by the system */
        boolean inferredCity = false;
        boolean unspecifiedSlot = true;

        if (newCity != null)
            city = City.getByName(newCity.getLocation());
        else {
            String oldCity = oldInformation.getOrDefault((isStart) ? START_CITY : END_CITY, NONE);
            if (!oldCity.equals(NONE))
                city = City.getByName(oldCity);
        }

        if (newAddress != null)
            address = newAddress.getLocation();
        else {
            String oldAddress = oldInformation.getOrDefault((isStart) ? START_ADDRESS : END_ADDRESS, NONE);
            if (!oldAddress.equals(NONE))
                address = oldAddress;
        }

        /* Set slot */
        if (newSlot != null) {
            List<Slot> slots = Slot.getByName(newSlot.getLocation());
            City slotCity = City.getByName(oldInformation.getOrDefault((isStart) ? START_CITY : END_CITY, NONE));
            if (slots != null && slotCity != null)
                slots = slots.stream().filter(s -> s.getCity().equals(slotCity)).collect(Collectors.toList());
            if (slots != null && !slots.isEmpty())
                slot = slots.get(0);
        } else {
            // If it has been saved, search for id
            City slotCity = City.getByName(oldInformation.getOrDefault((isStart) ? START_CITY : END_CITY, NONE));
            String oldSlot = oldInformation.getOrDefault((isStart) ? START_SLOT : END_SLOT, NONE);
            if (!oldSlot.equals(NONE))
                slot = Slot.getById(oldSlot + SLOT_ID_SEPARATOR + slotCity);
        }

        List<Slot> sortedSlots = Arrays.stream(oldInformation.getOrDefault((isStart) ? START_SORTED_SLOTS : END_SORTED_SLOTS, "").split(","))
                .map(Slot::getById).filter(s -> s != null).collect(Collectors.toList());

        log.info("Partial 1 " + ((isStart) ? "Start" : "End") + " Sorted Slots: " + sortedSlots);

        log.info(((isStart) ? "Start" : "End") + " City : " + city);
        log.info(((isStart) ? "Start" : "End") + " Address: " + address);
        log.info(((isStart) ? "Start" : "End") + " Slot: " + slot);
        log.info(((isStart) ? "Start" : "End") + " Sorted Slots: " + sortedSlots);


        // String currentUserPosition = getCurrentUserPosition(parser);
        // TESTING: Dipartimento di Informatica
        String currentUserPosition = "45.08914,7.6560533";
        // log.info("Current User Position:\t" + currentUserPosition);
        String[] userPositions = currentUserPosition.split(",");

        /* If the user knows the name of the parking slots and give it, no need of Google */
        if (newSlot != null && slot != null) {
            // If the user did not comminicate a city, just retrieve the (first) slots's one
            if (city == null) {
                city = slot.getCity();
                // if there is some ambiguity in the city, signal it
                inferredCity = !sortedSlots.isEmpty();
            }

            // If there is at least one slots in the city given by the user (or in general)...
            // Retrive all the informaiton!
            // Slot (1)
            // Slot already set
            // Address (2)
            address = slot.getAddress();

            // Latitude (5)
            lat = slot.getLatitude();
            // Longitude (6)
            lon = slot.getLongitude();

            // SortedSlots (7)
            // Filter the slots based on the city explicitly communicated by the user
            sortedSlots = sortSlots((sortedSlots.isEmpty()) ? new LinkedList<>(Arrays.asList(Slot.values())) : sortedSlots, false, city, slot.getLatitude(), slot.getLongitude());

            log.info("Partial 2 " + ((isStart) ? "Start" : "End") + " Sorted Slots: " + sortedSlots);
            // City (3) - InferredCity (4)
            // If the user did not comminicate a city, just retrieve the (first) slots's one
            if (city == null && !sortedSlots.isEmpty()) {
                city = sortedSlots.get(0).getCity();
                // if there is some ambiguity in the city, signal it
                inferredCity = sortedSlots.size() > 1;
            }

            // UnspecifiedSlot (8)
            unspecifiedSlot = false;
        } /* else
            errors.add("Error(" + "NoSlotFound" + ")");
        */
        /* Else, if the user give an address, we can retrieve the nearest slots (or slots) */
        else if (newAddress != null) {
            // Construct the partial address for Google to complete
            String toGoogleMaps = address;
            // If the user gave the city,...
            if (city != null) {
                // ... retrieve it ...
                inferredCity = false;

                // ... and append to the address
                toGoogleMaps += " " + city.getName();
            } else
                inferredCity = true;

            // JSON object for the complete address
            JsonObject queryCompletionResult = null;

            try {
                queryCompletionResult = parser.parse(getGoogleMapsResponseJSON(getMapsSearchURL(toGoogleMaps, currentUserPosition), false, "")).getAsJsonObject();
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
                    for (int i = 0; i < components.size() && (address == null || city == null); i++) {
                        JsonObject component = components.get(i).getAsJsonObject();
                        JsonArray types = component.get(COMPONENT_TYPES).getAsJsonArray();
                        for (JsonElement type : types) {
                            if (type.getAsString().equals(ROUTE))
                                address = component.getAsJsonObject().get(LONG_NAME).getAsString();
                            else if (type.getAsString().equals(LOCALITY) && city == null)
                                city = City.getByName(component.getAsJsonObject().get(LONG_NAME).getAsString());
                        }
                    }

                    sortedSlots = sortSlots(new LinkedList<>(Arrays.asList(Slot.values())), true, ((!inferredCity) ? city : null), Double.parseDouble(location.get(LATITUDE).getAsString()), Double.parseDouble(location.get(LONGITUDE).getAsString()));

                    log.info("Partial 3 " + ((isStart) ? "Start" : "End") + " Sorted Slots: " + sortedSlots);
                    if (!sortedSlots.isEmpty()) {
                        slot = sortedSlots.get(0);
                        sortedSlots = sortedSlots.subList(1, sortedSlots.size());
                    }

                    if (slot != null) {
                        // Slot (1)
                        // slot already set
                        // Address (2)
                        // 'address' already set
                        // City (3)
                        // 'city' alteady set
                        // InferredCity (4)
                        // 'inferredCity' already set

                        // Latitude (5)
                        lat = location.get(LATITUDE).getAsDouble();
                        // Lobgitude (6)
                        lon = location.get(LONGITUDE).getAsDouble();

                        // SortedSlots (7)
                        // sortedSlots already set

                        // UnspecifiedSlot (8)
                        // The user did not gave the precise slots
                        unspecifiedSlot = true;
                    }
                }
            }
            /*
            if (slot == null)
                errors.add("Error(" + "NoSlotFound" + ")");
             */
        }
        /* Else, if the user communicate just the city, select the slots nearer to s/he position */
        else if (newCity != null) {
            // Retains only the slots in the city specified by the user and sort them by the distance from the current position if the user
            sortedSlots = sortSlots(Arrays.asList(Slot.values()), false, city, Double.parseDouble(userPositions[0]), Double.parseDouble(userPositions[1]));

            log.info("Partial 4 " + ((isStart) ? "Start" : "End") + " Sorted Slots: " + sortedSlots);
            if (!sortedSlots.isEmpty()) {
                slot = sortedSlots.get(0);
                sortedSlots = sortedSlots.subList(1, sortedSlots.size());
            }
            if (slot != null) {
                // Slot (1)
                // 'slot' already set
                // Address (2)
                address = slot.getAddress();
                // City (3)
                // 'city' already set
                // InferredCity (4)
                inferredCity = false;
                // Latitude (5)
                lat = Double.parseDouble(userPositions[0]);
                // Longitude (6)
                lon = Double.parseDouble(userPositions[1]);
                // SortedSlots (7)
                // 'sortedSlots' already set

                // UnspecifiedSlot (8)
                unspecifiedSlot = true;
            } /*else
                    errors.add("Error(" + "NoSlotFound" + ")");
                    */
        }
        /* Otherwise, select the nearest slots to the current position of th user if s/he indicate that wants to start from there */
        else if (hereAnswer && machinePrevState.endsWith("SLOT") && slot == null) {
            // Retrive the address and the city of the current user position
            JsonObject currentUserAddress = null;
            try {
                currentUserAddress = parser.parse(getGoogleMapsResponseJSON(getMapsReverseGeocodingURL(currentUserPosition, GEOCODING_LOCALITY), false, "")).getAsJsonObject();
            } catch (MalformedURLException excpetion) {
                excpetion.printStackTrace();
            }
            if (isOK(currentUserAddress) && currentUserAddress.get(RESULTS).getAsJsonArray().size() > 0) {
//                JsonObject bestResult = currentUserAddress.get(RESULTS).getAsJsonArray().get(0).getAsJsonObject();
//                address = bestResult.get(COMPLETE_ADDRESS).getAsString();
//                city = City.getByName(bestResult.get(COMPONENTS).getAsJsonArray().get(0).getAsJsonObject().get(LONG_NAME).getAsString());
                inferredCity = true;
                JsonArray components = currentUserAddress.get(RESULTS).getAsJsonArray().get(0).getAsJsonObject().get(COMPONENTS).getAsJsonArray();
                // for (JsonElement component : components) {
                for (int i = 0; i < components.size() && (address == null || city == null); i++) {
                    JsonObject component = components.get(i).getAsJsonObject();
                    JsonArray types = component.get(COMPONENT_TYPES).getAsJsonArray();
                    for (JsonElement type : types) {
                        if (type.getAsString().equals(ROUTE))
                            address = component.getAsJsonObject().get(LONG_NAME).getAsString();
                        else if (type.getAsString().equals(LOCALITY) && city == null) {
                            city = City.getByName(component.getAsJsonObject().get(LONG_NAME).getAsString());
                            // Do not ask confirmation about the city, the user does not care
                            inferredCity = false;
                        }
                    }
                }
            }

            // Do not filter by city, the user did not give that
            sortedSlots = sortSlots(Arrays.asList(Slot.values()), true, null, Double.parseDouble(userPositions[0]), Double.parseDouble(userPositions[1]));

            log.info("Partial 5 " + ((isStart) ? "Start" : "End") + " Sorted Slots: " + sortedSlots);
            if (!sortedSlots.isEmpty()) {
                slot = sortedSlots.get(0);
                sortedSlots = sortedSlots.subList(1, sortedSlots.size());
            }

            if (slot != null) {
                // Slot (1)
                // 'slot' already set
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
                // 'sortedSlots' already set
                // UnspecifiedSlot(8)
                unspecifiedSlot = true;
            }
        }

        log.info("Final " + ((isStart) ? "Start" : "End") + " City : " + city);
        log.info("Final " + ((isStart) ? "Start" : "End") + " Address: " + address);
        log.info("Final " + ((isStart) ? "Start" : "End") + " Slot: " + slot);
        log.info("Final " + ((isStart) ? "Start" : "End") + " Sorted Slots: " + sortedSlots);

        if (slot != null) {
            newInformation.put((isStart) ? START_SLOT : END_SLOT, slot.getName()); // 1
            newInformation.put((isStart) ? START_SORTED_SLOTS : END_SORTED_SLOTS, sortedSlots.stream().map(Slot::toString).collect(Collectors.joining(",")));  // 7
            newInformation.put((isStart) ? START_LAT : END_LAT, String.valueOf(lat)   // 5
                    /* Avoids characters problems */
                    .replace(".", "_"));
            newInformation.put((isStart) ? START_LON : END_LON, String.valueOf(lon)   // 6
                    /* Avoids characters problems */
                    .replace(".", "_"));
        }
        if (address != null && !address.isEmpty())
            newInformation.put((isStart) ? START_ADDRESS : END_ADDRESS, address);   // 2
        if (city != null)
            newInformation.put((isStart) ? START_CITY : END_CITY, city.getName());    // 3 - 4
        newInformation.put((isStart) ? INFERRED_START_CITY : INFERRED_END_CITY, String.valueOf(inferredCity));    // 3 - 4
        newInformation.put((isStart) ? UNSPECIFIED_START_SLOT : UNSPECIFIED_END_SLOT, String.valueOf(unspecifiedSlot));
        /* slots, address and city END */


        // Geocoding
        /*
        JsonParser parser = new JsonParser();
        boolean waitBetweenRequests = false;
        if (!oldInformation.get(START_CITY).equals(NONE) && !oldInformation.get(START_SLOT).equals(NONE) &&
                (!oldInformation.getOrDefault(START_SLOT, NONE).equals(oldInformation.get(START_SLOT)) ||
                        !oldInformation.getOrDefault(START_CITY, NONE).equals(oldInformation.get(START_CITY)))) {
            String nominatimResponse = getNominatimJSON(oldInformation.get(START_SLOT) + " " + oldInformation.get(START_CITY));

            JsonArray locations = (JsonArray) parser.parse(nominatimResponse);
            if (locations.size() > 0) {
                newInformation.put(START_LAT, locations.get(0).getAsJsonObject().get(LATITUDE).getAsString());
                newInformation.put(START_LON, locations.get(0).getAsJsonObject().get(LONGITUDE).getAsString());
            }
            waitBetweenRequests = true;
        }
        if (!oldInformation.get(END_CITY).equals(NONE) && !oldInformation.get(END_SLOT).equals(NONE) &&
                (!oldInformation.getOrDefault(END_SLOT, NONE).equals(oldInformation.get(END_SLOT)) ||
                        !oldInformation.getOrDefault(END_CITY, NONE).equals(oldInformation.get(END_CITY)))) {
            if (waitBetweenRequests) {
                try {
                    Thread.sleep(NOMINATIM_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            String nominatimResponse = getNominatimJSON(oldInformation.get(END_SLOT) + " " + oldInformation.get(END_CITY));

            JsonArray locations = (JsonArray) parser.parse(nominatimResponse);
            if (locations.size() > 0) {
                newInformation.put(END_LAT, locations.get(0).getAsJsonObject().get(LATITUDE).getAsString());
                newInformation.put(END_LON, locations.get(0).getAsJsonObject().get(LONGITUDE).getAsString());
            }
        }
        */
    }

    private static boolean isOK(JsonObject queryReult) {
        return queryReult != null && queryReult.get("status").getAsString().equals("OK");
    }

    private static List<Slot> sortSlots(List<Slot> slots, boolean filterDistance, City filterCity, double biasLatitude, double biasLongitude) {
        Stream<Slot> sortedSlotsStream = slots.stream();

        // Filter by distance from the specified start point (if the slot was not given)
        if (filterDistance)
            sortedSlotsStream = sortedSlotsStream.filter(s -> {
                double latDiff = biasLatitude - s.getLatitude();
                double lonDiff = biasLongitude - s.getLongitude();
                return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff) < DISTANCE_THRESHOLD;
            });

        // ... and by city, if any was given;
        if (filterCity != null)
            sortedSlotsStream = sortedSlotsStream.filter(s -> s.getCity().name().equalsIgnoreCase(filterCity.getName()));

        // then sort by distance from the specified start point and return
        return sortedSlotsStream.sorted((s1, s2) -> {
            double latDiff1 = biasLatitude - s1.getLatitude();
            double lonDiff1 = biasLongitude - s1.getLongitude();
            double latDiff2 = biasLatitude - s2.getLatitude();
            double lonDiff2 = biasLongitude - s2.getLongitude();
            return Double.compare(Math.sqrt(latDiff1 * latDiff1 + lonDiff1 * lonDiff1), Math.sqrt(latDiff2 * latDiff2 + lonDiff2 * lonDiff2));
        }).collect(Collectors.toList());
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
}
