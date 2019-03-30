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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static opendial.modules.mume.config.Shared.*;
import static opendial.modules.mume.information.DateInfo.WEEK_DAY_NUMBER;
import static opendial.modules.mume.information.DateInfo.YEAR_MONTH_NUMBER;
import static opendial.modules.mumeuserdriven.Shared.log;

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
     * @param durationAnnotations    the List<Set<IndexedWord>> of recognised durations in annotatedUserUtterance
     * @param information            Map<String, String> that will contains the updated information
     * @param oldInformation         Map<String, String> of the already known information
     * @param machinePrevState       the previous state of the machine, needed if the user is answering a question from the system
     */
    void extractTimeAndDate(Annotation annotatedUserUtterance,
                            List<List<IndexedWord>> dateAnnotations,
                            List<List<IndexedWord>> timeAnnotations,
                            List<List<IndexedWord>> durationAnnotations,
                            Map<String, String> information,
                            Map<String, String> oldInformation,
                            String machinePrevState) {
        try {
            DateInfo newStartDate = null;
            DateInfo newEndDate = null;
            TimeInfo newStartTime = null;
            TimeInfo newEndTime = null;

            List<CoreLabel> tokens = annotatedUserUtterance.get(CoreAnnotations.TokensAnnotation.class);
            SemanticGraph dependencies = annotatedUserUtterance.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);

            List<DateInfo> dates = dateAnnotations.stream().map(a -> new DateInfo(a, tokens, dependencies)).collect(Collectors.toList());
            List<TimeInfo> times = timeAnnotations.stream().map(a -> new TimeInfo(a, tokens, dependencies)).collect(Collectors.toList());
            // TODO
            List<DurationInfo> durs = durationAnnotations.stream().map(a -> new DurationInfo(a, tokens, dependencies)).collect(Collectors.toList());
            boolean nowClue = false;

            log.info("Dates:\t" + dateAnnotations.toString());
            dates.forEach(d -> log.info(d.toString()));
            log.info("Times:\t" + timeAnnotations.toString());
            times.forEach(t -> log.info(t.toString()));
            log.info("Durations:\t" + durationAnnotations.toString());
            durs.forEach(t -> log.info(t.toString()));

            boolean doneCase = false;
            boolean doneVerbs = false;

            do {
                do {
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
                            if (newStartDate == null && STRONG_START_DATE_CASE.contains(date.getCaseType()) && !date.isEnd) {
                                newStartDate = date;
                                date.isStart = true;
                            }
                            /* "... fino a domani...", "... a domani...", "... al 21 febbraio..." */
                            else if (newEndDate == null && STRONG_END_DATE_CASE.contains(date.getCaseType()) && !date.isStart) {
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
                            if (newStartTime == null && STRONG_START_TIME_CASE.contains(time.getCaseType()) && !time.isEnd) {
                                newStartTime = time;
                                time.isStart = true;
                            }
                            /* "... fino alle 16..." */
                            else if (newEndTime == null && STRONG_END_TIME_CASE.contains(time.getCaseType()) && !time.isStart) {
                                newEndTime = time;
                                time.isEnd = true;
                            }
                        }

                    }

                    /* SEARCH FOR AMBIGUOUS TEMPORAL EXPRESSION (those which role dependes on other information in the sentence) */
                    /* DATES */
                    for (DateInfo date : dates) {
                        if (DEPENDANT_DATE_CASE.contains(date.getCaseType())) {
//                            log.info(String.valueOf(true));
                            /* "... dal 20 febbraio... al 21..." */
                            if (newEndDate == null &&
                                    newStartDate != null && STRONG_START_DATE_CASE.contains(newStartDate.getCaseType())) {
                                newEndDate = date;
                                date.isEnd = true;
                            }
                            /* "... dal 14 febbraio alle 13...", "... dalle 14 del 21 febbraio..." */
                            if (newStartDate == null &&
                                    newStartTime != null &&
                                    (newStartTime.isGovernorOf(date) ||
                                            date.isGovernorOf(newStartTime))) {
                                newStartDate = date;
                                date.isStart = true;
                            }
                            /* "... fino alle 19 del 22..." */
                            if (newEndDate == null &&
                                    newEndTime != null &&
                                    (newEndTime.isGovernorOf(date) ||
                                            date.isGovernorOf(newEndTime))) {
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
                                    newStartDate != null &&
                                    (newStartDate.isGovernorOf(time) ||
                                            time.isGovernorOf(newStartDate))) {
                                newStartTime = time;
                                time.isStart = true;
                            }
                            /* "... fino a dopodomani alle 9..." */
                            if (newEndTime == null && !time.isStart &&
                                    newEndDate != null &&
                                    (newEndDate.isGovernorOf(time) ||
                                            time.isGovernorOf(newEndDate))) {
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
                    if (newStartDate == null && !date.isEnd && /* START_VERBS.contains(date.getFirstVerbGovernorLemma()) */
                            !END_VERBS.contains(date.getFirstVerbGovernorLemma())) {
                        newStartDate = date;
                        date.isStart = true;
                    } else if (newEndDate == null && !date.isStart && END_VERBS.contains(date.getFirstVerbGovernorLemma())) {
                        newEndDate = date;
                        date.isEnd = true;
                    }
                }
                /* TIMES */
                for (TimeInfo time : times) {
//                    log.info(time.getFirstVerbGovernorLemma());
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

            // Propagate time characteristic to date
            if (newStartDate == null &&
                    newStartTime != null &&
                    !dates.isEmpty()) {
                Iterator<DateInfo> it = dates.iterator();
                while (it.hasNext() && newStartDate == null) {
                    DateInfo date = it.next();
                    if (!date.isEnd && newStartTime.isGovernorOf(date)) {
                        newStartDate = date;
                        newStartDate.isStart = true;
                    }
                }
            }

            if (newEndDate == null &&
                    newEndTime != null &&
                    !dates.isEmpty()) {
                Iterator<DateInfo> it = dates.iterator();
                while (it.hasNext() && newEndDate == null) {
                    DateInfo date = it.next();
                    if (!date.isStart && newEndTime.isGovernorOf(date)) {
                        newEndDate = date;
                        newEndDate.isEnd = true;
                    }
                }
            }

            for (CoreLabel token : tokens)
                if (NOW_WORDS.contains(token.originalText().toLowerCase()))
                    nowClue = true;


            /* TODO implement
            if (newEndTime == null && !durs.isEmpty() && newStartTime != null) {
                newEndTime = TimeInfo.generateFromDuration(newStartTime, durs.get(0));
                newEndTime.isEnd = true;
            }
            */

            /*
            // Finally
            // TODO check
            // If the only temporal information communicated is a date,
            //  then it's the start date and the end date
            if (newStartDate == null &&
                    newEndDate == null &&
                    dates.size() == 1) {
                newStartDate = dates.get(0);
                newStartDate.isStart = true;
                // newEndDate = dates.get(0);
                // newEndDate.isEnd = true;
            }
            */

            /* Check if the user is answering to targetted questions */
            /* Not updating indexes, but it doesn't matter */
            if (times.size() == 1 && newStartTime == null && machinePrevState.endsWith("START_TIME")) {
                newStartTime = times.get(0);
                times.get(0).isStart = true;
            } else if (dates.size() == 1 && newStartDate == null && machinePrevState.endsWith("START_TIME")) {
                newStartDate = dates.get(0);
                dates.get(0).isStart = true;
            }


            /*--------------*/
            /*- INFERENCES -*/
            /*--------------*/
            ZonedDateTime now = ZonedDateTime.now();
            // Check day/year Haideltime confusion
            if (newStartDate != null && newStartDate.getDate().split("-").length < 3)
                // The day of the month has been mistaken as an year by HidelTime:
                //  the user is (likely) telling that s/he wants to start the trip
                //  this year and this month on the day of the given date
                newStartDate.correctDayYearConfusion(now);
            if (newEndDate != null && newEndDate.getDate().split("-").length < 3)
                // The day of the month has been mistaken as an year by HidelTime:
                //  the user is (likely) telling that s/he wants to start the trip
                //  this year and this month on the day of the given date
                newEndDate.correctDayYearConfusion(now);

            /* ROUND MINUTES */
            if (newStartTime != null)
                newStartTime.roundToPreviousQuarter();
            if (newEndTime != null)
                newEndTime.roundToNextQuarter(newEndDate);

            log.info("Final Start Date: " + newStartDate);
            log.info("Final End Date: " + newEndDate);
            log.info("Final Start Time: " + newStartTime);
            log.info("Final End Time: " + newEndTime);

            /* ADJUST TIMES */
            // TODO check the methodology: minutes in letters are a problem?
            if (newStartDate != null) {
                String date = newStartDate.getDate();
                // "tutto il giorno"
                boolean startIsAlsoEnd = false;
                if (date.contains("X")) {
                    if (information.get(START_DATE).equals(NONE))
                        date = String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
                    if (newEndDate == null && information.get(END_DATE).equals(NONE))
                        startIsAlsoEnd = true;
                    // information.put(END_DATE, date);
                }
                /* Correct HeidelTime error on weekday:
                    if today is monday, "fino a lunedì" is not 'til today
                 /
                if (!newStartDate.getWeekDay().isEmpty()) {
                    String[] newStartDateFields = date.split("-");
                    int newYear = Integer.parseInt(newStartDateFields[0]);
                    int newMonth = Integer.parseInt(newStartDateFields[1]);
                    int newDayOfMonth = Integer.parseInt(newStartDateFields[2]);
                    if (newYear == now.getYear() &&
                            newMonth == now.getMonthValue() &&
                            newDayOfMonth == now.getDayOfMonth()) {
                        newDayOfMonth += WEEK_DAY_NUMBER;
                        int dayNumber = DateInfo.getMonthDaysNumber(newMonth);
                        if (newDayOfMonth > dayNumber) {
                            newDayOfMonth = newDayOfMonth % dayNumber;
                            newMonth++;
                            if (newMonth > YEAR_MONTH_NUMBER) {
                                newMonth = newMonth % YEAR_MONTH_NUMBER;
                                newYear++;
                            }
                        }
                    }
                    date = String.format("%04d-%02d-%02d", newYear, newMonth, newDayOfMonth);
                }
                */
                information.put(START_DATE, date);
                if (startIsAlsoEnd)
                    information.put(START_DATE, date);
            } else if (nowClue && information.get(START_DATE).equals(NONE))
                information.put(START_DATE, now.format(DateTimeFormatter.ISO_LOCAL_DATE));

            if (newEndDate != null) {
                String date = newEndDate.getDate();
                if (date.split("-").length < 2) {
                    // The start date or the current date if endDate == Missing
                    String[] startDateFields = information.get(START_DATE).split("-");
                    if (startDateFields.length >= 3)
                        date = String.format("%s-%s-%s", startDateFields[0], startDateFields[1], startDateFields[2]);
                    else
                        // startDate == Missing
                        date = String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
                }
                /* Correct HeidelTime error on weekday:
                    if today is monday, "fino a lunedì" is not 'til today
                    TODO check if "lunedì" is recognized as "il prossimo lunedì"
                 */
                if (!newEndDate.getWeekDay().isEmpty()) {
                    String[] newEndDateFields = date.split("-");
                    int newYear = Integer.parseInt(newEndDateFields[0]);
                    int newMonth = Integer.parseInt(newEndDateFields[1]);
                    int newDayOfMonth = Integer.parseInt(newEndDateFields[2]);
                    if (newYear == now.getYear() &&
                            newMonth == now.getMonthValue() &&
                            newDayOfMonth == now.getDayOfMonth()) {
                        newDayOfMonth += WEEK_DAY_NUMBER;
                        int dayNumber = DateInfo.getMonthDaysNumber(newMonth);
                        if (newDayOfMonth > dayNumber) {
                            newDayOfMonth = newDayOfMonth % dayNumber;
                            newMonth++;
                            if (newMonth > YEAR_MONTH_NUMBER) {
                                newMonth = newMonth % YEAR_MONTH_NUMBER;
                                newYear++;
                            }
                        }
                    }
                    date = String.format("%04d-%02d-%02d", newYear, newMonth, newDayOfMonth);
                }
                information.put(END_DATE, date);
            }
            // If the user cummunicated only the end hour, probably the end date is the same as the start date
            //  (or today if startDate == Missing)
            else if (newEndTime != null && information.get(END_DATE).equals(NONE)) {
                String[] startDate = information.get(START_DATE).split("-");
                if (startDate.length == 3)
                    information.put(END_DATE, String.format("%s-%s-%s", startDate[0], startDate[1], startDate[2]));
                else
                    information.put(END_DATE, String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
            }

            if (newStartTime != null) {
                String[] newTimeFields = newStartTime.getTime().split(":");
                String[] startDateFields = information.get(START_DATE).split("-");
                if (startDateFields.length < 3) {
                    // startDate == Missing: update
                    startDateFields = new String[]{String.valueOf(now.getYear()), String.valueOf(now.getMonthValue()), String.valueOf(now.getDayOfMonth())};
                    information.put(START_DATE, String.format("%s-%s-%s", startDateFields[0], startDateFields[1], startDateFields[2]));
                }

//                log.info(newStartTime.getWords());
//                log.info(String.valueOf(newStartTime.isInLetters()));
//                log.info(String.valueOf(Integer.parseInt(startDateFields[2]) == now.getDayOfMonth()));
//                log.info(String.valueOf(Integer.parseInt(newTimeFields[0]) < now.getHour()));
//                log.info(String.valueOf(Integer.parseInt(newTimeFields[0]) < 12));

                // If the user communicated an ambiguus time ("le sei": 6:00 or 16:00?)
                //  the system tries to infer the real start time
                String time = (Integer.parseInt(newTimeFields[0]) +
                        (// If the user has communicated an ambiguus hour, and...
                                (newStartTime.hasEveningSpecification() ||
                                        (newStartTime.isInLetters() &&
                                                // the start day is today, and...
                                                Integer.parseInt(startDateFields[2]) == now.getDayOfMonth() &&
                                                // the inferred hour is past, and...
                                                Integer.parseInt(newTimeFields[0]) < now.getHour() &&
                                                // there is actually some ambguity
                                                Integer.parseInt(newTimeFields[0]) < 12)) ?
                                        12 : 0)
                ) + ":" + newTimeFields[1];
                // OpenDial has some problem with ':', so replace it with '-' in time information
                information.put(START_TIME, time.replace(":", "-"));
            } else if ((newStartDate != null && newStartDate.isNow || nowClue) && information.get(START_TIME).equals(NONE)) {
                String[] nowTimeFields = now.format(DateTimeFormatter.ISO_LOCAL_TIME).split(":");
                information.put(START_TIME, String.format("%s:%s", nowTimeFields[0], nowTimeFields[1]).replace(":", "-"));
            }

            if (newEndTime != null) {
                String[] newTimeFields = newEndTime.getTime().split(":");

                String[] startDateFields = information.get(START_DATE).split("-");
                if (startDateFields.length < 3) {
                    // startDate == Missing
                    startDateFields = new String[]{String.valueOf(now.getYear()), String.valueOf(now.getMonthValue()), String.valueOf(now.getDayOfMonth())};
                    information.put(START_DATE, String.format("%s-%s-%s", startDateFields[0], startDateFields[1], startDateFields[2]));
                }
                String[] endDateFields = information.get(END_DATE).split("-");
                if (endDateFields.length < 3) {
                    // endDate == Missing
                    endDateFields = startDateFields;
                    information.put(END_DATE, String.format("%s-%s-%s", endDateFields[0], endDateFields[1], endDateFields[2]));
                }

                int startHour;
                String[] startTimeFields = information.get(START_TIME).split("-");
                if (startTimeFields.length < 2)
                    // stratTime == Missing
                    startHour = now.getHour();
                else
                    startHour = Integer.parseInt(startTimeFields[0]);

//                log.info(newEndTime.getWords());
//                log.info(String.valueOf(newEndTime.isInLetters()));
//                log.info(String.valueOf(Integer.parseInt(endDateFields[2]) == now.getDayOfMonth()));
//                log.info(String.valueOf(Integer.parseInt(newTimeFields[0]) < now.getHour()));
//                log.info(String.valueOf(Integer.parseInt(newTimeFields[0]) < 12));

                String time = (Integer.parseInt(newTimeFields[0]) +
                        (// If the user has communicated an ambiguus hour, and...
                                (newEndTime.hasEveningSpecification() ||
                                        (newEndTime.isInLetters() &&
                                                // the end day is the start day, and...
                                                Integer.parseInt(endDateFields[2]) == Integer.parseInt(startDateFields[2]) &&
                                                // the inferred hour is after the strat hour, and...
                                                Integer.parseInt(newTimeFields[0]) <= startHour &&
                                                // there is actually some ambguity
                                                Integer.parseInt(newTimeFields[0]) < 12)) ?
                                        12 : 0)
                ) + ":" + newTimeFields[1];
                // OpenDial has some problem with ':', so replace it with '-' in time information
                information.put(END_TIME, time.replace(":", "-"));
            }
        } catch (NullPointerException exception) {
            exception.printStackTrace();
            log.severe("USER UTTERANCE:\t" + annotatedUserUtterance.toString());
            log.severe("DATES FOUND:\t" + dateAnnotations.toString());
            log.severe("TIMES FOUND:\t" + timeAnnotations.toString());
        }
    }
}
