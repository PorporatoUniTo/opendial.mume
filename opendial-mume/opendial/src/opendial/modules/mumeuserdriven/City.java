package opendial.modules.mumeuserdriven;

import edu.stanford.nlp.ling.IndexedWord;

import java.util.List;
import java.util.stream.Collectors;

public enum City {
    TORINO("Torino"),
    CASELLE("Caselle"),
    CHIERI("Chieri"),
    CIRIE("Cirié"),
    COLLEGNO("Collegno"),
    GRUGLIASCO("Grugliasco"),
    IVREA("Ivrea"),
    MONCALIERI("Moncalieri"),
    NICHELINO("Nichelino"),
    RIVALTA("Rivalta"),
    RIVOLI("Rivoli"),
    VINOVO("Vinovo");

    // private static Map<String, City> nameMap = new HashMap<>();

    private String name;
    private int length;

    City(String name) {
        this.name = name;
        this.length = name.split(" ").length;
    }

    public static City getByName(String name) {
        switch (name) {
            case "torino":
                return TORINO;
            case "caselle":
                return CASELLE;
            case "chieri":
                return CHIERI;
            case "cirié":
            case "cirie":
                return CIRIE;
            case "collegno":
                return COLLEGNO;
            case "grugliasco":
                return GRUGLIASCO;
            case "ivrea":
                return IVREA;
            case "moncalieri":
                return MONCALIERI;
            case "nichelino":
                return NICHELINO;
            case "rivalta":
                return RIVALTA;
            case "rivoli":
                return RIVOLI;
            case "vinovo":
                return VINOVO;
            default:
                return null;
        }
    }

    public static City extractFromAddress(List<IndexedWord> address) {
        for (City city : City.values())
            for (int i = 0; i < address.size(); i++)
                if (address.subList(i, i + city.length).stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")).equalsIgnoreCase(city.name))
                    return city;
        return null;
    }

    public static City extractFromAddress(String address) {
        for (City city : City.values()) {
            String lowerCaseAddress = address.toLowerCase();
            if (lowerCaseAddress.contains(" " + city.getName().toLowerCase() + " ") ||
                    lowerCaseAddress.contains(" " + city.getName().toLowerCase() + ","))
                return city;
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public int getNumberOfWords() {
        return length;
    }

    @Override
    public String toString() {
        return name;
    }
}
