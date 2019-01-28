package opendial.modules.mume;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import opendial.modules.mume.information.DateInfo;
import opendial.modules.mume.information.TimeInfo;

import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static opendial.modules.mume.config.Shared.*;
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
            //List<DurationInfo> durs = durationAnnotations.stream().map(a -> new DurationInfo(a, tokens, dependencies)).collect(Collectors.toList());

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

                    /* TODO
                    if (newEndTime == null && !durs.isEmpty() && newStartTime != null) {
                        newEndTime = TimeInfo.generateFromDuration(newStartTime, durs.get(0));
                        newEndTime.isEnd = true;
                    }
                    */

                    /* SEARCH FOR AMBIGUOUS TEMPORAL EXPRESSION (those which role dependes on other information in the sentence) */
                    /* DATES */
                    for (DateInfo date : dates) {
                        if (DEPENDANT_DATE_CASE.contains(date.getCaseType())) {
                            log.info(String.valueOf(true));
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

            //
            if (newStartDate == null &&
                    newStartTime != null &&
                    !dates.isEmpty()) {
                Iterator<DateInfo> it = dates.iterator();
                while (it.hasNext() && newStartDate == null) {
                    DateInfo date = it.next();
                    if (newStartTime.isGovernorOf(date)) {
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
                    if (newEndTime.isGovernorOf(date)) {
                        newEndDate = date;
                        newEndDate.isEnd = true;
                    }
                }
            }

            // Finally
            // TODO check
            // If the only temporal information communicated is a date,
            //  then its the start date nad the end the end date
            if (newStartDate == null &&
                    newEndDate == null &&
                    dates.size() == 1) {
                newStartDate = dates.get(0);
                newStartDate.isStart = true;
                /*
                newEndDate = dates.get(0);
                newEndDate.isEnd = true;
                */
            }


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
            // TODO check the methodology: minutes in letters are a problem?

            ZonedDateTime now = ZonedDateTime.now();
            if (newStartDate != null) {
                String date = newStartDate.getDate();
                if (date.split("-").length < 2) {
                    // The day of the month has been mistaken as an year by HidelTime:
                    //  the user is (likely) telling that s/he wants to start the trip
                    //  this year and this month on the day of the given date
                    // TODO check if this works even when the user give an actual year (?)
                    int newYear = now.getYear();
                    int newDayOfMonth = Integer.parseInt(date.substring(2));
                    int newMonth = now.getMonthValue();
                    // If the day number has past fir the current month,
                    //  the user likely is referring to the next month
                    if (now.getDayOfMonth() > newDayOfMonth) {
                        newMonth++;
                        if (newMonth > 12) {
                            newMonth = newMonth % 12;
                            newYear++;
                        }
                    }
                    date = String.format("%04d-%02d-%02d", newYear, newMonth, newDayOfMonth);
                }
                information.put("startDate", date);
            }
            // If the user comunicate only a start hour, the date (probably) is today
            else if ((newStartTime != null || newEndDate != null) && information.get("startDate").equals("Missing"))
                information.put("startDate", String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth()));

            if (newEndDate != null) {
                String date = newEndDate.getDate();
                if (date.split("-").length < 2) {
                    // The start date or the current date if startDate == Missing
                    String[] startDateFields = information.get("startDate").split("-");
                    if (startDateFields.length < 3)
                        // startDate == Missing
                        startDateFields = new String[]{String.valueOf(now.getYear()), String.valueOf(now.getMonthValue()), String.valueOf(now.getDayOfMonth())};
                    // The day of the month has been mistaken as an year by HidelTime:
                    //  the user is (likely) telling that s/he wants to start the trip
                    //  this year and this month on the day of the given date
                    // TODO check if this works even when the user give an actual year (?)
                    int newYear = Integer.parseInt(startDateFields[0]);
                    int newDayOfMonth = Integer.parseInt(date.substring(2));
                    int newMonth = Integer.parseInt(startDateFields[1]);
                    // If the day number has past fir the current month,
                    //  the user likely is referring to the next month
                    if (Integer.parseInt(startDateFields[2]) > newDayOfMonth) {
                        newMonth++;
                        if (newMonth > 12) {
                            newMonth = newMonth % 12;
                            newYear++;
                        }
                    }
                    date = String.format("%04d-%02d-%02d", newYear, newMonth, newDayOfMonth);
                }
                information.put("endDate", date);
            }
            // If the user cummunicated only the end hour, probably the end date is the same as the start date
            //  (or today if startDate == Missing)
            else if (newEndTime != null && information.get("endDate").equals("Missing")) {
                String[] startDate = information.get("startDate").split("-");
                if (startDate.length == 3)
                    information.put("endDate", String.format("%s-%s-%s", startDate[0], startDate[1], startDate[2]));
                else
                    information.put("endDate", String.format("%04d-%02d-%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
            }

            if (newStartTime != null) {
                String[] newTimeFields = newStartTime.getTime().split(":");
                String[] startDateFields = information.get("startDate").split("-");
                if (startDateFields.length < 3)
                    // startDate == Missing
                    startDateFields = new String[]{String.valueOf(now.getYear()), String.valueOf(now.getMonthValue()), String.valueOf(now.getDayOfMonth())};

                log.info(newStartTime.getWords());
                log.info(String.valueOf(!newStartTime.isInDigits()));
                log.info(String.valueOf(Integer.parseInt(startDateFields[2]) == now.getDayOfMonth()));
                log.info(String.valueOf(Integer.parseInt(newTimeFields[0]) < now.getHour()));
                log.info(String.valueOf(Integer.parseInt(newTimeFields[0]) < 12));

                // If the user communicated an ambiguus tim ("le sei": 6:00 or 16:00?)
                //  the system tries to infer the real start time
                String time = (Integer.parseInt(newTimeFields[0]) +
                        (// If the user has communicated an ambiguus hour, and...
                                (!newStartTime.isInDigits() &&
                                        // the start day is today, and...
                                        Integer.parseInt(startDateFields[2]) == now.getDayOfMonth() &&
                                        // the inferred hour is past, and...
                                        Integer.parseInt(newTimeFields[0]) < now.getHour() &&
                                        // there is actually some ambguity
                                        Integer.parseInt(newTimeFields[0]) < 12) ?
                                        12 : 0)
                ) + ":" + newTimeFields[1];
                // OpenDial has some problem with ':', so replace it with '-' in time information
                information.put("startTime", time.replace(":", "-"));
            }

            if (newEndTime != null) {
                String[] newTimeFields = newEndTime.getTime().split(":");

                String[] startDateFields = information.get("startDate").split("-");
                if (startDateFields.length < 3)
                    // startDate == Missing
                    startDateFields = new String[]{String.valueOf(now.getYear()), String.valueOf(now.getMonthValue()), String.valueOf(now.getDayOfMonth())};
                String[] endDateFields = information.get("endDate").split("-");
                if (endDateFields.length < 3) {
                    // endDate == Missing
                    endDateFields = startDateFields;
                }

                int startHour;
                String[] startTimeFields = information.get("startTime").split("-");
                if (startTimeFields.length < 2)
                    // stratTime == Missing
                    startHour = now.getHour();
                else
                    startHour = Integer.parseInt(startTimeFields[0]);

                log.info(newEndTime.getWords());
                log.info(String.valueOf(!newEndTime.isInDigits()));
                log.info(String.valueOf(Integer.parseInt(endDateFields[2]) == now.getDayOfMonth()));
                log.info(String.valueOf(Integer.parseInt(newTimeFields[0]) < now.getHour()));
                log.info(String.valueOf(Integer.parseInt(newTimeFields[0]) < 12));

                String time = (Integer.parseInt(newTimeFields[0]) +
                        (// If the user has communicated an ambiguus hour, and...
                                (!newEndTime.isInDigits() &&
                                        // the end day is the start day, and...
                                        Integer.parseInt(endDateFields[2]) == Integer.parseInt(startDateFields[2]) &&
                                        // the inferred hour is after the strat hour, and...
                                        Integer.parseInt(newTimeFields[0]) <= startHour &&
                                        // there is actually some ambguity
                                        Integer.parseInt(newTimeFields[0]) < 12) ?
                                        12 : 0)
                ) + ":" + newTimeFields[1];
                // OpenDial has some problem with ':', so replace it with '-' in time information
                information.put("endTime", time.replace(":", "-"));
            }
        } catch (NullPointerException exception) {
            exception.printStackTrace();
            log.severe("USER UTTERANCE:\t" + annotatedUserUtterance.toString());
            log.severe("DATES FOUND:\t" + dateAnnotations.toString());
            log.severe("TIMES FOUND:\t" + timeAnnotations.toString());
        }
    }
}
