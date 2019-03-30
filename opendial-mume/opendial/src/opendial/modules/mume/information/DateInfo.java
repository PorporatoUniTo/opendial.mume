package opendial.modules.mume.information;

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

public class DateInfo {
    public static final int WEEK_DAY_NUMBER = 7;
    public static final int YEAR_MONTH_NUMBER = 12;

    private static final String PRESENT_REF = "PRESENT_REF";

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
        /* IMPORTANT: there is a shift of +1 between IndexedWord.index() and TokensAnnotation's index */
        String dateContent = tokens.get(ner.get(0).index() - 1).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
        if (dateContent.equals(PRESENT_REF)) {
            date = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);    // Convert to YYYY-MM-DD format any refernce about "now" by the user
            isNow = true;
        }
        else {
            date = dateContent.split("T")[0];
            isNow = false;
        }

        wordList = ner;
        /* IMPORTANT: there is a shift of +1 between IndexedWord.index() and TokensAnnotation's index */
        // tokenList = ner.stream().map(w -> tokens.get(w.index() - 1)).collect(Collectors.toList());

        // beginCharIndex = ner.get(0).beginPosition();
        // endCharIndex = ner.get(ner.size() - 1).endPosition();

        governors = ner.stream().map(dependencies::getParent).collect(Collectors.toList());

        List<IndexedWord> pathToRoot = dependencies.getPathToRoot(ner.get(0));
        Iterator<IndexedWord> pathToRootIterator = pathToRoot.iterator();
        boolean verbFound = false;
        while (!verbFound && pathToRootIterator.hasNext()) {
            IndexedWord currentParent = pathToRootIterator.next();
            /* IMPORTANT: there is a shift of +1 between IndexedWord.index() and TokensAnnotation's index */
            CoreLabel currentToken = tokens.get(currentParent.index() - 1);
            if (currentToken.get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("V")) {
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
                /* IMPORTANT: there is a shift of +1 between IndexedWord.index() and TokensAnnotation's index */
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
        if (!caseFound) caseType = "";

        weekDay = "";
        if (ner.stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")).toLowerCase().contains("luned"))
            weekDay = "lunedì";
        else if (ner.stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")).toLowerCase().contains("marted"))
            weekDay = "martedì";
        else if (ner.stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")).toLowerCase().contains("mercoled"))
            weekDay = "mercoledì";
        else if (ner.stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")).toLowerCase().contains("gioved"))
            weekDay = "giovedì";
        else if (ner.stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")).toLowerCase().contains("venerd"))
            weekDay = "venerdì";
        else if (ner.stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")).toLowerCase().contains("sabato"))
            weekDay = "sabato";
        else if (ner.stream().map(IndexedWord::originalText).collect(Collectors.joining(" ")).toLowerCase().contains("domenica"))
            weekDay = "domenica";

        isStart = false;
        isEnd = false;
    }

    public static int getMonthDaysNumber(int monthNum) {
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
                ", firstVerbGovernorWord=" + firstVerbGovernorWord +
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
                dateIntFields[2] = (dateIntFields[2] % monthDaysNumber) + 1;
                dateIntFields[1]++;
                if (dateIntFields[1] > YEAR_MONTH_NUMBER) {
                    dateIntFields[1] = (dateIntFields[1] % YEAR_MONTH_NUMBER) + 1;
                    dateIntFields[0]++;
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
