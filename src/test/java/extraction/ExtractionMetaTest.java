package extraction;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Set;

import static org.junit.Assert.*;

public class ExtractionMetaTest {
    private ExtractionMeta extractionMeta = null;

    @Test
    public void parseLineAccordingOperator() throws Exception {
        String line = "- * \t\tWHERE v.url = d.url\n"+"- * \t\t\tAND v.visitDate < 2000-01-01);";
        String expectedLine1 = "*WHEREv.url=d.url";
        String expectedLine2 = "*ANDv.visitDate<2000-01-01);";
        Set<String> parseLines = extractionMeta.parseLineAccordingOperator(ExtractionMeta.sub_operator_symbol,line);
        Assert.assertTrue(parseLines.contains(expectedLine1));
        Assert.assertTrue(parseLines.contains(expectedLine2));
    }

    @Before
    public void setUp() throws Exception {
        extractionMeta = new ExtractionMeta("MyVoldemort", 501, 800);
    }

    @After
    public void tearDown() throws Exception {
    }

}