package opendial.modules.mumedefault.information;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

import static opendial.modules.mume.config.Shared.CASES;
import static opendial.modules.mume.information.DurationInfo.DURATIONS_SEPARATOR;

public class TimeInfo {
    public static final int QUARTER = 15;
    static final int HOUR = 60;
    static final int MINUTES = 60;
    static final int HOUR_DAY = 24;

    private enum TemporalModifier {
        MORNING("mattina", "mattino"),
        EVENING("pomeriggio", "sera", "notte", "stasera");

        private Set<String> texts;

        TemporalModifier(String... t) {
            this.texts = new HashSet<>(Arrays.asList(t));
        }
    }

    private String time;
    private List<IndexedWord> wordList;

    // private List<CoreLabel> tokenList;
    // private int beginCharIndex;
    // private int endCharIndex;
    private String caseType;
    private List<IndexedWord> governors;
    private IndexedWord firstVerbGovernorWord;
    // private CoreLabel firstVerbGovernorToken;
    private boolean inLetters;
    public boolean isStart;
    public boolean isEnd;
    private TemporalModifier temporalMod;

    public TimeInfo(List<IndexedWord> ner, List<CoreLabel> tokens, SemanticGraph dependencies) {
        /* IMPORTANT: IndexedWord.index() and TokensAnnotation's index starts from 1 */
        time = tokens.get(ner.get(0).index() - 1).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class).split("T")[1];

        wordList = ner;
        /* IMPORTANT: IndexedWord.index() and TokensAnnotation's index starts from 1 */
        // tokenList = ner.stream().map(w -> tokens.get(w.index() - 1)).collect(Collectors.toList());

        // beginCharIndex = ner.get(0).beginPosition();
        // endCharIndex = ner.get(ner.size() - 1).endPosition();

        governors = ner.stream().filter(p -> !dependencies.getParents(p).isEmpty()).map(dependencies::getParent).collect(Collectors.toList());

        List<IndexedWord> pathToRoot = dependencies.getPathToRoot(ner.get(0));
        Iterator<IndexedWord> pathToRootIterator = pathToRoot.iterator();
        boolean verbFound = false;
        while (!verbFound && pathToRootIterator.hasNext()) {
            IndexedWord currentParent = pathToRootIterator.next();
            /* IMPORTANT: IndexedWord.index() and TokensAnnotation's index starts from 1 */
            CoreLabel currentToken = tokens.get(currentParent.index() - 1);
            if (currentToken.get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("V") ||
                    currentToken.get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("V+")) {
                verbFound = true;
                firstVerbGovernorWord = currentParent;
                // firstVerbGovernorToken = currentToken;
            }
        }
        if (!verbFound) {
            Iterator<IndexedWord> secondPathToRootIterator = pathToRoot.iterator();
            boolean modalFound = false;
            while (!modalFound && secondPathToRootIterator.hasNext()) {
                IndexedWord currentParent = secondPathToRootIterator.next();
                /* IMPORTANT: IndexedWord.index() and TokensAnnotation's index starts from 1 */
                CoreLabel currentToken = tokens.get(currentParent.index() - 1);
                String currentTokenPOS = currentToken.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                if (currentTokenPOS.equals("VM") || currentTokenPOS.equals("VA")) {
                    modalFound = true;
                    firstVerbGovernorWord = currentParent;
                    // firstVerbGovernorToken = currentToken;
                }
            }
        }

        boolean caseFound = false;
        Iterator<IndexedWord> wordIterator = ner.iterator();
        while (!caseFound && wordIterator.hasNext()) {
            IndexedWord currentWord = wordIterator.next();
            List<Pair<GrammaticalRelation, IndexedWord>> childList = dependencies.childPairs(currentWord);
            Iterator<Pair<GrammaticalRelation, IndexedWord>> childIteraor = childList.iterator();
            while (!caseFound && childIteraor.hasNext()) {
                Pair<GrammaticalRelation, IndexedWord> currentChild = childIteraor.next();
                if (currentChild.first.getShortName().equals("case")) {
                    caseFound = true;
                    caseType = currentChild.second.originalText();
                }
            }
        }
        if (!caseFound) {
            int firstIndex = ner.get(0).index();
            /* IMPORTANT: IndexedWord.index() and TokensAnnotation's index starts from 1 */
            if (firstIndex > 1 && firstIndex < dependencies.size() &&
                    CASES.contains(dependencies.getNodeByIndexSafe(firstIndex - 1).originalText())) {
                caseFound = true;
                caseType = dependencies.getNodeByIndexSafe(firstIndex - 1).originalText();
            }
        }
        if (!caseFound) caseType = "";

        this.inLetters = true;
        String words = ner.stream().map(IndexedWord::originalText).collect(Collectors.joining(" "));
        for (int i = 0; i < words.length() && this.inLetters; i++)
            if (Character.isDigit(words.charAt(i)))
                this.inLetters = false;

        isStart = false;
        isEnd = false;

        temporalMod = null;
        for (int i = 0; i < TemporalModifier.values().length && temporalMod == null; i++) {
            TemporalModifier mod = TemporalModifier.values()[i];
            for (IndexedWord child : dependencies.getChildren(ner.get(0))) {
                if (mod.texts.contains(child.originalText()))
                    temporalMod = mod;
            }
        }
    }

    public static String roundToPreviousQuarter(String time) {
        String[] timeFields = time.split(":");
        return String.format("%s:%02d", timeFields[0], ((Integer.parseInt(timeFields[1]) / QUARTER) * QUARTER));
    }

    public static String roundToNextQuarter(String endTimeDate) {
        String[] timeDate = endTimeDate.split(DURATIONS_SEPARATOR);
        String[] timeFields = timeDate[1].split(":");
        int m = Integer.parseInt(timeFields[1]);
        int newMinutes = ((m / QUARTER) * QUARTER);
        if (m % QUARTER != 0)
            newMinutes += QUARTER;
        int newHour = Integer.parseInt(timeFields[0]);
        String date = timeDate[0];
        if (newMinutes >= HOUR) {
            newMinutes = newMinutes % HOUR;
            // Increment hour
            newHour++;
            if (newHour >= HOUR_DAY) {
                newHour = newHour % HOUR_DAY;
                // Increment day
                date = DateInfo.addOneDay(date);
            }
        }
        return String.format("%02d:%02d" + DURATIONS_SEPARATOR + date, newHour, newMinutes);
    }

    public String getWords() {
        return wordList.stream().map(IndexedWord::originalText).collect(Collectors.joining(" "));
    }

    List<IndexedWord> getGovernors() {
        return governors;
    }

    public String getTime() {
        return time;
    }

    public String getCaseType() {
        return caseType;
    }

    public String getFirstVerbGovernorLemma() {
        if (firstVerbGovernorWord != null)
            return firstVerbGovernorWord.lemma().toLowerCase();
        return "";
    }

    public boolean isGovernorOf(DateInfo date) {
        for (IndexedWord governorWord : date.getGovernors())
            if (wordList.contains(governorWord))
                return true;
        return false;
    }

    public boolean isInLetters() {
        return inLetters;
    }

    public boolean hasMorningSpecification() {
        return temporalMod != null && temporalMod.equals(TemporalModifier.MORNING);
    }

    public boolean hasEveningSpecification() {
        return temporalMod != null && temporalMod.equals(TemporalModifier.EVENING);
    }

    public void roundToPreviousQuarter() {
        String[] timeFields = time.split(":");
        time = String.format("%s:%02d", timeFields[0], ((Integer.parseInt(timeFields[1]) / QUARTER) * QUARTER));
    }

    public void roundToNextQuarter(DateInfo date) {
        String[] timeFields = time.split(":");
        int m = Integer.parseInt(timeFields[1]);
        int newMinutes = ((m / QUARTER) * QUARTER);
        if (m % QUARTER != 0)
            newMinutes += QUARTER;
        int newHour = Integer.parseInt(timeFields[0]);
        if (newMinutes >= HOUR) {
            newMinutes = newMinutes % HOUR;
            // Increment hour
            newHour++;
            if (date != null && newHour >= HOUR_DAY) {
                newHour = newHour % HOUR_DAY;
                // Increment day
                date.addOneDay();
            }
        }
        time = String.format("%02d:%02d", newHour, newMinutes);
    }

    private void addHours(int n, DateInfo date) {
        String[] timeFields = time.split(":");
        int newHour = Integer.parseInt(timeFields[0]) + n;
        if (newHour >= HOUR_DAY) {
            date.addDays(newHour / HOUR_DAY);
            newHour = newHour % HOUR_DAY;
        }
        time = String.format("%02d:%s", newHour, timeFields[1]);
    }

    private void addMinutes(int n, DateInfo date) {
        String[] timeFields = time.split(":");
        int m = Integer.parseInt(timeFields[1]);
        int newMinutes = m + n;
        int newHour = Integer.parseInt(timeFields[0]);
        if (newMinutes >= HOUR) {
            newHour = newHour + (newMinutes / HOUR);
            newMinutes = newMinutes % HOUR;
            if (newHour >= HOUR_DAY) {
                date.addDays(newHour / HOUR_DAY);
                newHour = newHour % HOUR_DAY;
            }
        }
        time = String.format("%02d:%02d", newHour, newMinutes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeInfo)) return false;

        TimeInfo timeInfo = (TimeInfo) o;

        if (!time.equals(timeInfo.time)) return false;
        return wordList.equals(timeInfo.wordList);
    }

    @Override
    public int hashCode() {
        int result = time.hashCode();
        result = 31 * result + wordList.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "TimeInfo{" +
                "time='" + time + '\'' +
                ", wordList=" + wordList +
                ", caseType='" + caseType + '\'' +
                ", governors=" + governors +
                ", firstVerbGovernorWord='" + getFirstVerbGovernorLemma() + '\'' +
                ", inLetters=" + inLetters +
                ", isStart=" + isStart +
                ", isEnd=" + isEnd +
                '}';
    }
}
