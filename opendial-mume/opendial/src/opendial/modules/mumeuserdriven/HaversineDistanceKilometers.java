package opendial.modules.mumeuserdriven;

public class HaversineDistanceKilometers {
    public static final double R = 6372.8; // In kilometers

    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.pow(Math.sin(dLon / 2), 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return R * c;
    }

    public static void main(String[] args) {
        System.out.println(haversine(44.50, 7.25, 45.50, 8.00)); // 126 kilometers
        System.out.println(haversine(44.50, 7.25, 44.50, 8.00)); // h = 60 kilometers
        System.out.println(haversine(44.50, 7.25, 45.50, 7.25)); // b = 111 kilometers
        System.out.println(haversine(44.50, 7.25, 44.515, 7.265)); // 0.1|0.1 => ~1.5 kilometers
    }
}
