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
        String propertyFileName = "test.property";
        String propertyContent = " MetaTable = metaHunk";
        File propertyFile = new File(propertyFileName);
        if (propertyFile.exists()) {
            propertyFile.delete();
        }
        propertyFile.createNewFile();
        BufferedWriter bWriter = new BufferedWriter(new FileWriter(propertyFile));
        bWriter.append(propertyContent);
        bWriter.flush();
        bWriter.close();
        ExtractionMeta meta = new ExtractionMeta("MyVoldemort", 501, 800);
        meta.loadProperty(propertyFileName);
        Assert.assertEquals(meta.metaTableName, propertyContent.split("=")[1].trim());
        propertyFile.delete();
    }

}