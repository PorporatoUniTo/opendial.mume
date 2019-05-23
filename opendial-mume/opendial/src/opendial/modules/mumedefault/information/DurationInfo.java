package opendial.modules.mumedefault.information;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.Pair;

import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static opendial.modules.mumedefault.config.Shared.log;
import static opendial.modules.mumedefault.information.DateInfo.YEAR_MONTH_NUMBER;
import static opendial.modules.mumedefault.information.TimeInfo.*;

public class DurationInfo {
    public static String DURATIONS_SEPARATOR = "@";
    private static Pattern durationComponentsPattern = Pattern.compile("[+-]?\\d+(?: .\\d+)?\\w");

    //private String duration;
    private double years;
    private double months;
    private double days;
    private double hours;
    private double minutes;
    private List<IndexedWord> wordList;
    // private List<CoreLabel> tokenList;
    // private int beginCharIndex;
    // private int endCharIndex;
    private String caseType;
    private List<IndexedWord> governors;
    private IndexedWord firstVerbGovernorWord;
    // private CoreLabel firstVerbGovernorToken;
    public boolean isStart;
    public boolean isEnd;
    /* From now or from start (i.e. has 'dopo' specification) */
    private boolean after;

    private DurationInfo() {
    }

    public DurationInfo(List<IndexedWord> ner, List<CoreLabel> tokens, SemanticGraph dependencies) {
        years = 0;
        months = 0;
        days = 0;
        hours = 0;
        minutes = 0;
        String[] durationParts = tokens.get(ner.get(0).index() - 1).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class).split("T");
        Matcher matcher = durationComponentsPattern.matcher(durationParts[0]);
        while (matcher.find()) {
            String part = matcher.group();
            char type = part.charAt(part.length() - 1);
            double dur = Double.parseDouble(part.substring(0, part.length() - 1));
            switch (type) {
                case 'Y':
                    years = dur;
                    break;
                case 'M':
                    months = dur;
                    break;
                case 'W':
                    days += dur * 7;
                    break;
                case 'D':
                    days += dur;
            }
        }
        // If the duration comprehend hour and minutes oarts, extract them
        if (durationParts.length > 1) {
            matcher = durationComponentsPattern.matcher(durationParts[1]);
            while (matcher.find()) {
                String part = matcher.group();
                char type = part.charAt(part.length() - 1);
                double dur = Double.parseDouble(part.substring(0, part.length() - 1));
                switch (type) {
                    case 'H':
                        hours = dur;
                        break;
                    case 'M':
                        minutes += dur;
                        break;
                    case 'S':
                        minutes += dur / MINUTES;
                }
            }
        }

        wordList = ner;
        /* IMPORTANT: IndexedWord.index() and TokensAnnotation's index starts from 1 */
        // tokenList = ner.stream().map(w -> tokens.get(w.index() - 1)).collect(Collectors.toList());

        // beginCharIndex = ner.get(0).beginPosition();
        // endCharIndex = ner.get(ner.size() - 1).endPosition();

        governors = ner.stream().map(dependencies::getParent).collect(Collectors.toList());

        List<IndexedWord> pathToRoot = dependencies.getPathToRoot(ner.get(0));
        Iterator<IndexedWord> pathToRootIterator = pathToRoot.iterator();
        boolean verbFound = false;
        while (!verbFound && pathToRootIterator.hasNext()) {
            IndexedWord currentParent = pathToRootIterator.next();
            /* IMPORTANT: IndexedWord.index() and TokensAnnotation's index starts from 1 */
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
        if (!caseFound) caseType = "";

        isStart = false;
        isEnd = false;

        after = ner.stream().flatMap(w -> dependencies.getChildren(w).stream()).anyMatch(w -> w.originalText().matches("[Dd]opo")) ||
                ner.stream().filter(w -> dependencies.getParent(w) != null).map(w -> dependencies.getParent(w)).anyMatch(w -> w.originalText().matches("[Dd]opo"));
    }

    public static void split(DurationInfo duration, List<DurationInfo> timeDurations, List<DurationInfo> dateDurations) {
        DurationInfo newTimeDur = new DurationInfo();
        DurationInfo newDateDur = new DurationInfo();
        newDateDur.years = duration.years;
        newDateDur.months = duration.months;
        newDateDur.days = duration.days;
        newTimeDur.hours = duration.hours;
        newTimeDur.minutes = duration.minutes;

        newTimeDur.wordList = duration.wordList;
        newDateDur.wordList = duration.wordList;
        newTimeDur.governors = duration.governors;
        newDateDur.governors = duration.governors;
        newTimeDur.firstVerbGovernorWord = duration.firstVerbGovernorWord;
        newDateDur.firstVerbGovernorWord = duration.firstVerbGovernorWord;
        newTimeDur.isStart = duration.isStart;
        newDateDur.isStart = duration.isStart;
        newTimeDur.isEnd = duration.isEnd;
        newDateDur.isEnd = duration.isEnd;
        newTimeDur.after = duration.after;
        newDateDur.after = duration.after;

        timeDurations.add(newTimeDur);
        dateDurations.add(newDateDur);
    }

    public double getYears() {
        return years;
    }

    public double getMonths() {
        return months;
    }

    public double getDays() {
        return days;
    }

    public double getHours() {
        return hours;
    }

    public double getMinutes() {
        return minutes;
    }

    public List<IndexedWord> getWordList() {
        return wordList;
    }

    public String getCaseType() {
        return caseType;
    }

    public List<IndexedWord> getGovernors() {
        return governors;
    }

    public String getFirstVerbGovernorLemma() {
        if (firstVerbGovernorWord != null)
            return firstVerbGovernorWord.lemma().toLowerCase();
        return "";
    }

    public IndexedWord getFirstVerbGovernorWord() {
        return firstVerbGovernorWord;
    }

    public boolean hasAfterSpecification() {
        return after;
    }

    public String toDateAndTime(String startTime, String startDate) {
        String[] timeFields = startTime.split(":");
        int[] newTimeFields = new int[2];
        for (int i = 0; i < timeFields.length; i++)
            newTimeFields[i] = Integer.parseInt(timeFields[i]);
        String[] dateFields = startDate.split("-");
        int[] newDateFields = new int[3];
        for (int i = 0; i < dateFields.length; i++)
            newDateFields[i] = Integer.parseInt(dateFields[i]);
        newTimeFields[1] += minutes;
        if (newTimeFields[1] >= HOUR) {
            newTimeFields[0] += newTimeFields[1] / HOUR;
            newTimeFields[1] = newTimeFields[1] % HOUR;
        }
        newTimeFields[0] += hours;
        if (newTimeFields[0] > HOUR_DAY) {
            newDateFields[2] += newTimeFields[0] / HOUR_DAY;
            newTimeFields[0] = newTimeFields[0] % HOUR_DAY;
        }
        newDateFields[2] += days;
        int monthDay = DateInfo.getMonthDaysNumber(newDateFields[1]);
        while (newDateFields[2] > monthDay) {
            newDateFields[1]++;
            newDateFields[2] -= monthDay;
            monthDay = DateInfo.getMonthDaysNumber(newDateFields[1]);
        }
        newDateFields[1] += months;
        if (newDateFields[1] > YEAR_MONTH_NUMBER) {
            newDateFields[0] += newDateFields[1] / YEAR_MONTH_NUMBER;
            newDateFields[1] = newDateFields[1] % YEAR_MONTH_NUMBER;
        }
        newDateFields[0] += years;

        log.warning(String.format("%02d:%02d" + DURATIONS_SEPARATOR + "%04d-%02d-%02d", newTimeFields[0], newTimeFields[1], newDateFields[0], newDateFields[1], newDateFields[2]));

        return String.format("%02d:%02d" + DURATIONS_SEPARATOR + "%04d-%02d-%02d", newTimeFields[0], newTimeFields[1], newDateFields[0], newDateFields[1], newDateFields[2]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DurationInfo)) return false;

        DurationInfo that = (DurationInfo) o;

        if (Double.compare(that.getYears(), getYears()) != 0) return false;
        if (Double.compare(that.getMonths(), getMonths()) != 0) return false;
        if (Double.compare(that.getDays(), getDays()) != 0) return false;
        if (Double.compare(that.getHours(), getHours()) != 0) return false;
        if (Double.compare(that.getMinutes(), getMinutes()) != 0) return false;
        return wordList.equals(that.wordList);

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(getYears());
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(getMonths());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(getDays());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(getHours());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(getMinutes());
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (getWordList() != null ? getWordList().hashCode() : 0);
        result = 31 * result + (getCaseType() != null ? getCaseType().hashCode() : 0);
        result = 31 * result + (getGovernors() != null ? getGovernors().hashCode() : 0);
        result = 31 * result + (getFirstVerbGovernorWord() != null ? getFirstVerbGovernorWord().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DurationInfo{" +
                "{years=" + years +
                ", months=" + months +
                ", days=" + days +
                ", hours=" + hours +
                ", minutes=" + minutes + "}" +
                ", wordList=" + wordList +
                ", caseType='" + caseType + '\'' +
                ", governors=" + governors +
                ", firstVerbGovernorWord=" + firstVerbGovernorWord +
                ", isStart=" + isStart +
                ", isEnd=" + isEnd +
                ", after=" + after +
                '}';
    }
}
