package org.evomaster.client.java.controller.internal.db;

import io.restassured.http.ContentType;
import org.evomaster.client.java.controller.DatabaseTestTemplate;
import org.evomaster.client.java.controller.InstrumentedSutStarter;
import org.evomaster.client.java.controller.db.SqlScriptRunner;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.evomaster.client.java.controller.api.ControllerConstants.BASE_PATH;
import static org.evomaster.client.java.controller.api.ControllerConstants.TEST_RESULTS;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public interface HeuristicsCalculatorInDBTest extends DatabaseTestTemplate {


    @Test
    default void testHeuristic() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT)", true);


        InstrumentedSutStarter starter = getInstrumentedSutStarter();

        try {
            String url = start(starter);
            url += BASE_PATH;

            startNewTest(url);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x) VALUES (10)", false);

            given().accept(ContentType.JSON)
                    .get(url + TEST_RESULTS)
                    .then()
                    .statusCode(200)
                    .body("data.extraHeuristics.size()", is(1))
                    .body("data.extraHeuristics[0].heuristics.size()", is(0));

            startNewTest(url);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x) VALUES (10)", false);

            SqlScriptRunner.execCommand(getConnection(), "SELECT x FROM Foo WHERE x = 12", true);
            SqlScriptRunner.execCommand(getConnection(), "SELECT x FROM Foo WHERE x = 10", true);

            given().accept(ContentType.JSON)
                    .get(url + TEST_RESULTS)
                    .then()
                    .statusCode(200)
                    .body("data.extraHeuristics.size()", is(1))
                    .body("data.extraHeuristics[0].heuristics.size()", is(2))
                    .body("data.extraHeuristics[0].heuristics[0].value", greaterThan(0f))
                    .body("data.extraHeuristics[0].heuristics[1].value", is(0f));

            startNewActionInSameTest(url, 1);

            SqlScriptRunner.execCommand(getConnection(), "SELECT x FROM Foo WHERE x = 13", true);

            given().accept(ContentType.JSON)
                    .get(url + TEST_RESULTS)
                    .then()
                    .statusCode(200)
                    .body("data.extraHeuristics.size()", is(2))
                    .body("data.extraHeuristics[0].heuristics.size()", is(2))
                    .body("data.extraHeuristics[0].heuristics[0].value", greaterThan(0f))
                    .body("data.extraHeuristics[0].heuristics[1].value", is(0f))
                    .body("data.extraHeuristics[1].heuristics.size()", is(1))
                    .body("data.extraHeuristics[1].heuristics[0].value", greaterThan(0f));
        } finally {
            starter.stop();
        }
    }

    @Test
    default void testMultiline() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT, y INT)", true);


        int y = 42;
        String select = "select f.x \n from Foo f \n where f.y=" + y;


        InstrumentedSutStarter starter = getInstrumentedSutStarter();

        try {
            String url = start(starter);
            url += BASE_PATH;

            startNewTest(url);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x, y) VALUES (0, 0)", false);

            SqlScriptRunner.execCommand(getConnection(), select, true);

            double a = getFirstAndStartNew(url);
            assertTrue(a > 0d);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x, y) VALUES (1, " + y + ")", true);
            SqlScriptRunner.execCommand(getConnection(), select, true);

            double b = getFirstAndStartNew(url);
            assertTrue(b < a);
            assertEquals(0d, b, 0.0001);

        } finally {
            starter.stop();
        }
    }

    @Test
    default void testVarNotInSelect() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT, y INT)", true);


        int y = 42;
        String select = "select f.x from Foo f where f.y=" + y;


        InstrumentedSutStarter starter = getInstrumentedSutStarter();

        try {
            String url = start(starter);
            url += BASE_PATH;

            startNewTest(url);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x, y) VALUES (0, 0)", false);

            SqlScriptRunner.execCommand(getConnection(), select, true);

            double a = getFirstAndStartNew(url);
            assertTrue(a > 0d);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x, y) VALUES (1, " + y + ")", true);
            SqlScriptRunner.execCommand(getConnection(), select, true);

            double b = getFirstAndStartNew(url);
            assertTrue(b < a);
            assertEquals(0d, b, 0.0001);

        } finally {
            starter.stop();
        }
    }

    @Test
    default void testInnerJoin() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Bar(id INT Primary Key, valueColumn INT)", true);
        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(id INT Primary Key, valueColumn INT, bar_id INT, " +
                "CONSTRAINT fk FOREIGN KEY (bar_id) REFERENCES Bar(id) )", true);



        int x = 10;
        int y = 20;

        String select = "select f.id, f.valueColumn, f.bar_id  from Foo f inner join Bar b on f.bar_id=b.id " +
                "where f.valueColumn=" + x + " and b.valueColumn=" + y + " limit 1";

        InstrumentedSutStarter starter = getInstrumentedSutStarter();

        try {
            String url = start(starter);
            url += BASE_PATH;

            startNewTest(url);


            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Bar (id, valueColumn) VALUES (0, 0)", false);
            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (id, valueColumn, bar_id) VALUES (0, 0, 0)", false);

            SqlScriptRunner.execCommand(getConnection(), select, true);

            double a = getFirstAndStartNew(url);
            assertTrue(a > 0d);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (id, valueColumn, bar_id) VALUES (1, " + x + ", 0)", true);
            SqlScriptRunner.execCommand(getConnection(), select, true);

            double b = getFirstAndStartNew(url);
            assertTrue(b < a);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Bar (id, valueColumn) VALUES (1, " + y + ")", true);
            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (id, valueColumn, bar_id) VALUES (2, 0, 1)", true);
            SqlScriptRunner.execCommand(getConnection(), select, true);

            double c = getFirstAndStartNew(url);
            assertTrue(c < b);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Bar (id, valueColumn) VALUES (2, " + y + ")", true);
            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (id, valueColumn, bar_id) VALUES (3, " + x + ", 2)", true);
            SqlScriptRunner.execCommand(getConnection(), select, true);

            double d = getFirstAndStartNew(url);
            assertTrue(d < c);
            assertEquals(0d, d, 0.0001);

        } finally {
            starter.stop();
        }
    }


    default Double getFirstAndStartNew(String url) {

        double value = Double.parseDouble(given().accept(ContentType.JSON)
                .get(url + TEST_RESULTS)
                .then()
                .statusCode(200)
                .extract().body().path("data.extraHeuristics[0].heuristics[0].value").toString());

        startNewTest(url);

        return value;
    }

}
