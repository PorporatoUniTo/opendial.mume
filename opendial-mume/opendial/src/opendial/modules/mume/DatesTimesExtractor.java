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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static opendial.modules.mume.config.Shared.*;
import static opendial.modules.mume.information.DurationInfo.DURATIONS_SEPARATOR;
import static opendial.modules.mume.information.TimeInfo.QUARTER;

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
            log.info("Dates:\t" + dateAnnotations);
            log.info("Times:\t" + timeAnnotations);
            log.info("Durations:\t" + durationAnnotations);


            DateInfo newStartDate = null;
            DateInfo newEndDate = null;
            TimeInfo newStartTime = null;
            TimeInfo newEndTime = null;
            DurationInfo newDuration = null;

            List<CoreLabel> tokens = annotatedUserUtterance.get(CoreAnnotations.TokensAnnotation.class);
            SemanticGraph dependencies = annotatedUserUtterance.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);

            List<DateInfo> dates = dateAnnotations.stream().map(a -> new DateInfo(a, tokens, dependencies)).collect(Collectors.toList());
            List<TimeInfo> times = timeAnnotations.stream().map(a -> new TimeInfo(a, tokens, dependencies)).collect(Collectors.toList());
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
                            !date.getFirstVerbGovernorLemma().isEmpty() && !END_VERBS.contains(date.getFirstVerbGovernorLemma())) {
                        newStartDate = date;
                        date.isStart = true;
                    } else if (newEndDate == null && !date.isStart && END_VERBS.contains(date.getFirstVerbGovernorLemma())) {
                        newEndDate = date;
                        date.isEnd = true;
                    }
                }

                /* TIMES */
                for (TimeInfo time : times) {
                    // log.info(time.getFirstVerbGovernorLemma());
                    if (newStartTime == null && !time.isEnd &&
                            !time.getFirstVerbGovernorLemma().isEmpty() && !END_VERBS.contains(time.getFirstVerbGovernorLemma())) {
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


            if (!machinePrevState.endsWith("SLOT"))
                for (CoreLabel token : tokens)
                    if (NOW_WORDS.contains(token.originalText().toLowerCase()))
                        nowClue = true;


            if (newEndTime == null && !durs.isEmpty()) {
                newDuration = durs.get(0);
            }


            if (newEndTime == null && newStartTime == null && newDuration == null &&
                    times.size() == 1 && DEPENDANT_TIME_CASE.contains(times.get(0).getCaseType())) {
                newStartTime = times.get(0);
                times.get(0).isStart = true;
            }


            /* Check if the user is answering to targetted questions */
            if ((times.size() == 1 && !times.get(0).isEnd || times.size() == 2 && newEndTime != null) && newStartTime == null && (machinePrevState.endsWith("START_TIME") || machinePrevState.endsWith("START_DATE"))) {
                newStartTime = times.get(0);
                times.get(0).isStart = true;
            } else if ((dates.size() == 1 && !dates.get(0).isEnd || dates.size() == 2 && newEndDate != null) && newStartDate == null && machinePrevState.endsWith("START_DATE")) {
                newStartDate = dates.get(0);
                dates.get(0).isStart = true;
            }


            log.info("Extracted Start Time: " + newStartTime);
            log.info("Extracted End Time: " + newEndTime);
            log.info("Extracted Start Date: " + newStartDate);
            log.info("Extracted End Date: " + newEndDate);
            log.info("Extracted Duration: " + newDuration);
            log.info("Extracted 'now': " + nowClue);


            /* information has not been updated yet: propagate old information */
            information.put(START_TIME, oldInformation.getOrDefault(START_TIME, NONE));
            information.put(END_TIME, oldInformation.getOrDefault(END_TIME, NONE));
            information.put(START_DATE, oldInformation.getOrDefault(START_DATE, NONE));
            information.put(END_DATE, oldInformation.getOrDefault(END_DATE, NONE));


            /*--------------*/
            /*- INFERENCES -*/
            /*--------------*/
            ZonedDateTime now = ZonedDateTime.now();
            // Check day/year Haideltime confusion
            if (newStartDate != null && newStartDate.getDate().split("-").length == 1)
                // The day of the month has been mistaken as an year by HidelTime:
                //  the user is (likely) telling that s/he wants to start the trip
                //  this year and this month on the day of the given date
                newStartDate.correctDayYearConfusion(now);
            if (newEndDate != null && newEndDate.getDate().split("-").length == 1)
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
            if (newStartDate != null) {
                String date = newStartDate.getDate();
                // "tutto il giorno"
                if (date.contains("X")) {
                    if (information.get(START_DATE).equals(NONE))
                        date = String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
                }
                /* Correct HeidelTime error on weekday:
                    if today is monday, "da lunedì" is not from today or the past monday (?)
                 */
                if (!newStartDate.getWeekDay().isEmpty()) {
                    String[] newStartDateFields = date.split("-");
                    int newYear = Integer.parseInt(newStartDateFields[0]);
                    int newMonth = Integer.parseInt(newStartDateFields[1]);
                    int newDayOfMonth = Integer.parseInt(newStartDateFields[2]);
                    if (newYear <= now.getYear() &&
                            newMonth <= now.getMonthValue() &&
                            newDayOfMonth <= now.getDayOfMonth()) {
                        newStartDate.addOneWeek();
                        newStartDateFields = newStartDate.getDate().split("-");
                        newYear = Integer.parseInt(newStartDateFields[0]);
                        newMonth = Integer.parseInt(newStartDateFields[1]);
                        newDayOfMonth = Integer.parseInt(newStartDateFields[2]);
                    }
                    date = String.format("%04d-%02d-%02d", newYear, newMonth, newDayOfMonth);
                }
                information.put(START_DATE, date);
            }
            // Otherwise, the user may beeing answering a direct question
            else if (nowClue && !machinePrevState.endsWith("SLOT") && information.get(START_DATE).equals(NONE))
                information.put(START_DATE, String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth()));

            if (newEndDate != null) {
                String date = newEndDate.getDate();
                String[] startDateFields = information.get(START_DATE).split("-");
                ;
                if (date.split("-").length < 3) {
                    // The start date or the current date if endDate == Missing
                    //  this happens when the user give only the month OR
                    //  the system mistook a street name for a date (e.g. piazza XVIII Dicembre)
                    if (startDateFields.length >= 3)
                        date = String.format("%04d-%02d-%02d", Integer.parseInt(startDateFields[0]), Integer.parseInt(startDateFields[1]), Integer.parseInt(startDateFields[2]));
                    else {
                        // startDate == Missing
                        startDateFields = new String[]{String.format("%04d", now.getYear()), String.format("%02d", now.getMonthValue()), String.format("%04d", now.getDayOfMonth())};
                        date = String.format("%s-%s-%s", startDateFields[0], startDateFields[1], startDateFields[2]);
                    }
                }
                /* Correct HeidelTime error on weekday:
                    if today is monday, "fino a lunedì" is not until today
                 */
                if (!newEndDate.getWeekDay().isEmpty()) {
                    String[] newEndDateFields = date.split("-");
                    int newYear = Integer.parseInt(newEndDateFields[0]);
                    int newMonth = Integer.parseInt(newEndDateFields[1]);
                    int newDayOfMonth = Integer.parseInt(newEndDateFields[2]);
                    if (newYear <= Integer.parseInt(startDateFields[0]) &&
                            newMonth <= Integer.parseInt(startDateFields[0]) &&
                            newDayOfMonth <= Integer.parseInt(startDateFields[0])) {
                        newEndDate.addOneWeek();
                        newEndDateFields = newEndDate.getDate().split("-");
                        newYear = Integer.parseInt(newEndDateFields[0]);
                        newMonth = Integer.parseInt(newEndDateFields[1]);
                        newDayOfMonth = Integer.parseInt(newEndDateFields[2]);
                    }
                    date = String.format("%04d-%02d-%02d", newYear, newMonth, newDayOfMonth);
                }
                information.put(END_DATE, date);
            }
            // If the user cummunicated only the end hour, probably the end date is the same as the start date
            //  (or today if startDate == Missing)
            else if (newEndTime != null && information.get(END_DATE).equals(NONE)) {
                String[] startDateFields = information.get(START_DATE).split("-");
                if (startDateFields.length == 3)
                    information.put(END_DATE, String.format("%s-%s-%s", startDateFields[0], startDateFields[1], startDateFields[2]));
                else {
                    String newDate = String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
                    information.put(START_DATE, newDate);
                    information.put(END_DATE, newDate);
                }
            }
            // If the user didn't day anything about the endDate or the endTime,
            //  The system assums that the endDate is the same as the startDate
            else if (information.get(END_DATE).equals(NONE) && !information.get(START_DATE).equals(NONE))
                information.put(END_DATE, information.getOrDefault(START_DATE, NONE));


            if (newStartTime != null) {
                String[] newTimeFields = newStartTime.getTime().split(":");
                String[] startDateFields = information.get(START_DATE).split("-");
                if (startDateFields.length < 3) {
                    // startDate == Missing: update
                    startDateFields = new String[]{String.format("%04d", now.getYear()), String.format("%02d", now.getMonthValue()), String.format("%02d", now.getDayOfMonth())};
                    information.put(START_DATE, String.format("%s-%s-%s", startDateFields[0], startDateFields[1], startDateFields[2]));
                }

                // If the user communicated an ambiguus time ("le sei": 6:00 or 18:00?)
                //  the system tries to infer the real start time
                // TODO check the methodology: minutes in letters are a problem?
                newTimeFields[0] = String.format("%02d", Integer.parseInt(newTimeFields[0]) +
                        (// If the user has communicated an ambiguus hour, and...
                                (Integer.parseInt(newTimeFields[0]) < 12 &&
                                        (newStartTime.hasEveningSpecification() ||
                                                (newStartTime.isInLetters() &&
                                                        // the start day is today, and...
                                                        Integer.parseInt(startDateFields[0]) == now.getYear() &&
                                                        Integer.parseInt(startDateFields[1]) == now.getMonthValue() &&
                                                        Integer.parseInt(startDateFields[2]) == now.getDayOfMonth() &&
                                                        // the inferred hour (with corrected minutes) is past, and...
                                                        (Integer.parseInt(newTimeFields[0]) < now.getHour() ||
                                                                Integer.parseInt(newTimeFields[0]) == now.getHour() &&
                                                                        Integer.parseInt(newTimeFields[1]) < (now.getMinute() / QUARTER) * QUARTER)))) ?
                                        12 : 0));
                // OpenDial has some problem with ':', so replace it with '-' in time information
                information.put(START_TIME, String.format("%s:%s", newTimeFields[0], newTimeFields[1]).replace(":", "-"));
            }
            // Otherwise, the user may beeing answering a direct question
            else if ((newStartDate != null && newStartDate.isNow || nowClue && !machinePrevState.endsWith("TIME")) && information.get(START_TIME).equals(NONE))
                information.put(START_TIME, TimeInfo.roundToPreviousQuarter(String.format("%02d:%02d", now.getHour(), now.getMinute())).replace(":", "-"));
                // The user is giving the strat date as a time laps: "voglio partire tra due ore"
            else if (newDuration != null && !machinePrevState.endsWith("SLOT") &&
                    // The duration is valid, and not e.g. "sera"
                    (newDuration.getYears() != 0 ||
                            newDuration.getMonths() != 0 ||
                            newDuration.getDays() != 0 ||
                            newDuration.getHours() != 0 ||
                            newDuration.getMinutes() != 0)) {
                String startTime = TimeInfo.roundToPreviousQuarter(String.format("%02d:%02d", now.getHour(), now.getMinute()));
                String startDate = information.getOrDefault(START_DATE, String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
                if (startDate.equals(NONE))
                    startDate = String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
                String[] dur = TimeInfo.roundToNextQuarter(newDuration.toEnd(startTime, startDate)).split(DURATIONS_SEPARATOR);

                log.info("Final Duration:\t" + dur[0] + " | " + dur[1]);

                if (information.get(START_TIME).equals(NONE))
                    information.put(START_TIME, dur[0].replace(":", "-"));
                if (information.get(START_DATE).equals(NONE))
                    information.put(START_DATE, dur[1]);
                if (information.get(END_DATE).equals(NONE))
                    information.put(END_DATE, dur[1]);
            }
            /* If the user gave just the startTime, likely the startDate is 'today' */
            if (!information.getOrDefault(START_TIME, NONE).equals(NONE) &&
                    information.getOrDefault(START_DATE, NONE).equals(NONE))
                information.put(START_DATE, String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth()));

            if (newEndTime != null) {
                String[] newTimeFields = newEndTime.getTime().split(":");

                String[] startDateFields = information.get(START_DATE).split("-");
                if (startDateFields.length < 3) {
                    // startDate == Missing: update
                    startDateFields = new String[]{String.format("%04d", now.getYear()), String.format("%02d", now.getMonthValue()), String.format("%02d", now.getDayOfMonth())};
                    information.put(START_DATE, String.format("%s-%s-%s", startDateFields[0], startDateFields[1], startDateFields[2]));
                }
                String[] endDateFields = information.get(END_DATE).split("-");
                if (endDateFields.length < 3) {
                    // endDate == Missing
                    endDateFields = startDateFields;
                    information.put(END_DATE, String.format("%s-%s-%s", startDateFields[0], startDateFields[1], startDateFields[2]));
                }

                int startHour;
                String[] startTimeFields = information.get(START_TIME).split("-");
                if (startTimeFields.length < 2)
                    // startTime == Missing
                    startHour = now.getHour();
                else
                    startHour = Integer.parseInt(startTimeFields[0]);

                newTimeFields[0] = String.format("%02d", Integer.parseInt(newTimeFields[0]) +
                        (// If the user has communicated an ambiguus hour, and...
                                (newEndTime.hasEveningSpecification() ||
                                        (newEndTime.isInLetters() &&
                                                // the end day is the start day, and...
                                                Integer.parseInt(endDateFields[2]) == Integer.parseInt(startDateFields[2]) &&
                                                // the inferred hour is after the strat hour, and...
                                                Integer.parseInt(newTimeFields[0]) <= startHour &&
                                                // there is actually some ambguity
                                                Integer.parseInt(newTimeFields[0]) < 12)) ?
                                        12 : 0));
                // OpenDial has some problem with ':', so replace it with '-' in time information
                information.put(END_TIME, String.format("%s:%s", newTimeFields[0], newTimeFields[1]).replace(":", "-"));
            } else if (newDuration != null &&
                    !(machinePrevState.endsWith("TIME") || machinePrevState.endsWith("DATE")) &&  // TODO check
                    (newDuration.getYears() != 0 ||
                            newDuration.getMonths() != 0 ||
                            newDuration.getDays() != 0 ||
                            newDuration.getHours() != 0 ||
                            newDuration.getMinutes() != 0) &&
                    !information.get(START_TIME).equals(NONE)) {
                // Can not check newStartTime due to the correcttion e.g. for temporal specification ("di sera")
                String startTime = information.get(START_TIME).replace("-", ":");
                String startDate = information.get(START_DATE);
                String[] dur = TimeInfo.roundToNextQuarter(newDuration.toEnd(startTime, startDate)).split(DURATIONS_SEPARATOR);
                if (information.get(END_TIME).equals(NONE))
                    information.put(END_TIME, dur[0].replace(":", "-"));
                if (information.get(END_DATE).equals(NONE))
                    information.put(END_DATE, dur[1]);
            }
            /* If the user gave just the endTime, likely the endDate is the startDate */
            if (!information.getOrDefault(END_TIME, NONE).equals(NONE) &&
                    information.getOrDefault(END_DATE, NONE).equals(NONE) &&
                    information.getOrDefault(START_DATE, NONE).equals(NONE))
                information.put(END_DATE, information.getOrDefault(START_DATE, NONE));
        } catch (NullPointerException exception) {
            exception.printStackTrace();
            log.severe("USER UTTERANCE:\t" + annotatedUserUtterance.toString());
            log.severe("DATES FOUND:\t" + dateAnnotations.toString());
            log.severe("TIMES FOUND:\t" + timeAnnotations.toString());
            log.severe("DURATIONS FOUND:\t" + durationAnnotations.toString());
        }
    }
}
