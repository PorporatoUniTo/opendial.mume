package opendial.modules.mumedefault.information;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.Pair;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static opendial.modules.mumedefault.config.Shared.CASES;

public class DateInfo {
    public static final int WEEK_DAY_NUMBER = 7;
    public static final int YEAR_MONTH_NUMBER = 12;

    public static final String PRESENT_REF = "PRESENT_REF";

    private String date;
    private List<IndexedWord> wordList;
    // private List<CoreLabel> tokenList;
    // private int beginCharIndex;
    // private int endCharIndex;
    private String caseType;
    private List<IndexedWord> governors;
    private IndexedWord firstVerbGovernorWord;
    private String weekDay;
    // private CoreLabel firstVerbGovernorToken;
    public boolean isStart;
    public boolean isEnd;
    public boolean isNow;

    public DateInfo(List<IndexedWord> ner, List<CoreLabel> tokens, SemanticGraph dependencies) {
        /* IMPORTANT: IndexedWord.index() and TokensAnnotation's index starts from 1 */
        String dateContent = tokens.get(ner.get(0).index() - 1).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
        if (dateContent.equals(PRESENT_REF)) {
            date = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);    // Convert to YYYY-MM-DD format any refernce about "now" by the user
            isNow = true;
        } else {
            date = dateContent.split("T")[0];
            isNow = false;
        }

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
                if (currentToken.get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("VM")) {
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

        String dateString = ner.stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")).toLowerCase();
        weekDay = "";
        if (dateString.contains("luned"))
            weekDay = "lunedì";
        else if (dateString.contains("marted"))
            weekDay = "martedì";
        else if (dateString.contains("mercoled"))
            weekDay = "mercoledì";
        else if (dateString.contains("gioved"))
            weekDay = "giovedì";
        else if (dateString.contains("venerd"))
            weekDay = "venerdì";
        else if (dateString.contains("sabato"))
            weekDay = "sabato";
        else if (dateString.contains("domenica"))
            weekDay = "domenica";

        isStart = false;
        isEnd = false;
    }

    static String addOneDay(String date) {
        String[] dateFields = date.split("-");
        int[] dateIntFields = new int[3];
        try {
            for (int i = 0; i < dateFields.length; i++)
                dateIntFields[i] = Integer.parseInt(dateFields[i]);
        } catch (NumberFormatException ex) {
        }
        if (dateIntFields[0] > 0 && dateIntFields[1] > 0 && dateIntFields[2] > 0) {
            dateIntFields[2]++;
            int monthDaysNumber = getMonthDaysNumber(dateIntFields[1]);
            if (dateIntFields[2] > monthDaysNumber) {
                dateIntFields[2] = dateIntFields[2] % monthDaysNumber;
                dateIntFields[1]++;
                if (dateIntFields[1] > YEAR_MONTH_NUMBER) {
                    dateIntFields[1] = dateIntFields[1] % YEAR_MONTH_NUMBER;
                    dateIntFields[0]++;
                }
            }
        }
        return String.format("%04d-%02d-%02d", dateIntFields[0], dateIntFields[1], dateIntFields[2]);
    }

    static int getMonthDaysNumber(int monthNum) {
        switch (monthNum) {
            case 11:
            case 4:
            case 6:
            case 9:
                return 30;
            case 2:
                return 28;
            default:
                return 31;
        }
    }

    List<IndexedWord> getGovernors() {
        return governors;
    }

    public String getDate() {
        return date;
    }

    @Override
    public String toString() {
        return "DateInfo{" +
                "date='" + date + '\'' +
                ", wordList=" + wordList +
                ", caseType='" + caseType + '\'' +
                ", governors=" + governors +
                ", firstVerbGovernorWord='" + getFirstVerbGovernorLemma() + '\'' +
                ", weekDay='" + weekDay + '\'' +
                ", isStart=" + isStart +
                ", isEnd=" + isEnd +
                '}';
    }

    public String getCaseType() {
        return caseType;
    }

    public String getFirstVerbGovernorLemma() {
        if (firstVerbGovernorWord != null)
            return firstVerbGovernorWord.lemma().toLowerCase();
        return "";
    }

    public boolean isGovernorOf(TimeInfo time) {
        for (IndexedWord governorWord : time.getGovernors())
            if (wordList.contains(governorWord))
                return true;
        return false;
    }

    public String getWeekDay() {
        return weekDay;
    }

    void addOneDay() {
        String[] dateFields = date.split("-");
        int[] dateIntFields = new int[3];
        try {
            for (int i = 0; i < dateFields.length; i++)
                dateIntFields[i] = Integer.parseInt(dateFields[i]);
        } catch (NumberFormatException ex) {
        }
        if (dateIntFields[0] > 0 && dateIntFields[1] > 0 && dateIntFields[2] > 0) {
            dateIntFields[2]++;
            int monthDaysNumber = getMonthDaysNumber(dateIntFields[1]);
            if (dateIntFields[2] > monthDaysNumber) {
                dateIntFields[2] = dateIntFields[2] % monthDaysNumber;
                dateIntFields[1]++;
                if (dateIntFields[1] > YEAR_MONTH_NUMBER) {
                    dateIntFields[1] = dateIntFields[1] % YEAR_MONTH_NUMBER;
                    dateIntFields[0]++;
                }
            }
        }
        date = String.format("%04d-%02d-%02d", dateIntFields[0], dateIntFields[1], dateIntFields[2]);
    }

    public void addOneWeek() {
        String[] dateFields = date.split("-");
        int[] dateIntFields = new int[3];
        try {
            for (int i = 0; i < dateFields.length; i++)
                dateIntFields[i] = Integer.parseInt(dateFields[i]);
        } catch (NumberFormatException ex) {
        }
        if (dateIntFields[0] > 0 && dateIntFields[1] > 0 && dateIntFields[2] > 0) {
            dateIntFields[2] += WEEK_DAY_NUMBER;
            int monthDaysNumber = getMonthDaysNumber(dateIntFields[1]);
            if (dateIntFields[2] > monthDaysNumber) {
                dateIntFields[2] = dateIntFields[2] % monthDaysNumber;
                dateIntFields[1]++;
                if (dateIntFields[1] > YEAR_MONTH_NUMBER) {
                    dateIntFields[1] = dateIntFields[1] % YEAR_MONTH_NUMBER;
                    dateIntFields[0]++;
                }
            }
        }
        date = String.format("%04d-%02d-%02d", dateIntFields[0], dateIntFields[1], dateIntFields[2]);
    }

    void addDays(int n) {
        String[] dateFields = date.split("-");
        int[] dateIntFields = new int[3];
        try {
            for (int i = 0; i < dateFields.length; i++)
                dateIntFields[i] = Integer.parseInt(dateFields[i]);
        } catch (NumberFormatException ex) {
        }
        if (dateIntFields[0] > 0 && dateIntFields[1] > 0 && dateIntFields[2] > 0) {
            dateIntFields[2] += n;
            int monthDaysNumber = getMonthDaysNumber(dateIntFields[1]);
            if (dateIntFields[2] > monthDaysNumber) {
                dateIntFields[2] = dateIntFields[2] % monthDaysNumber;
                dateIntFields[1]++;
                if (dateIntFields[1] > YEAR_MONTH_NUMBER) {
                    dateIntFields[1]++;
                }
            }
        }
        date = String.format("%04d-%02d-%02d", dateIntFields[0], dateIntFields[1], dateIntFields[2]);
    }

    public void correctDayYearConfusion(ZonedDateTime now) {
        // The day of the month has been mistaken as an year by HidelTime:
        //  the user is (likely) telling that s/he wants to start the trip
        //  this year and this month on the day of the given date
        int newYear = now.getYear();
        int newDayOfMonth = Integer.parseInt(date.substring(2));
        int newMonth = now.getMonthValue();
        // If the day number has past fir the current month,
        //  the user likely is referring to the next month
        if (now.getDayOfMonth() > newDayOfMonth) {
            newMonth++;
            if (newMonth > YEAR_MONTH_NUMBER) {
                newMonth = (newMonth % YEAR_MONTH_NUMBER) + 1;
                newYear++;
            }
        }
        date = String.format("%04d-%02d-%02d", newYear, newMonth, newDayOfMonth);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DateInfo)) return false;

        DateInfo dateInfo = (DateInfo) o;

        if (!date.equals(dateInfo.date)) return false;
        return wordList.equals(dateInfo.wordList);
    }

    @Override
    public int hashCode() {
        int result = date.hashCode();
        result = 31 * result + wordList.hashCode();
        return result;
    }

}
