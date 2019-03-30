package opendial.modules.mume;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static opendial.modules.mume.config.Shared.*;

class VehicleTypeExtractor {
    private static VehicleTypeExtractor extractor = null;

    private VehicleTypeExtractor() {}

    static VehicleTypeExtractor getInstance() {
        if (extractor == null)
            extractor = new VehicleTypeExtractor();
        return extractor;
    }

    /**
     * Extracts vehicle type information from the (corrected) user utterance.
     *
     * @param userUtterance  the current (corrected) String user utterance
     * @param information    Map<String, String> that will contains the updated information
     * @param oldInformation the Map<String, String> that contains the old information
     */
    void extractVehicleType(String userUtterance, Map<String, String> information, Map<String, String> oldInformation) {
        String vehicleType = oldInformation.get(VEHICLE_TYPE);
        if (vehicleType.equals(NONE))
            vehicleType = "economy";

        boolean specialTypeFound = false;
        Iterator<Map.Entry<String, List<String>>> pairIterator = VEHICLE_TYPES.entrySet().iterator();
        while (!specialTypeFound && pairIterator.hasNext()) {
            Map.Entry<String, List<String>> pair = pairIterator.next();
            Iterator<String> typeSynonymsIterator = pair.getValue().iterator();
            while (!specialTypeFound && typeSynonymsIterator.hasNext()) {
                String typeSynonym = typeSynonymsIterator.next();
                if (userUtterance.contains(typeSynonym)) {
                    information.put(VEHICLE_TYPE, pair.getKey());
                    specialTypeFound = true;
                }
            }
        }
        if (!specialTypeFound)
            information.put(VEHICLE_TYPE, vehicleType);
    }

}
