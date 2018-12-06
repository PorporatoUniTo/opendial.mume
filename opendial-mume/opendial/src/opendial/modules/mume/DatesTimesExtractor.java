package opendial.modules.mume;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import opendial.modules.mume.information.DateInfo;
import opendial.modules.mume.information.DurationInfo;
import opendial.modules.mume.information.TimeInfo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static opendial.modules.mume.config.Shared.*;

class DatesTimesExtractor {

    private static DatesTimesExtractor extractor = null;

    private DatesTimesExtractor() {
    }

    static DatesTimesExtractor getInstance() {
        if (extractor == null)
            extractor = new DatesTimesExtractor();
        return extractor;
    }

    /**
     * Extracts time and date information from a (corrected) user utterance.
     *
     * @param annotatedUserUtterance the Annotation conteining the (corrected) user utterance
     * @param dateAnnotations        the List<Set<IndexedWord>> of recognised dates in annotatedUserUtterance
     * @param timeAnnotations        the List<Set<IndexedWord>> of recognised times in annotatedUserUtterance
     * @param information            Map<String, String> that will contains the updated information
     * @param machinePrevState       the previous state of the machine, needed if the user is answering a question from the system
     */
    void extractTimeAndDate(Annotation annotatedUserUtterance,
                            List<List<IndexedWord>> dateAnnotations,
                            List<List<IndexedWord>> timeAnnotations,
                            List<List<IndexedWord>> durationAnnotations,
                            Map<String, String> information,
                            String machinePrevState) {
        try {
            /* Retrieve information already known /
            String oldStartDate = oldInformation.get("startDate");
            String oldEndDate = oldInformation.get("endDate");
            String oldStartTime = oldInformation.get("startTime");
            String oldEndTime = oldInformation.get("endTime");
            */

            DateInfo newStartDate = null;
            DateInfo newEndDate = null;
            TimeInfo newStartTime = null;
            TimeInfo newEndTime = null;

            List<CoreLabel> tokens = annotatedUserUtterance.get(CoreAnnotations.TokensAnnotation.class);
            SemanticGraph dependencies = annotatedUserUtterance.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);

            List<DateInfo> dates = dateAnnotations.stream().map(a -> new DateInfo(a, tokens, dependencies)).collect(Collectors.toList());
            List<TimeInfo> times = timeAnnotations.stream().map(a -> new TimeInfo(a, tokens, dependencies)).collect(Collectors.toList());
            List<DurationInfo> durs = durationAnnotations.stream().map(a -> new DurationInfo(a, tokens, dependencies)).collect(Collectors.toList());

            log.info("Dates:\t" + dateAnnotations.toString());
            dates.forEach(d -> log.info(d.toString()));
            log.info("Times:\t" + timeAnnotations.toString());
            times.forEach(t -> log.info(t.toString()));
            log.info("Durations: " + durationAnnotations.toString());

            boolean doneCase = false;
            boolean doneVerbs = false;
            int cicleNumber = -1;

            do {
                do {
                    cicleNumber++;

                    /* Old values for checking for changes */
                    DateInfo previousStartDate = newStartDate;
                    DateInfo previousEndDate = newEndDate;
                    TimeInfo previousStartTime = newStartTime;
                    TimeInfo previousEndTime = newEndTime;

                    /* SEARCH FOR UNAMBIGUOUS TEMPORAL EXPRESSIONS (those that are unequivocally about the start or the end of the journey) */
                    /* DATES */
                    for (DateInfo date : dates) {
                        if (date.getCaseType() != null) {
                            /* "... da oggi...", "... dal 20 febbraio..." */
                            if (newStartDate == null && STRONG_START_DATE_CASE.contains(date.getCaseType())) {
                                newStartDate = date;
                                date.isStart = true;
                            }
                            /* "... fino a domani...", "... a domani...", "... al 21 febbraio..." */
                            else if (newEndDate == null && STRONG_END_DATE_CASE.contains(date.getCaseType())) {
                                newEndDate = date;
                                date.isEnd = true;
                            }
                            /* "... mi serve un'auto per domani..." */
                            else if (newStartDate == null && newEndDate == null && STRONG_SINGLE_DATE_CASE.contains(date.getCaseType())) {
                                newStartDate = date;
                                date.isStart = true;
                                newEndDate = date;
                                date.isEnd = true;
                            }
                        }
                    }

                    /* TIMES */
                    for (TimeInfo time : times) {
                        if (time.getCaseType() != null) {
                            /* "... dalle 14..." */
                            if (newStartTime == null && STRONG_START_TIME_CASE.contains(time.getCaseType())) {
                                newStartTime = time;
                                time.isStart = true;
                            }
                            /* "... fino alle 16..." */
                            else if (newEndTime == null && STRONG_END_TIME_CASE.contains(time.getCaseType())) {
                                newEndTime = time;
                                time.isEnd = true;
                            }
                        }

                    }

                    if (newEndTime == null && !durs.isEmpty() && newStartTime != null) {
                        newEndTime = TimeInfo.generateFromDuration(newStartTime, durs.get(0));
                        newEndTime.isEnd = true;
                    }

                    /* SEARCH FOR AMBIGUOUS TEMPORAL EXPRESSION (those which role dependes on other information in the sentence) */
                    /* DATES */
                    for (DateInfo date : dates) {
                        if (DEPENDANT_DATE_CASE.contains(date.getCaseType())) {
                            /* "... dal 20 febbraio... al 21..." */
                            if (newEndDate == null &&
                                    newStartDate != null && STRONG_START_DATE_CASE.contains(newStartDate.getCaseType())) {
                                newEndDate = date;
                                date.isEnd = true;
                            }
                            /* "... dalle 14 del 21 febbraio..." */
                            if (newStartDate == null &&
                                    newStartTime != null && newStartTime.isGovernorOf(date)) {
                                newStartDate = date;
                                date.isStart = true;
                            }
                            /* "... fino alle 19 del 22..." */
                            if (newEndDate == null &&
                                    newEndTime != null && newEndTime.isGovernorOf(date)) {
                                newEndDate = date;
                                date.isEnd = true;
                            }
                        }
                    }

                    /* TIMES */
                    for (TimeInfo time : times) {
                        if (DEPENDANT_TIME_CASE.contains(time.getCaseType())) {
                            /* "... dalle 15... alle 21..." */
                            if (newEndTime == null && !time.isStart &&
                                    newStartTime != null && STRONG_START_TIME_CASE.contains(newStartTime.getCaseType())) {
                                newEndTime = time;
                                time.isEnd = true;
                            }
                            /* "... da domani alle 14..." */
                            if (newStartTime == null && !time.isEnd &&
                                    newStartDate != null && newStartDate.isGovernorOf(time)) {
                                newStartTime = time;
                                time.isStart = true;
                            }
                            /* "... fino a dopodomani alle 9..." */
                            if (newEndTime == null && !time.isStart &&
                                    newEndDate != null && newEndDate.isGovernorOf(time)) {
                                newEndTime = time;
                                time.isEnd = true;
                            }
                        }
                    }

                    /* If no change occurred in the current iteration, exit */
                    if ((previousStartDate == null && newStartDate == null || previousStartDate != null && previousStartDate.equals(newStartDate)) &&
                            (previousEndDate == null && newEndDate == null || previousEndDate != null && previousEndDate.equals(newEndDate)) &&
                            (previousStartTime == null && newStartTime == null || previousStartTime != null && previousStartTime.equals(newStartTime)) &&
                            (previousEndTime == null && newEndTime == null || previousEndTime != null && previousEndTime.equals(newEndTime)))
                        doneCase = true;
                } while (!doneCase);

                /* Old values for checking for changes */
                DateInfo previousStartDate = newStartDate;
                DateInfo previousEndDate = newEndDate;
                TimeInfo previousStartTime = newStartTime;
                TimeInfo previousEndTime = newEndTime;

                /* After checking the presence of more significant indicator, check the verb for start or end clue */
                /* DATES */
                for (DateInfo date : dates) {
                    if (newStartDate == null && START_VERBS.contains(date.getFirstVerbGovernorLemma())) {
                        newStartDate = date;
                        date.isStart = true;
                    } else if (newEndDate == null && END_VERBS.contains(date.getFirstVerbGovernorLemma())) {
                        newEndDate = date;
                        date.isEnd = true;
                    }
                }
                /* TIMES */
                for (TimeInfo time : times) {
                    if (newStartTime == null && !time.isEnd &&
                            START_VERBS.contains(time.getFirstVerbGovernorLemma())) {
                        newStartTime = time;
                        time.isStart = true;
                    } else if (newEndTime == null && !time.isStart &&
                            END_VERBS.contains(time.getFirstVerbGovernorLemma())) {
                        newEndTime = time;
                        time.isEnd = true;
                    }
                }

                /* If no chnge occurred in the current iteration, exit */
                if ((previousStartDate == null && newStartDate == null || previousStartDate != null && previousStartDate.equals(newStartDate)) &&
                        (previousEndDate == null && newEndDate == null || previousEndDate != null && previousEndDate.equals(newEndDate)) &&
                        (previousStartTime == null && newStartTime == null || previousStartTime != null && previousStartTime.equals(newStartTime)) &&
                        (previousEndTime == null && newEndTime == null || previousEndTime != null && previousEndTime.equals(newEndTime)))
                    doneVerbs = true;
            } while (!doneVerbs);


            /* Check if the user is answering to targetted questions */
            /* Not updating indexes, but it doesn't matter */
            if (dates.size() == 1 && newStartDate == null && machinePrevState.endsWith("START_DATE"))
                newStartDate = dates.get(0);
            else if (dates.size() == 1 && newEndDate == null && machinePrevState.endsWith("END_DATE"))
                newEndDate = dates.get(0);
            else if (times.size() == 1 && newStartTime == null && machinePrevState.endsWith("START_TIME"))
                newStartTime = times.get(0);
            else if (times.size() == 1 && newEndTime == null && machinePrevState.endsWith("END_TIME"))
                newEndTime = times.get(0);

            log.info("Final Start Date: " + newStartDate);
            log.info("Final End Date: " + newEndDate);
            log.info("Final Start Time: " + newStartTime);
            log.info("Final End Time: " + newEndTime);

            /* ADJUST TIMES */


            log.info("Corrected Start Date: " + newStartDate);
            log.info("Corrected End Date: " + newEndDate);
            log.info("Corrected Start Time: " + newStartTime);
            log.info("Corrected End Time: " + newEndTime);

            if (newStartDate != null)
                information.put("startDate", newStartDate.getDate());
            if (newEndDate != null)
                information.put("endDate", newEndDate.getDate());
            if (newStartTime != null)
                information.put("startTime", newStartTime.getTime());
            if (newEndTime != null)
                information.put("endTime", newEndTime.getTime());
        } catch (NullPointerException exception) {
            exception.printStackTrace();
            log.severe("USER UTTERANCE:\t" + annotatedUserUtterance.toString());
            log.severe("DATES FOUND:\t" + dateAnnotations.toString());
            log.severe("TIMES FOUND:\t" + timeAnnotations.toString());
        }
    }
}
