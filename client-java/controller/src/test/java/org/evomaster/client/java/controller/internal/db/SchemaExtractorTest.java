package org.evomaster.client.java.controller.internal.db;

import io.restassured.http.ContentType;
import org.evomaster.client.java.controller.DatabaseTestTemplate;
import org.evomaster.client.java.controller.InstrumentedSutStarter;
import org.evomaster.client.java.controller.api.dto.database.schema.*;
import org.evomaster.client.java.controller.db.SqlScriptRunner;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.evomaster.client.java.controller.api.ControllerConstants.BASE_PATH;
import static org.evomaster.client.java.controller.api.ControllerConstants.INFO_SUT_PATH;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;

public class SchemaExtractorTest extends DatabaseTestTemplate {


    @Test
    public void testBasic() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT)");

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());
        assertNotNull(schema);

        assertAll(() -> assertEquals("public", schema.name.toLowerCase()),
                () -> assertEquals(DatabaseType.H2, schema.databaseType),
                () -> assertEquals(1, schema.tables.size()),
                () -> assertEquals("foo", schema.tables.get(0).name.toLowerCase()),
                () -> assertEquals(1, schema.tables.get(0).columns.size())
        );
    }

    @Test
    public void testTwoTables() throws Exception {
        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT); CREATE TABLE Bar(y INT)");

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());
        assertNotNull(schema);

        assertEquals(2, schema.tables.size());
        assertTrue(schema.tables.stream().map(t -> t.name.toLowerCase()).anyMatch(n -> n.equals("foo")));
        assertTrue(schema.tables.stream().map(t -> t.name.toLowerCase()).anyMatch(n -> n.equals("bar")));
    }


    @Test
    public void testIdentity() throws Exception {
        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(" +
                "  id bigint generated by default as identity " +
                ", x int" +
                ", primary key (id) " +
                ");");

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        TableDto table = schema.tables.get(0);
        assertEquals(2, table.columns.size());

        ColumnDto id = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("id"))
                .findAny().get();
        ColumnDto x = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("x"))
                .findAny().get();

        assertEquals("integer", x.type.toLowerCase());
        assertEquals("bigint", id.type.toLowerCase());

        assertFalse(x.autoIncrement);
        assertTrue(id.autoIncrement);
    }


    @Test
    public void testBasicConstraints() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(" +
                "  id bigint generated by default as identity " +
                ", name varchar(128) not null " +
                ", surname varchar(255) " +
                ", primary key (id) " +
                ");");

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        TableDto table = schema.tables.get(0);
        assertEquals(3, table.columns.size());

        ColumnDto id = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("id"))
                .findAny().get();
        assertTrue(id.autoIncrement);

        ColumnDto name = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("name"))
                .findAny().get();
        assertFalse(name.autoIncrement);
        assertFalse(name.nullable);
        assertEquals(128, name.size);

        ColumnDto surname = table.columns.stream()
                .filter(c -> c.name.equalsIgnoreCase("surname"))
                .findAny().get();
        assertFalse(surname.autoIncrement);
        assertTrue(surname.nullable);
        assertEquals(255, surname.size);
    }


    @Test
    public void testBasicForeignKey() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(" +
                "  id bigint generated by default as identity " +
                ", barId bigint not null " +
                ");" +
                " CREATE TABLE Bar(id bigint generated by default as identity);" +
                " ALTER TABLE Foo add constraint barIdKey foreign key (barId) references Bar;\n"
        );

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());
        assertEquals(2, schema.tables.size());

        TableDto bar = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Bar")).findAny().get();
        TableDto foo = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(0, bar.foreignKeys.size());
        assertEquals(1, foo.foreignKeys.size());

        ForeignKeyDto foreignKey = foo.foreignKeys.get(0);

        assertEquals(1, foreignKey.sourceColumns.size());
        assertTrue(foreignKey.sourceColumns.stream().anyMatch(c -> c.equalsIgnoreCase("barId")));
        assertTrue(foreignKey.targetTable.equalsIgnoreCase("Bar"));
    }

    @Test
    public void testQuizGame() throws Exception {

        SqlScriptRunner.runScriptFromResourceFile(getConnection(), "/db_schemas/quizgame.sql");

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());
        assertEquals(6, schema.tables.size());

        //TODO test all of its content
    }

    @Test
    public void testRetrieveSchema() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT); CREATE TABLE Bar(y INT)");

        InstrumentedSutStarter starter = getInstrumentedSutStarter();

        String url = start(starter);

        given().accept(ContentType.JSON)
                .get(url + BASE_PATH + INFO_SUT_PATH)
                .then()
                .statusCode(200)
                .body("data.sqlSchemaDto.tables.size()", is(2));
    }

    @Test
    public void testColumnUpperBoundConstraint() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (fooId INT, age_max integer check (age_max<=100));";
        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(2, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("fooId")));
        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("age_max")));

        // TODO check that the column constraint is actually extracted
        ColumnDto columnDto = fooTable.columns.stream().filter(c -> c.name.equalsIgnoreCase("age_max")).findFirst().orElse(null);

        assertEquals("INTEGER", columnDto.type);
        assertNull(columnDto.lowerBound);
        assertEquals(100, columnDto.upperBound.intValue());

    }

    @Test
    public void testTableConstraint() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (fooId INT, age_max integer);"
                + "ALTER TABLE FOO ADD CONSTRAINT CHK_AGE_MAX CHECK (age_max<=100);";
        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(2, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("fooId")));
        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("age_max")));

        // TODO check that the table constraint is actually extracted


    }

    @Test
    public void testPrimaryKey() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (id INT, "
                + "primary key (id));";

        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(1, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("id")));

        assertEquals(true, fooTable.columns.get(0).primaryKey);
        assertEquals(false, fooTable.columns.get(0).unique);

    }

    @Test
    public void testEnumStringConstraint() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (fooId INT, status varchar(1));"
                + "ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in ('A', 'B'));";
        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(2, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("fooId")));
        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("status")));

        ColumnDto statusColumn = fooTable.columns.stream().filter(c -> c.name.equalsIgnoreCase("status")).findFirst().get();

        Set<String> actualEnumValues = statusColumn.enumValuesAsStrings.stream().collect(Collectors.toSet());
        Set<String> expectedEnumValues = Stream.of("A", "B").collect(Collectors.toSet());

        assertEquals(expectedEnumValues, actualEnumValues);

    }

    @Test
    public void testEnumBooleanConstraint() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (status BOOLEAN);\n" +
                "            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (true, false));";

        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(1, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("status")));

        ColumnDto statusColumn = fooTable.columns.stream().filter(c -> c.name.equalsIgnoreCase("status")).findFirst().get();

        Set<Boolean> actualEnumValues = statusColumn.enumValuesAsStrings.stream().map(Boolean::valueOf).collect(Collectors.toSet());
        Set<Boolean> expectedEnumValues = Stream.of(Boolean.TRUE, Boolean.FALSE).collect(Collectors.toSet());

        assertEquals(expectedEnumValues, actualEnumValues);

    }

    @Test
    public void testEnumIntegerConstraint() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (status INT);\n" +
                "            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (42, 77));";

        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(1, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("status")));

        ColumnDto statusColumn = fooTable.columns.stream().filter(c -> c.name.equalsIgnoreCase("status")).findFirst().get();

        Set<Integer> actualEnumValues = statusColumn.enumValuesAsStrings.stream().map(Integer::valueOf).collect(Collectors.toSet());
        Set<Integer> expectedEnumValues = Stream.of(42, 77).collect(Collectors.toSet());

        assertEquals(expectedEnumValues, actualEnumValues);

    }

    @Test
    public void testEnumTinyIntConstraint() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (status TINYINT);\n" +
                "            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (42, 77));";

        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(1, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("status")));

        ColumnDto statusColumn = fooTable.columns.stream().filter(c -> c.name.equalsIgnoreCase("status")).findFirst().get();

        Set<Integer> actualEnumValues = statusColumn.enumValuesAsStrings.stream().map(Integer::valueOf).collect(Collectors.toSet());
        Set<Integer> expectedEnumValues = Stream.of(42, 77).collect(Collectors.toSet());

        assertEquals(expectedEnumValues, actualEnumValues);

    }

    @Test
    public void testEnumSmallIntConstraint() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (status SMALLINT);\n" +
                "            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (42, 77));";

        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(1, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("status")));

        ColumnDto statusColumn = fooTable.columns.stream().filter(c -> c.name.equalsIgnoreCase("status")).findFirst().get();

        Set<Integer> actualEnumValues = statusColumn.enumValuesAsStrings.stream().map(Integer::valueOf).collect(Collectors.toSet());
        Set<Integer> expectedEnumValues = Stream.of(42, 77).collect(Collectors.toSet());

        assertEquals(expectedEnumValues, actualEnumValues);

    }

    @Test
    public void testEnumBigIntConstraint() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (status BIGINT);\n" +
                "            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (42, 77));";

        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(1, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("status")));

        ColumnDto statusColumn = fooTable.columns.stream().filter(c -> c.name.equalsIgnoreCase("status")).findFirst().get();

        Set<Integer> actualEnumValues = statusColumn.enumValuesAsStrings.stream().map(Integer::valueOf).collect(Collectors.toSet());
        Set<Integer> expectedEnumValues = Stream.of(42, 77).collect(Collectors.toSet());

        assertEquals(expectedEnumValues, actualEnumValues);

    }

    @Test
    public void testEnumDoubleConstraint() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (status DOUBLE);\n" +
                "            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (1.0, 2.5));";

        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(1, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("status")));

        ColumnDto statusColumn = fooTable.columns.stream().filter(c -> c.name.equalsIgnoreCase("status")).findFirst().get();

        Set<Double> actualEnumValues = statusColumn.enumValuesAsStrings.stream().map(Double::valueOf).collect(Collectors.toSet());
        Set<Double> expectedEnumValues = Stream.of(1.0, 2.5).collect(Collectors.toSet());

        assertEquals(expectedEnumValues, actualEnumValues);

    }


    @Test
    public void testEnumRealConstraint() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (status REAL);\n" +
                "            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (1.0, 2.5));";

        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(1, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("status")));

        ColumnDto statusColumn = fooTable.columns.stream().filter(c -> c.name.equalsIgnoreCase("status")).findFirst().get();

        Set<Double> actualEnumValues = statusColumn.enumValuesAsStrings.stream().map(Double::valueOf).collect(Collectors.toSet());
        Set<Double> expectedEnumValues = Stream.of(1.0, 2.5).collect(Collectors.toSet());

        assertEquals(expectedEnumValues, actualEnumValues);

    }

    @Test
    public void testEnumDecimalConstraint() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (status DECIMAL);\n" +
                "            ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in (1.0, 2.5));";

        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(1, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("status")));

        ColumnDto statusColumn = fooTable.columns.stream().filter(c -> c.name.equalsIgnoreCase("status")).findFirst().get();

        Set<Double> actualEnumValues = statusColumn.enumValuesAsStrings.stream().map(Double::valueOf).collect(Collectors.toSet());
        Set<Double> expectedEnumValues = Stream.of(1.0, 2.5).collect(Collectors.toSet());

        assertEquals(expectedEnumValues, actualEnumValues);

    }

    @Test
    public void testEnumCharConstraint() throws Exception {
        String sqlCommand = "CREATE TABLE FOO (status CHAR);"
                + "ALTER TABLE FOO ADD CONSTRAINT CHK_STATUS CHECK (status in ('A', 'B'));";
        SqlScriptRunner.execCommand(getConnection(), sqlCommand);

        DbSchemaDto schema = SchemaExtractor.extract(getConnection());

        assertEquals(1, schema.tables.size());

        TableDto fooTable = schema.tables.stream().filter(t -> t.name.equalsIgnoreCase("Foo")).findAny().get();

        assertEquals(1, fooTable.columns.size());

        assertTrue(fooTable.columns.stream().anyMatch(c -> c.name.equalsIgnoreCase("status")));

        ColumnDto statusColumn = fooTable.columns.stream().filter(c -> c.name.equalsIgnoreCase("status")).findFirst().get();

        Set<String> actualEnumValues = statusColumn.enumValuesAsStrings.stream().collect(Collectors.toSet());
        Set<String> expectedEnumValues = Stream.of("A", "B").collect(Collectors.toSet());

        assertEquals(expectedEnumValues, actualEnumValues);

    }
}