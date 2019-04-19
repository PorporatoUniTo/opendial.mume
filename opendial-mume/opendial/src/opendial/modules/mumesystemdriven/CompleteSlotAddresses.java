package opendial.modules.mumesystemdriven;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.StringJoiner;

import static opendial.modules.mumesystemdriven.Shared.*;

public class CompleteSlotAddresses {
    static private Properties localGoogleMapsAPIPropeties;
    static private final boolean TEST = true;

    public static void main(String[] args) {
        localGoogleMapsAPIPropeties = new Properties();
        try (BufferedReader googlePropertiesReader = new BufferedReader(new InputStreamReader(new FileInputStream(
                "D:\\University\\Borsa\\app\\CarPoolingInformationExtractionModel\\src\\opendial\\google-api.properties"
        )))) {
            localGoogleMapsAPIPropeties.load(googlePropertiesReader);
        } catch (FileNotFoundException fNFE) {
            System.out.println("The attemp to use non-local Google API properties has failed.");
            fNFE.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        JsonParser parser = new JsonParser();
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(new File("slot_completion_info.txt"))))) {
            for (Slot slot : Slot.values()) {
                if (!TEST) {
                    String slotLatLon = slot.getLatitude() + "," + slot.getLongitude();
                    JsonObject queryCompletionResult = parser.parse(getGoogleMapsResponseJSON(getMapsSearchURL(slot.getAddress() + ", " + slot.getCity(), slotLatLon))).getAsJsonObject();
                    String completeResults = "Arrays.asList(Collections.emptyList())";
                    if (queryCompletionResult != null && queryCompletionResult.get("status").getAsString().equals("OK") && queryCompletionResult.get("candidates").getAsJsonArray().size() > 0) {
                        StringJoiner aj = new StringJoiner(", ", "Arrays.asList(", ")");
                        for (JsonElement result : queryCompletionResult.get("candidates").getAsJsonArray())
                            aj.add("\"" + result.toString().replace("\"", "\\\"") + "\"");
                        completeResults = aj.toString();
                    }
                    JsonObject slotAddress = parser.parse(getGoogleMapsResponseJSON(getMapsReverseGeocodingURL(slotLatLon))).getAsJsonObject();
                    String geoResults = "Arrays.asList(Collections.emptyList())";
                    if (slotAddress != null && slotAddress.get("status").getAsString().equals("OK") && slotAddress.get("results").getAsJsonArray().size() > 0) {
                        StringJoiner aj = new StringJoiner(", ", "Arrays.asList(", ")");
                        for (JsonElement result : slotAddress.get("results").getAsJsonArray())
                            aj.add("\"" + result.toString().replace("\"", "\\\"") + "\"");
                        geoResults = aj.toString();
                    }

                    out.println("'" + slot.getName() + "'\n" +
                            completeResults + ", " + geoResults
                            + "\n");
                } else
                    System.out.println(slot.getAddresses());
            }
        } catch (NullPointerException | IOException exception) {
            exception.printStackTrace();
        }
    }

    private static String getGoogleMapsResponseJSON(URL service) {
        StringBuilder o = new StringBuilder();
        try {
            URLConnection geoConnection = service.openConnection();
            geoConnection.setDoOutput(false);
            geoConnection.setRequestProperty("Content-Type", "application/json;charset=" + StandardCharsets.UTF_8);
            BufferedReader in = new BufferedReader(new InputStreamReader(geoConnection.getInputStream(), StandardCharsets.UTF_8));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                o.append(inputLine);
            }
            in.close();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        return o.toString();
    }

    private static URL getMapsSearchURL(String input, String bias) throws MalformedURLException {
        return new URL(MAPS_SEARCH +
                KEY + localGoogleMapsAPIPropeties.getProperty("google.api.key") + "&" +
                INPUT + URLEncoder.encode(input, StandardCharsets.UTF_8) + "&" +
                INPUT_TYPE + "&" +
                LANGUAGE + "&" +
                FIELDS +
                ((bias.isEmpty()) ? "" : "&" + LOCATION_BIAS + bias));
    }

    private static URL getMapsReverseGeocodingURL(String latLng) throws MalformedURLException {
        return new URL(MAPS_GEOCODING +
                KEY + localGoogleMapsAPIPropeties.getProperty("google.api.key") + "&" +
                LAT_LNG + latLng + "&" +
                LANGUAGE);
    }
}
