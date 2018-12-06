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

public class DateInfo {
    private String date;
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

    public DateInfo(List<IndexedWord> ner, List<CoreLabel> tokens, SemanticGraph dependencies) {
        /* IMPORTANT: there is a shift of +1 between IndexedWord.index() and TokensAnnotation's index */
        date = tokens.get(ner.get(0).index() - 1).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class).split("T")[0];

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

        isStart = false;
        isEnd = false;
    }

    List<IndexedWord> getGovernors() {
        return governors;
    }

    public String getDate() {
        return date;
    }

    public String getCaseType() {
        return caseType;
    }

    public String getFirstVerbGovernorLemma() {
        if (firstVerbGovernorWord != null)
            return firstVerbGovernorWord.lemma();
        return "";
    }

    public boolean isGovernorOf(TimeInfo time) {
        for (IndexedWord governorWord : time.getGovernors())
            if (wordList.contains(governorWord))
                return true;
        return false;
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

    @Override
    public String toString() {
        return "DateInfo{" +
                "date='" + date + '\'' +
                ", wordList=" + wordList +
                ", caseType='" + caseType + '\'' +
                ", governors=" + governors +
                ", firstVerbGovernorWord=" + firstVerbGovernorWord +
                ", isStart=" + isStart +
                ", isEnd=" + isEnd +
                '}';
    }
}
