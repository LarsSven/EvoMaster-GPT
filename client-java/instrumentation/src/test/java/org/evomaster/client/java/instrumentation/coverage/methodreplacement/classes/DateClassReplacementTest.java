package org.evomaster.client.java.instrumentation.coverage.methodreplacement.classes;

import org.evomaster.client.java.instrumentation.shared.ObjectiveNaming;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by jgaleotti on 29-Ago-19.
 */
public class DateClassReplacementTest {

    @BeforeEach
    public void setUp() {
        ExecutionTracer.reset();
    }

    @Test
    public void testEqualsDates() {
        Date thisDate = new Date();


        final String idTemplate = ObjectiveNaming.METHOD_REPLACEMENT + "IdTemplate";
        boolean equalsValue = DateClassReplacement.equals(thisDate, thisDate, idTemplate);

        assertEquals(1, ExecutionTracer.getNonCoveredObjectives(idTemplate).size());

        String targetId = ExecutionTracer.getNonCoveredObjectives(ObjectiveNaming.METHOD_REPLACEMENT)
                .iterator().next();
        double h0 = ExecutionTracer.getValue(targetId);

        assertEquals(0, h0);
        assertTrue(equalsValue);
    }

    @Test
    public void testEqualsNull() {
        Date thisDate = new Date();

        final String idTemplate = ObjectiveNaming.METHOD_REPLACEMENT + "IdTemplate";
        boolean equalsValue = DateClassReplacement.equals(thisDate, null, idTemplate);
        assertFalse(equalsValue);

        assertEquals(1, ExecutionTracer.getNonCoveredObjectives(idTemplate).size());

        String targetId = ExecutionTracer.getNonCoveredObjectives(ObjectiveNaming.METHOD_REPLACEMENT)
                .iterator().next();
        double h0 = ExecutionTracer.getValue(targetId);

        assertEquals(0, h0);


    }

    @Test
    public void testDateDistance() throws ParseException {
        String date1 = "07/15/2016";
        String time1 = "11:00 AM";
        String time2 = "11:15 AM";
        String time3 = "11:30 AM";

        String format = "MM/dd/yyyy hh:mm a";

        SimpleDateFormat sdf = new SimpleDateFormat(format);

        Date dateObject1 = sdf.parse(date1 + " " + time1);
        Date dateObject2 = sdf.parse(date1 + " " + time2);
        Date dateObject3 = sdf.parse(date1 + " " + time3);

        final String idTemplate = ObjectiveNaming.METHOD_REPLACEMENT + "IdTemplate";
        boolean equals0 = DateClassReplacement.equals(dateObject1, dateObject3, idTemplate);
        assertFalse(equals0);

        String targetId = ExecutionTracer.getNonCoveredObjectives(ObjectiveNaming.METHOD_REPLACEMENT)
                .iterator().next();
        double h0 = ExecutionTracer.getValue(targetId);
        assertTrue(h0 > 0);

        boolean equals1 = DateClassReplacement.equals(dateObject1, dateObject2, idTemplate);
        assertFalse(equals1);

        double h1 = ExecutionTracer.getValue(targetId);
        assertTrue(h1 > 0);

        // 11.15 is closer to 11.00 than 11.30
        assertTrue(h1 > h0);
    }


    @Test
    public void testBeforeDateDistance() throws ParseException {
        String date1 = "07/15/2016";
        String time1 = "11:00 AM";
        String time2 = "11:15 AM";
        String time3 = "11:30 AM";

        String format = "MM/dd/yyyy hh:mm a";

        SimpleDateFormat sdf = new SimpleDateFormat(format);

        Date dateObject1 = sdf.parse(date1 + " " + time1);
        Date dateObject2 = sdf.parse(date1 + " " + time2);
        Date dateObject3 = sdf.parse(date1 + " " + time3);

        final String idTemplate = ObjectiveNaming.METHOD_REPLACEMENT + "IdTemplate";

        boolean before0 = DateClassReplacement.before(dateObject3, dateObject1, idTemplate);
        assertFalse(before0);

        assertEquals(1, ExecutionTracer.getNonCoveredObjectives(idTemplate).size());
        String targetId = ExecutionTracer.getNonCoveredObjectives(ObjectiveNaming.METHOD_REPLACEMENT)
                .iterator().next();
        double h0 = ExecutionTracer.getValue(targetId);
        assertTrue(h0 > 0);

        boolean before1 = DateClassReplacement.before(dateObject2, dateObject1, idTemplate);
        assertFalse(before1);
        double h1 = ExecutionTracer.getValue(targetId);
        assertTrue(h0 < h1);


        boolean before2 = DateClassReplacement.before(dateObject1, dateObject3, idTemplate);
        assertTrue(before2);
        double h2 = ExecutionTracer.getValue(targetId);

        assertEquals(1, h2);

        boolean before3 = DateClassReplacement.before(dateObject1, dateObject2, idTemplate);
        assertTrue(before3);
        double h3 = ExecutionTracer.getValue(targetId);
        assertEquals(1, h3);
    }

    @Test
    public void testBeforeDate() throws ParseException {
        String date1 = "07/15/2016";
        String time1 = "11:00 AM";
        String time2 = "11:15 AM";

        String format = "MM/dd/yyyy hh:mm a";

        SimpleDateFormat sdf = new SimpleDateFormat(format);

        Date dateObject1 = sdf.parse(date1 + " " + time1);
        Date dateObject2 = sdf.parse(date1 + " " + time2);

        boolean beforeValue = DateClassReplacement.before(dateObject1, dateObject2, ObjectiveNaming.METHOD_REPLACEMENT + "IdTemplate");
        assertTrue(beforeValue);

        boolean notBeforeValue = DateClassReplacement.before(dateObject2, dateObject1, ObjectiveNaming.METHOD_REPLACEMENT + "IdTemplate");
        assertFalse(notBeforeValue);

        boolean sameDateValue = DateClassReplacement.before(dateObject1, dateObject1, ObjectiveNaming.METHOD_REPLACEMENT + "IdTemplate");
        assertFalse(sameDateValue);

    }

    @Test
    public void testAfterDate() throws ParseException {
        String date1 = "07/15/2016";
        String time1 = "11:00 AM";
        String time2 = "11:15 AM";

        String format = "MM/dd/yyyy hh:mm a";

        SimpleDateFormat sdf = new SimpleDateFormat(format);

        Date dateObject1 = sdf.parse(date1 + " " + time1);
        Date dateObject2 = sdf.parse(date1 + " " + time2);

        boolean afterValue = DateClassReplacement.after(dateObject1, dateObject2, ObjectiveNaming.METHOD_REPLACEMENT + "IdTemplate");
        assertFalse(afterValue);

        boolean notAfterValue = DateClassReplacement.after(dateObject2, dateObject1, ObjectiveNaming.METHOD_REPLACEMENT + "IdTemplate");
        assertTrue(notAfterValue);

        boolean sameDateValue = DateClassReplacement.after(dateObject1, dateObject1, ObjectiveNaming.METHOD_REPLACEMENT + "IdTemplate");
        assertFalse(sameDateValue);
    }

}