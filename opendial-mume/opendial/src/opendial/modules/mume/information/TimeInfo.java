package opendial.modules.mume.information;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.Pair;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class TimeInfo {
    private String time;
    private List<IndexedWord> wordList;
    // private List<CoreLabel> tokenList;
    // private int beginCharIndex;
    // private int endCharIndex;
    private String caseType;
    private List<IndexedWord> governors;
    private IndexedWord firstVerbGovernorWord;
    // private CoreLabel firstVerbGovernorToken;
    private boolean inDigits;
    public boolean isStart;
    public boolean isEnd;

    private TimeInfo() {
    }

    public TimeInfo(List<IndexedWord> ner, List<CoreLabel> tokens, SemanticGraph dependencies) {
        /* IMPORTANT: there is a shift of +1 between IndexedWord.index() and TokensAnnotation's index */
        time = tokens.get(ner.get(0).index() - 1).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class).split("T")[1];

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

        this.inDigits = false;
        String words = ner.stream().map(IndexedWord::originalText).collect(Collectors.joining(" "));
        for (int i = 0; i < words.length(); i++)
            if (Character.isDigit(words.charAt(i))) {
                this.inDigits = true;
                break;
            }

        isStart = false;
        isEnd = false;
    }

    public static TimeInfo generateFromDuration(TimeInfo time, DurationInfo dur) {
        TimeInfo toReturn = new TimeInfo();
        String[] split = time.time.split(":");
        int oldHour = Integer.parseInt(split[0]);
        int duration = Integer.parseInt(dur.getDuration().substring(0, dur.getDuration().length() - 1));
        /* This has to be done at the end of the main extraction method */
        // boolean incrementDay = false;
        int newHour = oldHour + duration;
        toReturn.time = String.valueOf(newHour) + ":" + split[1];
        toReturn.wordList = dur.getWordList();
        toReturn.caseType = dur.getCaseType();
        toReturn.governors = dur.getGovernors();
        toReturn.firstVerbGovernorWord = dur.getFirstVerbGovernorWord();
        toReturn.isStart = false;
        toReturn.isEnd = false;

        return toReturn;
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
            return firstVerbGovernorWord.lemma();
        return "";
    }

    public boolean isGovernorOf(DateInfo date) {
        for (IndexedWord governorWord : date.getGovernors())
            if (wordList.contains(governorWord))
                return true;
        return false;
    }

    public boolean isInDigits() {
        return inDigits;
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
                ", firstVerbGovernorWord=" + firstVerbGovernorWord +
                ", isStart=" + isStart +
                ", isEnd=" + isEnd +
                '}';
    }
}
