package src.test.extraction;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import src.main.extraction.ExtractionMeta;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import static org.junit.Assert.*;

public class ExtractionMetaTest {
    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void loadProperty() throws Exception {
        String logFileName = "log";
        String logContent = " MetaTable = metaHunk";
        File logFile = new File(logFileName);
        if (logFile.exists()) {
            logFile.delete();
        }
        logFile.createNewFile();
        BufferedWriter bWriter = new BufferedWriter(new FileWriter(logFile));
        bWriter.append(logContent);
        bWriter.flush();
        bWriter.close();
        ExtractionMeta meta = new ExtractionMeta("MyVoldemort", 501, 800);
        meta.loadProperty(logFileName);
        Assert.assertEquals(meta.metaTableName, logContent.split("=")[1].trim());
        logFile.delete();
    }

}