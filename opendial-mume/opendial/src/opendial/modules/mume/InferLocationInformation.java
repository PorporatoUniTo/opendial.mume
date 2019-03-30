package opendial.modules.mume;

import com.google.gson.JsonArray;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static opendial.modules.mume.CarPoolingInformationExtraction.*;
import static opendial.modules.mume.config.Shared.*;

class InferLocationInformation {
    static void inferMissingInfo(Map<String, String> oldInformation,
                                 LocationInfo newCity,
                                 LocationInfo newAddress,
                                 LocationInfo newSlot,
                                 boolean hereAnswer,
                                 boolean isStart,
                                 Map<String, String> newInformation) {
        // Map<String, String> newInformation = new HashMap<>();


        JsonParser parser = new JsonParser();

        City city = null;
        String address = null;
        List<Slot> slots = null;

        List<Slot> sortedSlots = new ArrayList<>();
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

        if (newSlot != null)
            slots = Slot.getByName(newSlot.getLocation());
        else {
            String oldSlot = oldInformation.getOrDefault((isStart) ? START_SLOT : END_SLOT, NONE);
            if (!oldSlot.equals(NONE))
                slots = Slot.getByName(oldSlot);
        }

        // String currentUserPosition = getCurrentUserPosition(parser);
        // TESTING: Dipartimento di Informatica
        String currentUserPosition = "45.08914,7.6560533";
        // log.info("Current User Position:\t" + currentUserPosition);
        String[] userPositions = currentUserPosition.split(",");

        /* If the user knows the name of the parking slots and give it, no need of Google */
        if (newSlot != null && slots != null && !slots.isEmpty()) {
            // Filter the slots based on the city explicitly communicated by the user
            sortedSlots = sortSlots(slots, false, city, userPositions[0], userPositions[1]);

            // If the user did not comminicate a city, just retrieve the (first) slots's one
            if (city == null && !sortedSlots.isEmpty()) {
                city = sortedSlots.get(0).getCity();
                // if there is some ambiguity in the city, signal it
                inferredCity = sortedSlots.size() > 1;
            }

            // If there is at least one slots in the city given by the user (or in general)...
            if (!sortedSlots.isEmpty()) {
                // Retrive all the informaiton!
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

                // SortedSlots (1 - 7)
                // sortedSlots already set

                // UnspecifiedSlot (8)
                unspecifiedSlot = false;
            } /*else
                    errors.add("Error(" + "NoSlotFound" + ")");
                    */
        }
        /* Else, if the user give an address, we can retrieve the nearest slots (or slots) */
        else if (newAddress != null) {
            // The user did not gave the precise slots
            unspecifiedSlot = true;

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
            if (queryCompletionResult != null && queryCompletionResult.get("status").getAsString().equals("OK")) {
                JsonObject bestAddress = queryCompletionResult.get("candidates").getAsJsonArray().get(0).getAsJsonObject();
                address = bestAddress.get(COMPLETE_ADDRESS).getAsString();

                JsonObject location = bestAddress.getAsJsonObject().get("geometry")
                        .getAsJsonObject().get("location").getAsJsonObject();

                if (city == null)
                    try {
                        JsonObject geoQueryResult = parser.parse(getGoogleMapsResponseJSON(getMapsReverseGeocodingURL(location.get(LATITUDE) + "," + location.get(LONGITUDE), GEOCODING_LOCALITY), false, "")).getAsJsonObject();
                        if (geoQueryResult != null && geoQueryResult.get("status").getAsString().equals("OK") && geoQueryResult.get(RESULTS).getAsJsonArray().size() > 0) {
                            City geoCity = City.getByName(geoQueryResult.get(RESULTS).getAsJsonArray().get(0).getAsJsonObject().get(COMPONENTS).getAsJsonArray().get(0).getAsJsonObject().get(LONG_ADDRESS).getAsString().toLowerCase());
                            if (geoCity != null)
                                // Ciriè/e
                                city = geoCity;
                        }
                    } catch (MalformedURLException excpetion) {
                        excpetion.printStackTrace();
                    }

                sortedSlots = sortSlots(new LinkedList<>(Arrays.asList(Slot.values())), true, ((!inferredCity) ? city : null), location.get(LATITUDE).getAsString(), location.get(LONGITUDE).getAsString());

                if (!sortedSlots.isEmpty()) {
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

                    // SortedSlots (1 - 7)
                    // sortedSlots already set
                    // sortedSlots = sortedSlots.subList(1, sortedSlots.size());

                    // UnspecifiedSlot (8)
                    unspecifiedSlot = true;
                }
            } /*else
                    errors.add("Error(" + "NoSlotFound" + ")");
                    */
        }
        /* Else, if the user communicate just the city, select the slots nearer to s/he position */
        else if (city != null) {
            // Retains only the slots in the city specified by the user and sort them by the distance from the current position if the user
            sortedSlots = sortSlots(Arrays.asList(Slot.values()), true, city, userPositions[0], userPositions[1]);

            if (!sortedSlots.isEmpty()) {
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
                // SortedSlots (1 - 7)
                // sortedSlots already set
                // sortedSlots = sortedSlots.subList(1, sortedSlots.size());
                // UnspecifiedSlot (8)
                unspecifiedSlot = true;
            } /*else
                    errors.add("Error(" + "NoSlotFound" + ")");
                    */
        }
        /* Otherwise, select the nearest slots to the current position of th user if s/he indicate that wants to start from there */
        else if (hereAnswer) {
            // Retrive the address and the city of the current user position
            try {
                JsonObject currentUserAddress = parser.parse(getGoogleMapsResponseJSON(getMapsReverseGeocodingURL(currentUserPosition, GEOCODING_LOCALITY), false, "")).getAsJsonObject();
                if (currentUserAddress != null && currentUserAddress.get("status").getAsString().equals("OK") && currentUserAddress.get(RESULTS).getAsJsonArray().size() > 0) {
                    JsonObject bestResult = currentUserAddress.get(RESULTS).getAsJsonArray().get(0).getAsJsonObject();
                    address = bestResult.get(COMPLETE_ADDRESS).getAsString();
                    city = City.getByName(bestResult.get(COMPONENTS).getAsJsonArray().get(0).getAsJsonObject().get(LONG_ADDRESS).getAsString());
                    // Do not ask confirmation about the city, the user does not care
                    inferredCity = false;
                }
            } catch (MalformedURLException excpetion) {
                excpetion.printStackTrace();
            }

            // Do not filter by city, the user did not give that
            sortedSlots = sortSlots(Arrays.asList(Slot.values()), true, null, userPositions[0], userPositions[1]);

            if (!sortedSlots.isEmpty()) {
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
                // SortedSlots (1 - 7)
                // sortedSlots already set
                // sortedSlots = sortedSlots.subList(0, sortedSlots.size());
                // UnspecifiedSlot(8)
                unspecifiedSlot = true;
            }
        }

        // TODO endSlot = startSLot if not stated otherwise
//        if (!isStart && slots != null && slots.isEmpty() && !oldInformation.get(START_SLOT).equalsIgnoreCase(NONE)) {
//            slots = Arrays.asList(Slot.getByName(oldInformation.getOrDefault(START_SLOT, "")));
//        }

        log.info("Final " + ((isStart) ? "Start" : "End") + " City : " + city);
        log.info("Final " + ((isStart) ? "Start" : "End") + " Address: " + address);
        log.info("Final " + ((isStart) ? "Start" : "End") + " Slots: " + slots);

        if (!sortedSlots.isEmpty()) {
            newInformation.put((isStart) ? START_SLOT : END_SLOT, sortedSlots.get(0).getName()); // 1
            newInformation.put((isStart) ? START_SORTED_SLOTS : END_SORTED_SLOTS, sortedSlots.subList(1, sortedSlots.size()).stream().map(Slot::toString).collect(Collectors.joining(",")));  // 7
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
            if (!information.get(START_CITY).equals(NONE) && !information.get(START_SLOT).equals(NONE) &&
                    (!information.getOrDefault(START_SLOT, NONE).equals(oldInformation.get(START_SLOT)) ||
                            !information.getOrDefault(START_CITY, NONE).equals(oldInformation.get(START_CITY)))) {
                String nominatimResponse = getNominatimJSON(information.get(START_SLOT) + " " + information.get(START_CITY));

                JsonArray locations = (JsonArray) parser.parse(nominatimResponse);
                if (locations.size() > 0) {
                    information.put(START_LAT, locations.get(0).getAsJsonObject().get(LATITUDE).getAsString());
                    information.put(START_LON, locations.get(0).getAsJsonObject().get(LONGITUDE).getAsString());
                }
                waitBetweenRequests = true;
            }
            if (!information.get(END_CITY).equals(NONE) && !information.get(END_SLOT).equals(NONE) &&
                    (!information.getOrDefault(END_SLOT, NONE).equals(oldInformation.get(END_SLOT)) ||
                            !information.getOrDefault(END_CITY, NONE).equals(oldInformation.get(END_CITY)))) {
                if (waitBetweenRequests) {
                    try {
                        Thread.sleep(NOMINATIM_TIMEOUT);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                String nominatimResponse = getNominatimJSON(information.get(END_SLOT) + " " + information.get(END_CITY));

                JsonArray locations = (JsonArray) parser.parse(nominatimResponse);
                if (locations.size() > 0) {
                    information.put(END_LAT, locations.get(0).getAsJsonObject().get(LATITUDE).getAsString());
                    information.put(END_LON, locations.get(0).getAsJsonObject().get(LONGITUDE).getAsString());
                }
            }
            */
    }

    private static List<Slot> sortSlots(List<Slot> slots, boolean filterDistance, City filterCity, String biasLatitude, String biasLongitude) {
        Stream<Slot> sortedSlotsStream = slots.stream();

        // Filter by distance from the specified start point (if the slot was not given)
        if (filterDistance)
            sortedSlotsStream = sortedSlotsStream.filter(s -> {
                double latDiff = Double.parseDouble(biasLatitude) - s.getLatitude();
                double lonDiff = Double.parseDouble(biasLongitude) - s.getLongitude();
                return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff) < DISTANCE_THRESHOLD;
            });

        // ... and by city, if any was given;
        if (filterCity != null)
            sortedSlotsStream = sortedSlotsStream.filter(s -> s.getCity().name().equalsIgnoreCase(filterCity.getName()));

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
            if (userCurrentPositionObject.get("status").getAsString().equals("OK"))
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
